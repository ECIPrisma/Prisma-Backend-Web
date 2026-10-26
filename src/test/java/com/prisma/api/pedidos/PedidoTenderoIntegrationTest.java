package com.prisma.api.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo del tendero: catálogo, creación de pedido, "lo de siempre", historial y reclamos. */
class PedidoTenderoIntegrationTest extends IntegrationTestBase {

    private Producto producto(String sku) {
        return productoRepository.findByProveedorIdAndSku(femsa().getId(), sku).orElseThrow();
    }

    private Map<String, Object> cuerpoPedido(Object... productoYCantidad) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("proveedorId", femsa().getId());
        cuerpo.put("items", items(productoYCantidad));
        return cuerpo;
    }

    // ============================================================ CREAR PEDIDO
    @Test
    @DisplayName("Crear pedido: total = suma de líneas, comisión 2.5%, estado CREADO, PIN de 6 dígitos y número único")
    void crearPedidoCorrecto() throws Exception {
        Producto p1 = producto("KO-PET-400");
        Producto p2 = productoRepository.findByProveedorId(femsa().getId()).stream()
                .filter(p -> !p.getId().equals(p1.getId())).findFirst().orElseThrow();
        String token = login(MARTA, MARTA_PASS);

        JsonNode datos = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), 2, p2.getId(), 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("CREADO")))
                .path("datos");

        BigDecimal esperado = p1.getPrecioUnitario().multiply(BigDecimal.valueOf(2))
                .add(p2.getPrecioUnitario().multiply(BigDecimal.valueOf(3)));
        assertThat(datos.path("subtotal").decimalValue()).isEqualByComparingTo(esperado);
        assertThat(datos.path("totalAPagar").decimalValue()).isEqualByComparingTo(esperado);
        assertThat(datos.path("comisionPrismaValor").decimalValue())
                .isEqualByComparingTo(esperado.multiply(new BigDecimal("0.025")));
        assertThat(datos.path("pinEntregaHash").asText()).matches("\\d{6}");
        assertThat(datos.path("numeroPedido").asText()).startsWith("PED-2026-");
        assertThat(datos.path("items")).hasSize(2);

        Pedido guardado = pedido(datos.path("numeroPedido").asText());
        assertThat(guardado.getEstado()).isEqualTo(EstadoPedido.CREADO);
        assertThat(guardado.getItems()).extracting("cantidad").containsExactlyInAnyOrder(2, 3);
    }

    @Test
    @DisplayName("El pedido se asigna a la ruta del proveedor y suma una parada y su recaudo esperado")
    void crearPedidoSeAsignaARuta() throws Exception {
        Producto p1 = producto("KO-PET-400");
        var ruta = rutaKennedy();
        int paradasAntes = ruta.getTotalParadas();
        BigDecimal recaudoAntes = ruta.getTotalRecaudoEsperado();

        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpoPedido(p1.getId(), 1))
                .andExpect(status().isOk());

        var despues = rutaKennedy();
        assertThat(despues.getTotalParadas()).isEqualTo(paradasAntes + 1);
        assertThat(despues.getTotalRecaudoEsperado()).isEqualByComparingTo(recaudoAntes.add(p1.getPrecioUnitario()));
    }

    @Test
    @DisplayName("Dos pedidos seguidos reciben números distintos")
    void numerosDePedidoUnicos() throws Exception {
        Producto p1 = producto("KO-PET-400");
        String token = login(MARTA, MARTA_PASS);
        String n1 = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), 1))).at("/datos/numeroPedido").asText();
        String n2 = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), 1))).at("/datos/numeroPedido").asText();
        assertThat(n1).isNotEqualTo(n2);
    }

    @Test
    @DisplayName("Pedido sin productos -> 400 (antes inventaba un total de 125.000)")
    void pedidoSinProductos() throws Exception {
        long antes = pedidoRepository.count();
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpoPedido())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("al menos un producto")));
        assertThat(pedidoRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Cantidad cero o negativa -> 400 y no se guarda nada")
    void cantidadInvalida() throws Exception {
        Producto p1 = producto("KO-PET-400");
        String token = login(MARTA, MARTA_PASS);
        long antes = pedidoRepository.count();
        postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), 0)).andExpect(status().isBadRequest());
        postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), -5)).andExpect(status().isBadRequest());
        assertThat(pedidoRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Cantidad o producto no numéricos -> 400 (no 500)")
    void valoresNoNumericos() throws Exception {
        Producto p1 = producto("KO-PET-400");
        String token = login(MARTA, MARTA_PASS);
        postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), "muchas")).andExpect(status().isBadRequest());
        postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido("abc", 1)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Producto inexistente -> 400, aunque otros productos del pedido sean válidos (nada se guarda)")
    void productoInexistente() throws Exception {
        Producto p1 = producto("KO-PET-400");
        long antes = pedidoRepository.count();
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpoPedido(p1.getId(), 1, 999_999L, 1))
                .andExpect(status().isBadRequest());
        assertThat(pedidoRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Producto de OTRO proveedor dentro del pedido -> 400")
    void productoDeOtroProveedor() throws Exception {
        Producto ajeno = crearProducto(postobon(), "PB-001", "ACTIVO", "5000");
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpoPedido(ajeno.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("no pertenece al proveedor")));
    }

    @Test
    @DisplayName("Producto retirado del catálogo (INACTIVO) no se puede pedir -> 400")
    void productoInactivo() throws Exception {
        Producto inactivo = crearProducto(femsa(), "KO-OFF", "INACTIVO", "9000");
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpoPedido(inactivo.getId(), 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Proveedor inexistente -> 404")
    void proveedorInexistente() throws Exception {
        Producto p1 = producto("KO-PET-400");
        Map<String, Object> cuerpo = cuerpoPedido(p1.getId(), 1);
        cuerpo.put("proveedorId", 987_654L);
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpo).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin sesión -> 401; un proveedor no puede crear pedidos de tendero -> 403")
    void crearPedidoSinPermiso() throws Exception {
        Producto p1 = producto("KO-PET-400");
        postReq("/api/v1/tiendas/pedidos", null, cuerpoPedido(p1.getId(), 1)).andExpect(status().isUnauthorized());
        postReq("/api/v1/tiendas/pedidos", login(FEMSA_USER, FEMSA_PASS), cuerpoPedido(p1.getId(), 1)).andExpect(status().isForbidden());
    }

    // ============================================================ CATÁLOGO
    @Test
    @DisplayName("El catálogo no muestra productos INACTIVOS")
    void catalogoOcultaInactivos() throws Exception {
        crearProducto(femsa(), "KO-OCULTO", "INACTIVO", "9000");
        crearProducto(femsa(), "KO-VISIBLE", "ACTIVO", "9000");
        JsonNode lista = leer(getReq("/api/v1/tiendas/catalogo/" + femsa().getId(), login(MARTA, MARTA_PASS))
                .andExpect(status().isOk())).path("datos");
        assertThat(lista.findValuesAsText("sku")).contains("KO-VISIBLE").doesNotContain("KO-OCULTO");
    }

    // ============================================================ LO DE SIEMPRE
    @Test
    @DisplayName("Repetir último pedido: crea uno nuevo CREADO, con los mismos productos, cantidades y total, y otro número")
    void repetirUltimoPedido() throws Exception {
        Producto p1 = producto("KO-PET-400");
        String token = login(MARTA, MARTA_PASS);
        JsonNode original = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpoPedido(p1.getId(), 4))).path("datos");

        JsonNode nuevo = leer(postReq("/api/v1/tiendas/pedidos/repetir-ultimo", token, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("CREADO"))).path("datos");

        assertThat(nuevo.path("numeroPedido").asText()).isNotEqualTo(original.path("numeroPedido").asText());
        assertThat(nuevo.path("totalAPagar").decimalValue()).isEqualByComparingTo(original.path("totalAPagar").decimalValue());
        assertThat(nuevo.path("items")).hasSize(1);
        assertThat(nuevo.path("items").get(0).path("cantidad").asInt()).isEqualTo(4);
    }

    @Test
    @DisplayName("Repetir último pedido sin historial -> 400")
    void repetirSinHistorial() throws Exception {
        String token = registrarTendero("nuevo@test.com", "2.000.111");
        postReq("/api/v1/tiendas/pedidos/repetir-ultimo", token, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("No hay pedidos previos")));
    }

    // ============================================================ HISTORIAL (aislamiento entre tenderos)
    @Test
    @DisplayName("Mis pedidos: cada tendero ve SOLO los suyos")
    void historialAislado() throws Exception {
        JsonNode deMarta = leer(getReq("/api/v1/tiendas/mis-pedidos", login(MARTA, MARTA_PASS))
                .andExpect(status().isOk())).path("datos");
        assertThat(deMarta.findValuesAsText("numeroPedido")).contains("PED-2026-0001", "PED-2026-0002", "PED-2026-0003");

        String tokenNuevo = registrarTendero("sin-pedidos@test.com", "2.000.222");
        JsonNode delNuevo = leer(getReq("/api/v1/tiendas/mis-pedidos", tokenNuevo)
                .andExpect(status().isOk())).path("datos");
        assertThat(delNuevo).isEmpty();
    }

    // ============================================================ RECLAMOS
    @Test
    @DisplayName("Reportar avería sobre un pedido propio crea un reclamo ABIERTO con ticket PQRS")
    void reportarAveria() throws Exception {
        Pedido propio = pedido("PED-2026-0001");
        postReq("/api/v1/tiendas/pedidos/" + propio.getId() + "/reportar-averia", login(MARTA, MARTA_PASS),
                Map.of("descripcion", "Llegaron 3 botellas rotas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.datos.tipoIncidente").value("MERCANCIA_DANADA_O_FALTANTE"))
                .andExpect(jsonPath("$.datos.descripcion").value("Llegaron 3 botellas rotas"))
                .andExpect(jsonPath("$.datos.ticketNumero", containsString("PQRS-MOBI-")));
    }

    @Test
    @DisplayName("Reportar avería sin descripción usa un texto por defecto (no falla)")
    void reportarAveriaSinDescripcion() throws Exception {
        Pedido propio = pedido("PED-2026-0001");
        postReq("/api/v1/tiendas/pedidos/" + propio.getId() + "/reportar-averia", login(MARTA, MARTA_PASS), Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.descripcion").isNotEmpty());
    }

    @Test
    @DisplayName("Reportar avería sobre un pedido AJENO -> 403")
    void reportarAveriaPedidoAjeno() throws Exception {
        Pedido deMarta = pedido("PED-2026-0001");
        String otroTendero = registrarTendero("ajeno@test.com", "2.000.333");
        postReq("/api/v1/tiendas/pedidos/" + deMarta.getId() + "/reportar-averia", otroTendero,
                Map.of("descripcion", "reclamo indebido"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reportar avería de un pedido inexistente -> 404")
    void reportarAveriaInexistente() throws Exception {
        postReq("/api/v1/tiendas/pedidos/987654/reportar-averia", login(MARTA, MARTA_PASS), Map.of("descripcion", "x"))
                .andExpect(status().isNotFound());
    }
}
