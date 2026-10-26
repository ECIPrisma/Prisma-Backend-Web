package com.prisma.api.admin;

import com.prisma.api.entity.AuditLog;
import com.prisma.api.entity.Pedido;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mesa de ayuda: entrega forzada sin PIN (override), que exige justificación y deja rastro de auditoría. */
class AdminOverridePinIntegrationTest extends IntegrationTestBase {

    private String tokenAdmin;
    private Pedido ped3;

    @BeforeEach
    void preparar() throws Exception {
        tokenAdmin = login(ADMIN, ADMIN_PASS);
        ped3 = pedido("PED-2026-0003");
    }

    private String url(Pedido p) {
        return "/api/v1/admin/pedidos/" + p.getId() + "/override-pin";
    }

    @Test
    @DisplayName("Override con justificación válida: pedido ENTREGADO, nota con el motivo y quién lo autorizó")
    void overrideCorrecto() throws Exception {
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "Cliente sin celular, confirmado por llamada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ENTREGADO"));

        Pedido p = pedido("PED-2026-0003");
        assertThat(p.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(p.getFechaEntregaReal()).isNotNull();
        assertThat(p.getNotasEntrega()).contains("Cliente sin celular").contains(ADMIN);
    }

    @Test
    @DisplayName("El override queda registrado en la auditoría (resultado EXITOSO, a nombre del admin que lo autorizó)")
    void overrideDejaRastro() throws Exception {
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "Cliente sin celular, confirmado por llamada"))
                .andExpect(status().isOk());

        AuditLog registro = auditLogRepository.findAll().stream()
                .filter(a -> "PIN_OVERRIDE_ADMIN".equals(a.getTipoEvento()))
                .findFirst().orElseThrow();
        assertThat(registro.getResultado()).isEqualTo("EXITOSO");
        assertThat(registro.getAgenteEmail()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("Sin justificación, vacía o de menos de 10 caracteres -> 400 y el pedido NO cambia")
    void justificacionInsuficiente() throws Exception {
        postReq(url(ped3), tokenAdmin, Map.of()).andExpect(status().isBadRequest());
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "")).andExpect(status().isBadRequest());
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "         ")).andExpect(status().isBadRequest());
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "123456789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("10 caracteres")));
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    @DisplayName("Justificación de exactamente 10 caracteres es aceptada (límite)")
    void justificacionEnElLimite() throws Exception {
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "1234567890")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("No se puede forzar la entrega de un pedido ya ENTREGADO o CANCELADO -> 409")
    void noSobrescribePedidosCerrados() throws Exception {
        postReq(url(pedido("PED-2026-0001")), tokenAdmin, Map.of("justificacion", "intento de reescribir un cierre"))
                .andExpect(status().isConflict());

        ped3.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(ped3);
        postReq(url(ped3), tokenAdmin, Map.of("justificacion", "intento sobre pedido cancelado"))
                .andExpect(status().isConflict());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("Pedido inexistente -> 404")
    void pedidoInexistente() throws Exception {
        postReq("/api/v1/admin/pedidos/987654/override-pin", tokenAdmin, Map.of("justificacion", "justificación suficiente"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo personal autorizado: tendero y proveedor -> 403; sin sesión -> 401")
    void soloPersonalAutorizado() throws Exception {
        Map<String, String> cuerpo = Map.of("justificacion", "justificación suficiente");
        postReq(url(ped3), login(MARTA, MARTA_PASS), cuerpo).andExpect(status().isForbidden());
        postReq(url(ped3), login(FEMSA_USER, FEMSA_PASS), cuerpo).andExpect(status().isForbidden());
        postReq(url(ped3), null, cuerpo).andExpect(status().isUnauthorized());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    @DisplayName("Un intento fallido de login queda auditado con resultado ERROR")
    void loginFallidoQuedaAuditado() throws Exception {
        postReq("/api/v1/auth/login", null, Map.of("email", MARTA, "password", "incorrecta"))
                .andExpect(status().isUnauthorized());
        assertThat(auditLogRepository.findAll())
                .anyMatch(a -> "LOGIN_INTENTO".equals(a.getTipoEvento()) && "ERROR".equals(a.getResultado()));
    }
}
