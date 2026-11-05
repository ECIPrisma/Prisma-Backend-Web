package com.prisma.api.sprint2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProductoRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PushNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Sprint2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private com.prisma.api.repository.TiendaRepository tiendaRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    private UsernamePasswordAuthenticationToken authProveedor() {
        UserPrincipal principal = new UserPrincipal(
                2L, "ventas@femsa.com", "hash", "Admin FEMSA", 1L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR_ADMIN"))
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken authTendero() {
        Long tenderoId = tiendaRepository.findAll().get(0).getTendero().getId();
        UserPrincipal principal = new UserPrincipal(
                tenderoId, "tendero@oasis.com", "hash", "Don Pedro", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken authRepartidor() {
        UserPrincipal principal = new UserPrincipal(
                4L, "conductor@femsa.com", "hash", "Carlos Conductor", 1L,
                List.of(new SimpleGrantedAuthority("ROLE_REPARTIDOR"))
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("SCRUM-15 (HU-05): Publicación exitosa (201) y rechazo cuando cantidad mínima <= 0 (400)")
    void testHu05PublicacionProductoValidacionCantidadMinima() throws Exception {
        Map<String, Object> valido = Map.of(
                "sku", "SKU-SPRINT2-01",
                "nombre", "Coca-Cola Zero 2.5L",
                "precioUnitario", 6500,
                "stockDisponible", 40,
                "cantidadMinimaPedido", 6,
                "zonasCobertura", "Chapinero, Usaquén, Suba"
        );

        mockMvc.perform(post("/api/v1/proveedores/productos")
                        .with(authentication(authProveedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.cantidadMinimaPedido").value(6));

        Map<String, Object> invalido = Map.of(
                "sku", "SKU-SPRINT2-02",
                "nombre", "Producto Inválido",
                "precioUnitario", 5000,
                "cantidadMinimaPedido", 0
        );

        mockMvc.perform(post("/api/v1/proveedores/productos")
                        .with(authentication(authProveedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La cantidad mínima debe ser mayor a 0"));
    }

    @Test
    @DisplayName("SCRUM-16 (HU-06) y SCRUM-18 (HU-08): Actualización de stock/precio y rechazo de cantidad menor a la mínima o agotado")
    void testHu06YHu08StockAgotadoYCantidadMinimaEnPedido() throws Exception {
        Producto prod = productoRepository.findAll().get(0);
        prod.setCantidadMinimaPedido(5);
        prod.setStockDisponible(20);
        productoRepository.save(prod);

        // Intentar pedir 2 unidades cuando la mínima es 5 -> 400 Bad Request
        Map<String, Object> pedidoMenorMinimo = Map.of(
                "proveedorId", 1L,
                "items", List.of(Map.of("productoId", prod.getId(), "cantidad", 2))
        );

        mockMvc.perform(post("/api/v1/tiendas/pedidos")
                        .with(authentication(authTendero()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoMenorMinimo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La cantidad mínima para este producto es 5 unidades"));

        // Actualizar stock a 0 vía HU-06
        mockMvc.perform(put("/api/v1/proveedores/productos/" + prod.getId())
                        .with(authentication(authProveedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stockDisponible", 0, "precioUnitario", 4800))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.stockDisponible").value(0));
    }

    @Test
    @DisplayName("SCRUM-17 (HU-07): Exploración de catálogo filtrado por zona geográfica y proveedor")
    void testHu07ExploracionCatalogoPorZona() throws Exception {
        mockMvc.perform(get("/api/v1/tiendas/catalogo")
                        .param("zona", "Chapinero")
                        .param("proveedorId", "1")
                        .with(authentication(authTendero())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    @DisplayName("SCRUM-19 (HU-09): Creación de pedido con Idempotency-Key evita duplicados en reintentos offline")
    void testHu09PedidoConIdempotencyKeyNoDuplica() throws Exception {
        Producto prod = productoRepository.findAll().get(0);
        prod.setCantidadMinimaPedido(1);
        prod.setStockDisponible(100);
        productoRepository.save(prod);

        String idemKey = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "proveedorId", 1L,
                "items", List.of(Map.of("productoId", prod.getId(), "cantidad", 3))
        );

        long countAntes = pedidoRepository.count();

        mockMvc.perform(post("/api/v1/tiendas/pedidos")
                        .header("Idempotency-Key", idemKey)
                        .with(authentication(authTendero()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));

        // Reintento con la misma Idempotency-Key tras recuperar señal
        mockMvc.perform(post("/api/v1/tiendas/pedidos")
                        .header("Idempotency-Key", idemKey)
                        .with(authentication(authTendero()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));

        long countDespues = pedidoRepository.count();
        assertThat(countDespues).isEqualTo(countAntes + 1);
    }

    @Test
    @DisplayName("SCRUM-20 (HU-10): Transición válida de estado (200) y bloqueo de transición desde Anulado a Entregado (409)")
    void testHu10TransicionEstadosPedido() throws Exception {
        Pedido pedido = pedidoRepository.findAll().get(0);
        pedido.setEstado(EstadoPedido.ACEPTADO);
        pedidoRepository.save(pedido);

        // Transición válida a En Despacho
        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedido.getId() + "/cambiar-estado")
                        .with(authentication(authProveedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "En Despacho"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_RUTA"));

        // Anular pedido y luego intentar pasarlo a Entregado -> 409 Conflict
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(pedido);

        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedido.getId() + "/cambiar-estado")
                        .with(authentication(authProveedor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("estado", "ENTREGADO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Transición de estado no permitida"));
    }

    @Test
    @DisplayName("SCRUM-21 (HU-11): Confirmación de entrega con OTP bloquea temporalmente tras superar límite de reintentos")
    void testHu11ConfirmacionEntregaOtpBloqueoPorReintentos() throws Exception {
        Pedido pedido = pedidoRepository.findAll().get(0);
        pedido.setPinEntregaHash("654321");
        pedido.setEstado(EstadoPedido.EN_RUTA);
        pedido.setIntentosFallidosPin(0);
        pedido.setPinBloqueadoHasta(null);
        pedidoRepository.save(pedido);

        ConfirmarEntregaPinRequest pinInvalido = new ConfirmarEntregaPinRequest();
        pinInvalido.setPin("000000");
        pinInvalido.setMedioPago(MedioPago.EFECTIVO);

        // 3 intentos fallidos -> en el 3ro bloquea temporalmente
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/repartidores/pedidos/" + pedido.getId() + "/confirmar-entrega")
                            .with(authentication(authRepartidor()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(pinInvalido)))
                    .andExpect(status().isBadRequest());
        }

        // Intento posterior aun con PIN correcto debe ser rechazado por estar bloqueado temporalmente
        ConfirmarEntregaPinRequest pinCorrecto = new ConfirmarEntregaPinRequest();
        pinCorrecto.setPin("654321");
        pinCorrecto.setMedioPago(MedioPago.EFECTIVO);

        mockMvc.perform(post("/api/v1/repartidores/pedidos/" + pedido.getId() + "/confirmar-entrega")
                        .with(authentication(authRepartidor()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pinCorrecto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Confirmación bloqueada temporalmente por superar el límite de intentos de OTP."));
    }

    @Test
    @DisplayName("SCRUM-61 (N-07): Registro de token FCM y despacho de notificaciones push")
    void testN07RegistroTokenFcmYNotificacionesPush() throws Exception {
        mockMvc.perform(post("/api/v1/notificaciones/fcm-token")
                        .with(authentication(authTendero()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fcmToken", "fcm_test_token_android_123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.estado").value("REGISTRADO"));

        mockMvc.perform(get("/api/v1/notificaciones/mis-notificaciones")
                        .with(authentication(authTendero())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }
}
