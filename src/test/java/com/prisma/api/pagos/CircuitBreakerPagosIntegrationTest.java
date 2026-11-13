package com.prisma.api.pagos;

import com.jayway.jsonpath.JsonPath;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.repository.ProveedorRepository;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-19 – Circuit breaker de la pasarela de pagos de punta a punta (HTTP).
 * Usa la configuración por defecto: ventana de 10, mínimo 5 llamadas, 50% de fallos.
 * El circuito es un único bean compartido: se reinicia antes y después de cada prueba para no afectar a las demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CircuitBreakerPagosIntegrationTest {

    private static final String CAIDA = "{\"medio\":\"NEQUI\",\"celular\":\"" + PasarelaPagosSimulada.NEQUI_PASARELA_CAIDA + "\"}";
    private static final String APRUEBA = "{\"medio\":\"NEQUI\",\"celular\":\"" + PasarelaPagosSimulada.NEQUI_APRUEBA + "\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasarelaPagosResiliente pasarelaResiliente;
    @Autowired private ProveedorRepository proveedorRepository;

    private String tokenTendero;
    private String tokenAdmin;

    @BeforeEach
    void setUp() throws Exception {
        pasarelaResiliente.reiniciar();
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
        tokenAdmin = login("admin@prisma.com.co", "admin123");
    }

    @AfterEach
    void cerrarCircuito() {
        pasarelaResiliente.reiniciar();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    private Long crearPedido() throws Exception {
        Proveedor femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
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

    @Test
    @DisplayName("HU-19 criterio 1: con la pasarela caída el circuito se abre y los pagos responden 503 de inmediato")
    void circuitoSeAbreYRespondeInmediato() throws Exception {
        Long pedidoId = crearPedido();

        for (int i = 0; i < 5; i++) {
            pagar(pedidoId, CAIDA)
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.mensaje").value(PasarelaPagosResiliente.MENSAJE_FALLA_PASARELA));
        }
        mockMvc.perform(get("/api/v1/admin/pagos/circuito").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("OPEN"));

        // Datos que aprobarían: igual no se llama a la pasarela
        pagar(pedidoId, APRUEBA)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Servicio de pagos temporalmente no disponible"));

        // El pedido no cambió: no se creó ningún pago
        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-19: al cerrarse el circuito los pagos vuelven a procesarse con normalidad")
    void alCerrarseVuelveANormalidad() throws Exception {
        Long pedidoId = crearPedido();
        for (int i = 0; i < 5; i++) {
            pagar(pedidoId, CAIDA).andExpect(status().isServiceUnavailable());
        }
        assertThat(pasarelaResiliente.estadoActual().name()).isEqualTo("OPEN");

        // (La transición automática por tiempo se prueba en CircuitBreakerPasarelaUnitTest)
        mockMvc.perform(post("/api/v1/admin/pagos/circuito/reiniciar").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("CLOSED"));

        pagar(pedidoId, APRUEBA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));
    }

    @Test
    @DisplayName("HU-19: solo los administradores ven el estado del circuito")
    void estadoSoloAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pagos/circuito").header("Authorization", tokenTendero))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/pagos/circuito").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("CLOSED"))
                .andExpect(jsonPath("$.datos.umbralFallosPorcentaje").value(50.0));
    }
}
