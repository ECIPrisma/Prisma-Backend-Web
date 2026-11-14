package com.prisma.api.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.prisma.api.entity.ConfiguracionSistema;
import com.prisma.api.entity.Producto;
import com.prisma.api.repository.ConfiguracionSistemaRepository;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * N-02 (comisión parametrizable, hoy 2.5 %), HU-09 (idempotencia del pedido) y stock.
 * Cubre lo que debe pasar y lo que NO debe pasar.
 */
class PedidoComisionIdempotenciaIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ConfiguracionSistemaRepository configuracionRepository;

    private Producto producto() {
        return productoRepository.findByProveedorIdAndSku(femsa().getId(), "KO-PET-400").orElseThrow();
    }

    private Map<String, Object> cuerpo(Producto p, int cantidad, String idempotencyKey) {
        Map<String, Object> c = new HashMap<>();
        c.put("proveedorId", femsa().getId());
        c.put("items", items(p.getId(), cantidad));
        if (idempotencyKey != null) {
            c.put("idempotencyKey", idempotencyKey);
        }
        return c;
    }

    private void fijarComision(String valor) {
        configuracionRepository.save(new ConfiguracionSistema(
                "comision_porcentaje", valor, "DECIMAL", "Porcentaje retenido por PRISMA", "OPERACIONES"));
        configuracionRepository.flush();
    }

    private BigDecimal comisionDeNuevoPedido(Producto p, int cantidad) throws Exception {
        JsonNode datos = leer(postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpo(p, cantidad, null))
                .andExpect(status().isOk())).path("datos");
        assertThat(datos.path("subtotal").decimalValue())
                .isEqualByComparingTo(p.getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad)));
        return datos.path("comisionPrismaValor").decimalValue();
    }

    // ============================================================ COMISIÓN
    @Test
    @DisplayName("Sin cambios, la comisión es 2.5 % del subtotal (valor sembrado en configuracion_sistema)")
    void comisionPorDefecto() throws Exception {
        Producto p = producto();
        BigDecimal esperado = p.getPrecioUnitario().multiply(BigDecimal.valueOf(2)).multiply(new BigDecimal("0.025"));
        assertThat(comisionDeNuevoPedido(p, 2)).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("Si el admin cambia comision_porcentaje a 3.5, los pedidos nuevos usan 3.5 % sin tocar el código")
    void comisionSeLeeDeLaConfiguracion() throws Exception {
        fijarComision("3.5");
        Producto p = producto();
        BigDecimal esperado = p.getPrecioUnitario().multiply(BigDecimal.valueOf(2)).multiply(new BigDecimal("0.035"));
        assertThat(comisionDeNuevoPedido(p, 2)).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("Una comisión inválida (texto, negativa o mayor a 100) NO se aplica: se usa 2.5 %")
    void comisionInvalidaCaeAlDefecto() throws Exception {
        Producto p = producto();
        BigDecimal esperado = p.getPrecioUnitario().multiply(BigDecimal.valueOf(1)).multiply(new BigDecimal("0.025"));
        for (String invalido : new String[] {"abc", "-5", "150", ""}) {
            fijarComision(invalido);
            assertThat(comisionDeNuevoPedido(p, 1)).as("valor configurado '%s'", invalido).isEqualByComparingTo(esperado);
        }
    }

    @Test
    @DisplayName("Cambiar la comisión no altera pedidos ya creados")
    void comisionNoCambiaPedidosPrevios() throws Exception {
        Producto p = producto();
        String token = login(MARTA, MARTA_PASS);
        JsonNode previo = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 1, null))
                .andExpect(status().isOk())).path("datos");
        BigDecimal comisionPrevia = previo.path("comisionPrismaValor").decimalValue();

        fijarComision("9");
        assertThat(pedido(previo.path("numeroPedido").asText()).getComisionPrismaValor())
                .isEqualByComparingTo(comisionPrevia);
    }

    // ============================================================ IDEMPOTENCIA
    @Test
    @DisplayName("La misma Idempotency-Key dos veces devuelve el MISMO pedido y no crea otro ni descuenta stock dos veces")
    void mismaClaveNoDuplica() throws Exception {
        Producto p = producto();
        int stockInicial = p.getStockDisponible();
        String token = login(MARTA, MARTA_PASS);
        long antes = pedidoRepository.count();

        JsonNode primero = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 2, "clave-offline-001"))
                .andExpect(status().isOk())).path("datos");
        JsonNode segundo = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 2, "clave-offline-001"))
                .andExpect(status().isOk())).path("datos");

        assertThat(segundo.path("id").asLong()).isEqualTo(primero.path("id").asLong());
        assertThat(pedidoRepository.count()).isEqualTo(antes + 1);
        assertThat(productoRepository.findById(p.getId()).orElseThrow().getStockDisponible()).isEqualTo(stockInicial - 2);
    }

    @Test
    @DisplayName("Claves distintas crean pedidos distintos")
    void clavesDistintasCreanPedidosDistintos() throws Exception {
        Producto p = producto();
        String token = login(MARTA, MARTA_PASS);
        long a = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 1, "clave-A")).andExpect(status().isOk()))
                .path("datos").path("id").asLong();
        long b = leer(postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 1, "clave-B")).andExpect(status().isOk()))
                .path("datos").path("id").asLong();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("La clave también funciona como cabecera Idempotency-Key")
    void claveComoCabecera() throws Exception {
        Producto p = producto();
        String token = login(MARTA, MARTA_PASS);
        long antes = pedidoRepository.count();
        for (int i = 0; i < 2; i++) {
            postReq("/api/v1/tiendas/pedidos", token, cuerpo(p, 1, null), "Idempotency-Key", "cab-777")
                    .andExpect(status().isOk());
        }
        assertThat(pedidoRepository.count()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("Una clave usada por OTRO tendero NO devuelve su pedido (no se filtran datos ajenos) -> 409 y no se crea nada")
    void claveDeOtroTenderoNoSeFiltra() throws Exception {
        Producto p = producto();
        JsonNode pedidoDeMarta = leer(postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpo(p, 1, "clave-compartida"))
                .andExpect(status().isOk())).path("datos");
        String tokenPedro = registrarTendero("pedro.idem@test.com", "9.999.001");
        long antes = pedidoRepository.count();

        String respuesta = postReq("/api/v1/tiendas/pedidos", tokenPedro, cuerpo(p, 1, "clave-compartida"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();

        String pinDeMarta = pedidoDeMarta.path("pinEntregaHash").asText();
        assertThat(pinDeMarta).matches("\\d{6}");
        assertThat(respuesta).doesNotContain(pinDeMarta).doesNotContain(pedidoDeMarta.path("numeroPedido").asText());
        assertThat(pedidoRepository.count()).isEqualTo(antes);
    }

    // ============================================================ STOCK
    @Test
    @DisplayName("Pedir más unidades que el stock -> 400 y el stock no cambia")
    void stockInsuficiente() throws Exception {
        Producto p = producto();
        int stock = p.getStockDisponible();
        long antes = pedidoRepository.count();

        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpo(p, stock + 1, null))
                .andExpect(status().isBadRequest());

        assertThat(pedidoRepository.count()).isEqualTo(antes);
        assertThat(productoRepository.findById(p.getId()).orElseThrow().getStockDisponible()).isEqualTo(stock);
    }

    @Test
    @DisplayName("Pedir exactamente el stock disponible es válido y lo deja en 0")
    void pedirTodoElStock() throws Exception {
        Producto p = producto();
        postReq("/api/v1/tiendas/pedidos", login(MARTA, MARTA_PASS), cuerpo(p, p.getStockDisponible(), null))
                .andExpect(status().isOk());
        assertThat(productoRepository.findById(p.getId()).orElseThrow().getStockDisponible()).isZero();
    }
}
