package com.prisma.api.pagos;

import com.jayway.jsonpath.JsonPath;
import com.prisma.api.entity.PagoPedido;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.repository.PagoPedidoRepository;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estado REEMBOLSADO: un pedido pagado en línea que se cancela o rechaza ANTES de la entrega devuelve al tendero
 * el dinero que estaba en garantía. Un pago ya liberado (pedido entregado) o rechazado no se toca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReembolsoPagosIntegrationTest {

    private static final String NEQUI_APRUEBA = "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PagoPedidoRepository pagoRepository;

    private String tokenTendero;
    private String tokenFemsa;
    private String tokenAdmin;
    private Proveedor femsa;

    @BeforeEach
    void setUp() throws Exception {
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        tokenAdmin = login("admin@prisma.com.co", "admin123");
        femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    private Long crearPedidoPagado() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tiendas/pedidos").header("Authorization", tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":" + femsa.getId() + ",\"items\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long pedidoId = ((Number) JsonPath.read(body, "$.datos.id")).longValue();
        pagar(pedidoId, NEQUI_APRUEBA).andExpect(status().isOk()).andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));
        return pedidoId;
    }

    private ResultActions pagar(Long pedidoId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private EstadoPagoPedido estadoUltimoPago(Long pedidoId) {
        List<PagoPedido> pagos = pagoRepository.findByPedidoIdInOrderByCreadoEnAscIdAsc(List.of(pedidoId));
        return pagos.get(pagos.size() - 1).getEstado();
    }

    @Test
    @DisplayName("Soporte cancela un pedido pagado: el pago pasa a REEMBOLSADO y lo ven el tendero y el proveedor")
    void soporteCancelaPedidoPagado() throws Exception {
        Long pedidoId = crearPedidoPagado();

        mockMvc.perform(post("/api/v1/admin/pedidos/" + pedidoId + "/soporte-cancelar").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Producto agotado\",\"justificacion\":\"Producto agotado en bodega\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.datos.estadoPago").value("REEMBOLSADO"));

        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos.estado").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.datos.reembolsadoEn").isNotEmpty())
                .andExpect(jsonPath("$.datos.motivoReembolso").value(org.hamcrest.Matchers.containsString("Producto agotado")));
        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("REEMBOLSADO")));
        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")].estado").value(contains("REEMBOLSADO")))
                .andExpect(jsonPath("$.datos.cantidadReembolsados").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("El proveedor rechaza el pedido pagado (reporte de problema): se reembolsa")
    void proveedorRechazaPedidoPagado() throws Exception {
        Long pedidoId = crearPedidoPagado();

        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedidoId + "/reportar-problema").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"SIN_STOCK\",\"detalle\":\"Sin inventario\",\"accion\":\"RECHAZAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.datos.estadoPago").value("REEMBOLSADO"));

        assertThat(estadoUltimoPago(pedidoId)).isEqualTo(EstadoPagoPedido.REEMBOLSADO);
    }

    @Test
    @DisplayName("El proveedor cancela el pedido pagado con el cambio de estado: se reembolsa")
    void proveedorCancelaConCambioDeEstado() throws Exception {
        Long pedidoId = crearPedidoPagado();

        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedidoId + "/cambiar-estado").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ANULADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estadoPago").value("REEMBOLSADO"));

        assertThat(estadoUltimoPago(pedidoId)).isEqualTo(EstadoPagoPedido.REEMBOLSADO);
    }

    @Test
    @DisplayName("Reprogramar un pedido pagado NO reembolsa: el dinero sigue en garantía")
    void reprogramarNoReembolsa() throws Exception {
        Long pedidoId = crearPedidoPagado();

        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedidoId + "/reportar-problema").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"TIENDA_CERRADA\",\"detalle\":\"Volver mañana\",\"accion\":\"REPROGRAMAR\"}"))
                .andExpect(status().isOk());

        assertThat(estadoUltimoPago(pedidoId)).isEqualTo(EstadoPagoPedido.EN_GARANTIA);
    }

    @Test
    @DisplayName("Un pago ya LIBERADO (pedido entregado) no se reembolsa aunque soporte cancele después")
    void pagoLiberadoNoSeReembolsa() throws Exception {
        Long pedidoId = crearPedidoPagado();
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        String tokenConductor = login("conductor.juan@femsa.com.co", "admin123");
        mockMvc.perform(post("/api/v1/repartidores/pedidos/" + pedidoId + "/confirmar-entrega")
                        .header("Authorization", tokenConductor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pedido.getPinEntregaHash() + "\",\"medioPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/pedidos/" + pedidoId + "/soporte-cancelar").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error\",\"justificacion\":\"Cancelación tardía de prueba\"}"))
                .andExpect(status().isOk());

        assertThat(estadoUltimoPago(pedidoId)).isEqualTo(EstadoPagoPedido.LIBERADO);
    }

    @Test
    @DisplayName("Cancelar un pedido sin pago en línea, o con el pago rechazado, no crea ni cambia pagos")
    void cancelarSinPagoAprobado() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tiendas/pedidos").header("Authorization", tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":" + femsa.getId() + ",\"items\":[]}"))
                .andReturn().getResponse().getContentAsString();
        Long pedidoId = ((Number) JsonPath.read(body, "$.datos.id")).longValue();
        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3992222222\"}")
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));

        mockMvc.perform(post("/api/v1/admin/pedidos/" + pedidoId + "/soporte-cancelar").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Prueba\",\"justificacion\":\"Cancelación sin pago aprobado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estadoPago").doesNotExist());

        assertThat(estadoUltimoPago(pedidoId)).isEqualTo(EstadoPagoPedido.RECHAZADO);
        // Y un pedido cancelado ya no admite pagos
        pagar(pedidoId, NEQUI_APRUEBA).andExpect(status().isConflict());
    }
}
