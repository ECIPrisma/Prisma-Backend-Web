package com.prisma.api.pagos;

import com.jayway.jsonpath.JsonPath;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-12 / N-08 – Pasarela de pagos SIMULADA: vinculación de la cuenta de cobro del proveedor,
 * checkout del tendero (Nequi / PSE / tarjeta), garantía y liberación al confirmar la entrega.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagosSimuladosIntegrationTest {

    private static final String MENSAJE_FALLA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PedidoRepository pedidoRepository;

    private String tokenTendero;
    private String tokenFemsa;
    private Proveedor femsa;

    @BeforeEach
    void setUp() throws Exception {
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    private Long crearPedido() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tiendas/pedidos").header("Authorization", tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":" + femsa.getId() + ",\"items\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.datos.id")).longValue();
    }

    private ResultActions pagar(Long pedidoId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------------ Checkout del tendero (N-08)

    @Test
    @DisplayName("Nequi 3991111111 aprueba: el pedido queda pagado en garantía y se ve en Mis Pedidos")
    void nequiApruebaYQuedaEnGarantia() throws Exception {
        Long pedidoId = crearPedido();

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.aprobado").value(true))
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.datos.detalleMedio").value("Nequi ••• 1111"))
                .andExpect(jsonPath("$.datos.referencia").value(startsWith("PRISMA-PED-")))
                .andExpect(jsonPath("$.datos.transaccionId").value(startsWith("SBX-")))
                .andExpect(jsonPath("$.datos.simulado").value(true));

        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("EN_GARANTIA")))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].medioPagoEnLinea").value(contains("NEQUI")));
    }

    @Test
    @DisplayName("Un pedido ya pagado no se puede pagar dos veces")
    void noSePagaDosVeces() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242 4242 4242 4242\",\"titular\":\"MARTA RODRIGUEZ\",\"vencimiento\":\"12/30\",\"cuotas\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.detalleMedio").value("VISA •••• 4242"));

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rechazos simulados (Nequi, PSE, tarjeta) dejan el pedido sin pagar y permiten reintentar")
    void rechazosPermitenReintentar() throws Exception {
        Long pedidoId = crearPedido();

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3992222222\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.aprobado").value(false))
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.mensaje").value(startsWith("Pago rechazado")));
        pagar(pedidoId, "{\"medio\":\"PSE\",\"banco\":\"Banco que rechaza\",\"tipoPersona\":\"NATURAL\",\"documento\":\"1020304050\"}")
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4111111111111111\",\"titular\":\"MARTA\",\"vencimiento\":\"12/30\"}")
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));

        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("RECHAZADO")));

        pagar(pedidoId, "{\"medio\":\"PSE\",\"banco\":\"Bancolombia\",\"tipoPersona\":\"NATURAL\",\"documento\":\"1020304050\"}")
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.datos.detalleMedio").value("PSE • Bancolombia"));

        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));
    }

    @Test
    @DisplayName("Datos inválidos se rechazan con 400 sin crear transacción")
    void datosInvalidos() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242424242424241\",\"titular\":\"MARTA\",\"vencimiento\":\"12/30\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242424242424242\",\"titular\":\"MARTA\",\"vencimiento\":\"01/20\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"12345\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"celular\":\"3991111111\"}")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo el tendero dueño del pedido puede pagarlo")
    void soloElTenderoPaga() throws Exception {
        Long pedidoId = crearPedido();
        mockMvc.perform(post("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}"))
                .andExpect(status().isForbidden());
        pagar(999_999L, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Confirmar la entrega con PIN libera la garantía al proveedor y no suma efectivo a la ruta")
    void entregaLiberaGarantia() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cantidadEnGarantia").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")].estado").value(contains("EN_GARANTIA")));

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        var recaudoAntes = pedido.getRuta() != null ? pedido.getRuta().getTotalRecaudoEfectivo() : null;

        String tokenConductor = login("conductor.juan@femsa.com.co", "admin123");
        mockMvc.perform(post("/api/v1/repartidores/pedidos/" + pedidoId + "/confirmar-entrega")
                        .header("Authorization", tokenConductor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pedido.getPinEntregaHash() + "\",\"medioPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estadoPago").value("LIBERADO"));

        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")].estado").value(contains("LIBERADO")))
                .andExpect(jsonPath("$.datos.cantidadLiberados").value(greaterThanOrEqualTo(1)));

        Pedido entregado = pedidoRepository.findById(pedidoId).orElseThrow();
        if (entregado.getRuta() != null) {
            assertThat(entregado.getRuta().getTotalRecaudoEfectivo()).isEqualTo(recaudoAntes);
        }
    }

    // ------------------------------------------------------------- Cuenta de cobro del proveedor (HU-12)

    @Test
    @DisplayName("Proveedor sin cuenta: estado no vinculada con el porcentaje que recibirá")
    void estadoSinVincular() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.disponible").value(true))
                .andExpect(jsonPath("$.datos.simulada").value(true))
                .andExpect(jsonPath("$.datos.vinculada").value(false))
                .andExpect(jsonPath("$.datos.porcentajeProveedor").value(97.50));
    }

    @Test
    @DisplayName("Criterio 1: vincular la cuenta la habilita y solo expone los últimos 4 dígitos")
    void vincularCuenta() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Bancolombia\",\"tipoCuenta\":\"ahorros\",\"numeroCuenta\":\"123-456789-12\",\"titular\":\"FEMSA Colombia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vinculada").value(true))
                .andExpect(jsonPath("$.datos.habilitadoPagosDivididos").value(true))
                .andExpect(jsonPath("$.datos.tipoCuenta").value("AHORROS"))
                .andExpect(jsonPath("$.datos.numeroCuentaEnmascarado").value("•••• 8912"))
                .andExpect(jsonPath("$.datos.cuentaExternaId").value(startsWith("WMP-SBX-")))
                .andExpect(content().string(not(containsString("12345678912"))));

        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Davivienda\",\"tipoCuenta\":\"CORRIENTE\",\"numeroCuenta\":\"99998888\",\"titular\":\"FEMSA\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vinculada").value(false));
    }

    @Test
    @DisplayName("Criterio 2: si la pasarela no verifica la cuenta (termina en 0000) se muestra el mensaje de falla")
    void vinculacionRechazada() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Bancolombia\",\"tipoCuenta\":\"AHORROS\",\"numeroCuenta\":\"1234560000\",\"titular\":\"FEMSA Colombia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_FALLA));

        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vinculada").value(false));
    }

    @Test
    @DisplayName("El tendero no puede administrar cuentas de cobro de proveedores")
    void tenderoNoAccedeCuentaCobro() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenTendero))
                .andExpect(status().isForbidden());
    }
}
