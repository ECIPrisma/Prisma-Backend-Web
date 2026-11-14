package com.prisma.api.modopro;

import com.fasterxml.jackson.databind.JsonNode;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Tienda;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-15 – Ranking de productos por zona (Modo Pro).
 * Solo cuentan pedidos ENTREGADOS dentro de la ventana; sin datos se muestra el mensaje acordado.
 */
class RankingPorZonaIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/v1/proveedores/modo-pro/ranking";
    private static final String MENSAJE_SIN_DATOS = "Aún no hay suficientes datos para generar este reporte";

    private String tokenMarta;
    private String tokenPedro;
    private Producto cola;
    private Producto lata;

    @BeforeEach
    void preparar() throws Exception {
        // Las entregas sembradas se sacan de la ventana para que cada prueba controle sus datos
        pedidoRepository.findByProveedorIdAndEstado(femsa().getId(), EstadoPedido.ENTREGADO)
                .forEach(p -> p.setFechaEntregaReal(OffsetDateTime.now().minusDays(400)));
        pedidoRepository.flush();

        cola = productoRepository.findByProveedorIdAndSku(femsa().getId(), "KO-PET-400").orElseThrow();
        lata = productoRepository.findByProveedorIdAndSku(femsa().getId(), "KO-LATA-330").orElseThrow();

        tokenMarta = login(MARTA, MARTA_PASS);
        asignarBarrio(MARTA, "Zona Alfa");
        tokenPedro = registrarTendero("pedro.ranking@test.com", "8.888.001");
        asignarBarrio("pedro.ranking@test.com", "Zona Beta");
    }

    private void asignarBarrio(String email, String barrio) {
        Tienda t = tiendaRepository.findFirstByTenderoId(usuarioRepository.findByEmail(email).orElseThrow().getId()).orElseThrow();
        t.setBarrio(barrio);
        tiendaRepository.saveAndFlush(t);
    }

    /** Crea el pedido por la API y lo deja ENTREGADO hace {@code haceDias} días. */
    private String entregado(String token, int haceDias, Object... productoYCantidad) throws Exception {
        String numero = pedidoCreado(token, productoYCantidad);
        Pedido p = pedido(numero);
        p.setEstado(EstadoPedido.ENTREGADO);
        p.setFechaEntregaReal(OffsetDateTime.now().minusDays(haceDias));
        pedidoRepository.saveAndFlush(p);
        return numero;
    }

    private String pedidoCreado(String token, Object... productoYCantidad) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("proveedorId", femsa().getId());
        cuerpo.put("items", items(productoYCantidad));
        return leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo).andExpect(status().isOk()))
                .path("datos").path("numeroPedido").asText();
    }

    private JsonNode ranking(String query) throws Exception {
        return leer(getReq(URL + query, login(FEMSA_USER, FEMSA_PASS)).andExpect(status().isOk())).path("datos");
    }

    private JsonNode zona(JsonNode datos, String nombre) {
        for (JsonNode z : datos.path("zonas")) {
            if (nombre.equals(z.path("zona").asText())) {
                return z;
            }
        }
        return null;
    }

    // ============================================================ LO QUE DEBE PASAR
    @Test
    @DisplayName("Ranking por zona: productos ordenados por unidades entregadas (mayor primero), con posición y total de la zona")
    void ordenaPorUnidades() throws Exception {
        entregado(tokenMarta, 2, cola.getId(), 5);
        entregado(tokenMarta, 3, lata.getId(), 9);

        JsonNode datos = ranking("");
        JsonNode alfa = zona(datos, "Zona Alfa");

        assertThat(datos.path("hayDatos").asBoolean()).isTrue();
        assertThat(datos.path("dias").asInt()).isEqualTo(30);
        assertThat(alfa).isNotNull();
        assertThat(alfa.path("unidadesTotales").asLong()).isEqualTo(14);
        assertThat(alfa.path("productos")).hasSize(2);
        assertThat(alfa.path("productos").get(0).path("productoId").asLong()).isEqualTo(lata.getId());
        assertThat(alfa.path("productos").get(0).path("unidades").asLong()).isEqualTo(9);
        assertThat(alfa.path("productos").get(0).path("posicion").asInt()).isEqualTo(1);
        assertThat(alfa.path("productos").get(1).path("productoId").asLong()).isEqualTo(cola.getId());
        assertThat(alfa.path("productos").get(1).path("unidades").asLong()).isEqualTo(5);
        assertThat(alfa.path("productos").get(1).path("posicion").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Varios pedidos del mismo producto en una zona se suman")
    void sumaPedidosDelMismoProducto() throws Exception {
        entregado(tokenMarta, 1, cola.getId(), 4);
        entregado(tokenMarta, 5, cola.getId(), 6);

        JsonNode alfa = zona(ranking(""), "Zona Alfa");
        assertThat(alfa.path("productos")).hasSize(1);
        assertThat(alfa.path("productos").get(0).path("unidades").asLong()).isEqualTo(10);
    }

    @Test
    @DisplayName("Cada zona tiene su propio ranking y las zonas salen ordenadas por volumen total")
    void zonasSeparadasYOrdenadas() throws Exception {
        entregado(tokenPedro, 1, cola.getId(), 3);
        entregado(tokenMarta, 1, cola.getId(), 8);

        JsonNode datos = ranking("");
        assertThat(datos.path("zonas").get(0).path("zona").asText()).isEqualTo("Zona Alfa");
        assertThat(datos.path("zonas").get(1).path("zona").asText()).isEqualTo("Zona Beta");
        assertThat(zona(datos, "Zona Beta").path("productos").get(0).path("unidades").asLong()).isEqualTo(3);
        assertThat(zona(datos, "Zona Alfa").path("productos").get(0).path("unidades").asLong()).isEqualTo(8);
    }

    @Test
    @DisplayName("Una tienda sin barrio se agrupa en 'Sin zona' en vez de romper el reporte")
    void tiendaSinBarrio() throws Exception {
        asignarBarrio("pedro.ranking@test.com", "   ");
        entregado(tokenPedro, 1, cola.getId(), 2);

        assertThat(zona(ranking(""), "Sin zona")).isNotNull();
    }

    @Test
    @DisplayName("El periodo es configurable: una entrega de hace 40 días no entra en 30 días pero sí en 60")
    void ventanaConfigurable() throws Exception {
        entregado(tokenMarta, 40, cola.getId(), 7);

        assertThat(ranking("?dias=30").path("hayDatos").asBoolean()).isFalse();
        JsonNode con60 = ranking("?dias=60");
        assertThat(con60.path("hayDatos").asBoolean()).isTrue();
        assertThat(zona(con60, "Zona Alfa").path("productos").get(0).path("unidades").asLong()).isEqualTo(7);
    }

    // ============================================================ LO QUE NO DEBE PASAR
    @Test
    @DisplayName("Sin pedidos entregados en el periodo: mensaje 'Aún no hay suficientes datos...' y ninguna zona (no un ranking vacío)")
    void sinDatos() throws Exception {
        getReq(URL, login(FEMSA_USER, FEMSA_PASS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_SIN_DATOS))
                .andExpect(jsonPath("$.datos.hayDatos").value(false))
                .andExpect(jsonPath("$.datos.zonas").isEmpty());
    }

    @Test
    @DisplayName("Pedidos que aún no se entregan (CREADO) no suman al ranking")
    void ignoraPedidosNoEntregados() throws Exception {
        pedidoCreado(tokenMarta, cola.getId(), 50);

        assertThat(ranking("").path("hayDatos").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Un pedido CANCELADO tampoco suma, aunque tenga fecha de entrega")
    void ignoraPedidosCancelados() throws Exception {
        String numero = pedidoCreado(tokenMarta, cola.getId(), 50);
        Pedido p = pedido(numero);
        p.setEstado(EstadoPedido.CANCELADO);
        p.setFechaEntregaReal(OffsetDateTime.now().minusDays(1));
        pedidoRepository.saveAndFlush(p);

        assertThat(ranking("").path("hayDatos").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Periodo inválido (0, negativo, mayor a 365 o no numérico) -> 400")
    void periodoInvalido() throws Exception {
        String token = login(FEMSA_USER, FEMSA_PASS);
        for (String dias : new String[] {"0", "-5", "366", "abc"}) {
            getReq(URL + "?dias=" + dias, token).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Un proveedor no puede ver datos de otro pasando proveedorId: siempre recibe los suyos")
    void proveedorIgnoraProveedorIdAjeno() throws Exception {
        entregado(tokenMarta, 1, cola.getId(), 4);
        String token = login(FEMSA_USER, FEMSA_PASS);

        JsonNode propio = leer(getReq(URL, token).andExpect(status().isOk())).path("datos");
        JsonNode conParametroAjeno = leer(getReq(URL + "?proveedorId=987654", token).andExpect(status().isOk())).path("datos");

        assertThat(conParametroAjeno).isEqualTo(propio);
        assertThat(zona(conParametroAjeno, "Zona Alfa")).isNotNull();
    }

    @Test
    @DisplayName("Un tendero no accede al ranking -> 403; sin sesión -> 401")
    void sinPermiso() throws Exception {
        getReq(URL, tokenMarta).andExpect(status().isForbidden());
        getReq(URL, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un admin de plataforma debe indicar el proveedor (400 si falta); con proveedorId recibe el ranking de ese proveedor")
    void adminDebeIndicarProveedor() throws Exception {
        entregado(tokenMarta, 1, cola.getId(), 4);
        String tokenAdmin = login(ADMIN, ADMIN_PASS);

        getReq(URL, tokenAdmin).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("proveedorId")));

        JsonNode datos = leer(getReq(URL + "?proveedorId=" + femsa().getId(), tokenAdmin)
                .andExpect(status().isOk())).path("datos");
        assertThat(zona(datos, "Zona Alfa")).isNotNull();
    }
}
