package com.prisma.api.entrega;

import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.Rol;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jornada del repartidor sobre los datos semilla:
 * RUTA-KENNEDY-01 con PED-0001 (ENTREGADO, PIN 849201), PED-0002 (ENTREGADO) y PED-0003 (ACEPTADO, PIN 771209).
 */
class EntregaRepartidorIntegrationTest extends IntegrationTestBase {

    private static final String PIN_PED3 = "771209";

    private String tokenJuan;
    private Ruta ruta;
    private Pedido ped3;

    @BeforeEach
    void preparar() throws Exception {
        tokenJuan = login(JUAN, JUAN_PASS);
        ruta = rutaKennedy();
        ped3 = pedido("PED-2026-0003");
    }

    private Map<String, Object> entrega(String pin) {
        return Map.of("pin", pin, "medioPago", "EFECTIVO");
    }

    private String urlConfirmar(Pedido p) {
        return "/api/v1/repartidores/pedidos/" + p.getId() + "/confirmar-entrega";
    }

    private void ponerEnRuta(Pedido p) {
        p.setEstado(EstadoPedido.EN_RUTA);
        pedidoRepository.save(p);
    }

    // ============================================================ CONFIRMAR ENTREGA CON PIN
    @Test
    @DisplayName("Flujo completo: iniciar ruta (pedido pasa a EN_RUTA) y confirmar con el PIN correcto -> ENTREGADO y suma el recaudo")
    void flujoCompletoDeEntrega() throws Exception {
        BigDecimal recaudoAntes = ruta.getTotalRecaudoEfectivo();

        postReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/iniciar", tokenJuan, Map.of())
                .andExpect(status().isOk());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);

        postReq(urlConfirmar(ped3), tokenJuan, Map.of("pin", PIN_PED3, "medioPago", "NEQUI_DAVIPLATA", "notasEntrega", "Recibió la dueña"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ENTREGADO"));

        Pedido entregado = pedido("PED-2026-0003");
        assertThat(entregado.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(entregado.getPinValidadoEn()).isNotNull();
        assertThat(entregado.getNotasEntrega()).isEqualTo("Recibió la dueña");
        assertThat(rutaKennedy().getTotalRecaudoEfectivo())
                .isEqualByComparingTo(recaudoAntes.add(ped3.getTotalAPagar()));
    }

    @Test
    @DisplayName("PIN incorrecto -> 400, el pedido sigue EN_RUTA y no cambia el recaudo")
    void pinIncorrecto() throws Exception {
        ponerEnRuta(ped3);
        BigDecimal recaudoAntes = ruta.getTotalRecaudoEfectivo();

        postReq(urlConfirmar(ped3), tokenJuan, entrega("000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exitoso").value(false));

        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
        assertThat(pedido("PED-2026-0003").getPinValidadoEn()).isNull();
        assertThat(rutaKennedy().getTotalRecaudoEfectivo()).isEqualByComparingTo(recaudoAntes);
    }

    @Test
    @DisplayName("PIN con formato inválido (4 dígitos, letras, vacío, 7 dígitos) -> 400 y el pedido no cambia")
    void pinMalFormado() throws Exception {
        ponerEnRuta(ped3);
        for (String pin : new String[]{"7712", "77120A", "", "7712091"}) {
            postReq(urlConfirmar(ped3), tokenJuan, entrega(pin)).andExpect(status().isBadRequest());
        }
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
    }

    @Test
    @DisplayName("Sin medio de pago, o con uno inexistente -> 400")
    void medioDePagoInvalido() throws Exception {
        ponerEnRuta(ped3);
        postReq(urlConfirmar(ped3), tokenJuan, Map.of("pin", PIN_PED3)).andExpect(status().isBadRequest());
        postReq(urlConfirmar(ped3), tokenJuan, Map.of("pin", PIN_PED3, "medioPago", "BITCOIN")).andExpect(status().isBadRequest());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
    }

    @Test
    @DisplayName("No se puede entregar un pedido que aún no salió a ruta (ACEPTADO), ni con el PIN correcto -> 409")
    void noSePuedeEntregarSinEstarEnRuta() throws Exception {
        postReq(urlConfirmar(ped3), tokenJuan, entrega(PIN_PED3)).andExpect(status().isConflict());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    @DisplayName("No se puede entregar dos veces el mismo pedido -> 409 y el recaudo no se duplica")
    void noSeEntregaDosVeces() throws Exception {
        ponerEnRuta(ped3);
        postReq(urlConfirmar(ped3), tokenJuan, entrega(PIN_PED3)).andExpect(status().isOk());
        BigDecimal recaudo = rutaKennedy().getTotalRecaudoEfectivo();

        postReq(urlConfirmar(ped3), tokenJuan, entrega(PIN_PED3)).andExpect(status().isConflict());
        assertThat(rutaKennedy().getTotalRecaudoEfectivo()).isEqualByComparingTo(recaudo);
    }

    @Test
    @DisplayName("Un pedido ya ENTREGADO (semilla PED-0001) no se vuelve a confirmar -> 409")
    void pedidoYaEntregadoDeLaSemilla() throws Exception {
        postReq(urlConfirmar(pedido("PED-2026-0001")), tokenJuan, entrega("849201")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Un pedido CANCELADO no se puede entregar -> 409")
    void pedidoCancelado() throws Exception {
        ped3.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.save(ped3);
        postReq(urlConfirmar(ped3), tokenJuan, entrega(PIN_PED3)).andExpect(status().isConflict());
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    @DisplayName("Pedido inexistente -> 404")
    void pedidoInexistente() throws Exception {
        postReq("/api/v1/repartidores/pedidos/987654/confirmar-entrega", tokenJuan, entrega(PIN_PED3))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un repartidor de OTRO proveedor no puede confirmar entregas ajenas, ni con el PIN correcto -> 403")
    void repartidorDeOtroProveedor() throws Exception {
        crearUsuario("rep.postobon@test.com", Rol.ROLE_REPARTIDOR, postobon());
        String tokenAjeno = login("rep.postobon@test.com", CLAVE_TEST);
        ponerEnRuta(ped3);

        postReq(urlConfirmar(ped3), tokenAjeno, entrega(PIN_PED3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.exitoso").value(false));
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
    }

    @Test
    @DisplayName("Un tendero no puede confirmar entregas -> 403; sin sesión -> 401")
    void sinPermisoParaConfirmar() throws Exception {
        ponerEnRuta(ped3);
        postReq(urlConfirmar(ped3), login(MARTA, MARTA_PASS), entrega(PIN_PED3)).andExpect(status().isForbidden());
        postReq(urlConfirmar(ped3), null, entrega(PIN_PED3)).andExpect(status().isUnauthorized());
    }

    // ============================================================ RUTAS
    @Test
    @DisplayName("Iniciar ruta: la ruta pasa a EN_EJECUCION y solo los pedidos pendientes pasan a EN_RUTA (los ENTREGADOS no se tocan)")
    void iniciarRuta() throws Exception {
        postReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/iniciar", tokenJuan, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_EJECUCION"));

        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.EN_RUTA);
        assertThat(pedido("PED-2026-0001").getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(pedido("PED-2026-0002").getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    @DisplayName("Iniciar una ruta ya FINALIZADA -> 409")
    void iniciarRutaFinalizada() throws Exception {
        postReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/finalizar", tokenJuan, Map.of()).andExpect(status().isOk());
        postReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/iniciar", tokenJuan, Map.of()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Finalizar ruta: queda FINALIZADA con hora de fin y resumen de recaudo")
    void finalizarRuta() throws Exception {
        postReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/finalizar", tokenJuan, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("FINALIZADA"))
                .andExpect(jsonPath("$.datos.codigoRuta").value("RUTA-KENNEDY-01"));
        assertThat(rutaKennedy().getEstado()).isEqualTo("FINALIZADA");
        assertThat(rutaKennedy().getHoraFinReal()).isNotNull();
    }

    @Test
    @DisplayName("Ruta inexistente -> 404 en iniciar, finalizar, manifiesto y paradas")
    void rutaInexistente() throws Exception {
        postReq("/api/v1/repartidores/rutas/987654/iniciar", tokenJuan, Map.of()).andExpect(status().isNotFound());
        postReq("/api/v1/repartidores/rutas/987654/finalizar", tokenJuan, Map.of()).andExpect(status().isNotFound());
        getReq("/api/v1/repartidores/rutas/987654/manifiesto", tokenJuan).andExpect(status().isNotFound());
        getReq("/api/v1/repartidores/rutas/987654/pedidos", tokenJuan).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un repartidor de otro proveedor no puede iniciar, finalizar ni consultar la ruta -> 403 y la ruta no cambia")
    void rutaDeOtroProveedor() throws Exception {
        crearUsuario("rep.postobon@test.com", Rol.ROLE_REPARTIDOR, postobon());
        String tokenAjeno = login("rep.postobon@test.com", CLAVE_TEST);
        String base = "/api/v1/repartidores/rutas/" + ruta.getId();
        String estadoAntes = ruta.getEstado();

        postReq(base + "/iniciar", tokenAjeno, Map.of()).andExpect(status().isForbidden());
        postReq(base + "/finalizar", tokenAjeno, Map.of()).andExpect(status().isForbidden());
        getReq(base + "/manifiesto", tokenAjeno).andExpect(status().isForbidden());
        getReq(base + "/pedidos", tokenAjeno).andExpect(status().isForbidden());

        assertThat(rutaKennedy().getEstado()).isEqualTo(estadoAntes);
        assertThat(pedido("PED-2026-0003").getEstado()).isEqualTo(EstadoPedido.ACEPTADO);
    }

    @Test
    @DisplayName("Manifiesto de carga: 3 paradas, recaudo esperado = suma de los pedidos y productos consolidados")
    void manifiestoDeCarga() throws Exception {
        BigDecimal suma = pedido("PED-2026-0001").getTotalAPagar()
                .add(pedido("PED-2026-0002").getTotalAPagar())
                .add(pedido("PED-2026-0003").getTotalAPagar());
        var datos = leer(getReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/manifiesto", tokenJuan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.codigoRuta").value("RUTA-KENNEDY-01"))
                .andExpect(jsonPath("$.datos.totalParadas").value(3))).path("datos");
        assertThat(datos.path("totalRecaudoEsperado").decimalValue()).isEqualByComparingTo(suma);
        assertThat(datos.path("itemsCargados")).isNotEmpty();
    }

    @Test
    @DisplayName("Paradas de la ruta vienen ordenadas 1, 2, 3")
    void paradasOrdenadas() throws Exception {
        var datos = leer(getReq("/api/v1/repartidores/rutas/" + ruta.getId() + "/pedidos", tokenJuan)
                .andExpect(status().isOk())).path("datos");
        assertThat(datos).hasSize(3);
        assertThat(datos.findValuesAsText("ordenParada")).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("Mis rutas de hoy: Juan ve su ruta; un repartidor sin rutas ve la lista vacía")
    void misRutas() throws Exception {
        getReq("/api/v1/repartidores/mis-rutas/hoy", tokenJuan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].codigoRuta").value("RUTA-KENNEDY-01"));

        crearUsuario("rep.postobon@test.com", Rol.ROLE_REPARTIDOR, postobon());
        getReq("/api/v1/repartidores/mis-rutas/hoy", login("rep.postobon@test.com", CLAVE_TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isEmpty());
    }
}
