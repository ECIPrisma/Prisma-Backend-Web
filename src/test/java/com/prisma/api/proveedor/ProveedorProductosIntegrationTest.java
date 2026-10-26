package com.prisma.api.proveedor;

import com.fasterxml.jackson.databind.JsonNode;
import com.prisma.api.entity.Producto;
import com.prisma.api.enums.Rol;
import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-20 Aislamiento multi-tenant + gestión de catálogo del proveedor. */
class ProveedorProductosIntegrationTest extends IntegrationTestBase {

    private String tokenFemsa;
    private String tokenPostobon;
    private Producto deFemsa;
    private Producto dePostobon;

    @BeforeEach
    void preparar() throws Exception {
        tokenFemsa = login(FEMSA_USER, FEMSA_PASS);
        crearUsuario("ventas@postobon.test", Rol.ROLE_PROVEEDOR, postobon());
        tokenPostobon = login("ventas@postobon.test", CLAVE_TEST);
        deFemsa = productoRepository.findByProveedorIdAndSku(femsa().getId(), "KO-PET-400").orElseThrow();
        dePostobon = crearProducto(postobon(), "PB-001", "ACTIVO", "5000");
    }

    private JsonNode skusDe(String token) throws Exception {
        return leer(getReq("/api/v1/proveedores/mis-productos", token).andExpect(status().isOk())).path("datos");
    }

    // ---------------------------------------------------------------- lectura aislada
    @Test
    @DisplayName("Cada proveedor ve SOLO su catálogo")
    void catalogoAislado() throws Exception {
        assertThat(skusDe(tokenFemsa).findValuesAsText("sku")).contains("KO-PET-400").doesNotContain("PB-001");
        assertThat(skusDe(tokenPostobon).findValuesAsText("sku")).containsExactly("PB-001");
    }

    @Test
    @DisplayName("Un producto retirado (INACTIVO) desaparece de mis-productos")
    void inactivosNoSeListan() throws Exception {
        crearProducto(postobon(), "PB-OFF", "INACTIVO", "1000");
        assertThat(skusDe(tokenPostobon).findValuesAsText("sku")).containsExactly("PB-001");
    }

    // ---------------------------------------------------------------- crear
    @Test
    @DisplayName("Crear producto: queda en el catálogo del proveedor que lo crea y de ningún otro")
    void crearProducto() throws Exception {
        postReq("/api/v1/proveedores/productos", tokenPostobon,
                Map.of("sku", "PB-NEW", "nombre", "Pony Malta 330ml", "presentacion", "Caja x 24", "precioUnitario", 48000, "stockDisponible", 20))
                .andExpect(status().isCreated());

        assertThat(productoRepository.findByProveedorIdAndSku(postobon().getId(), "PB-NEW")).isPresent();
        assertThat(productoRepository.findByProveedorIdAndSku(femsa().getId(), "PB-NEW")).isEmpty();
        assertThat(skusDe(tokenFemsa).findValuesAsText("sku")).doesNotContain("PB-NEW");
    }

    // ---------------------------------------------------------------- actualizar
    @Test
    @DisplayName("Actualizar precio y stock de un producto PROPIO funciona")
    void actualizarPropio() throws Exception {
        putReq("/api/v1/proveedores/productos/" + dePostobon.getId(), tokenPostobon,
                Map.of("precioUnitario", 5500, "stockDisponible", 80))
                .andExpect(status().isOk());
        Producto p = productoRepository.findById(dePostobon.getId()).orElseThrow();
        assertThat(p.getPrecioUnitario()).isEqualByComparingTo("5500");
        assertThat(p.getStockDisponible()).isEqualTo(80);
    }

    @Test
    @DisplayName("Un proveedor NO puede modificar productos de otro -> 403 y el precio no cambia")
    void noActualizaAjeno() throws Exception {
        BigDecimal precioAntes = deFemsa.getPrecioUnitario();
        putReq("/api/v1/proveedores/productos/" + deFemsa.getId(), tokenPostobon, Map.of("precioUnitario", 1))
                .andExpect(status().isForbidden());
        assertThat(productoRepository.findById(deFemsa.getId()).orElseThrow().getPrecioUnitario())
                .isEqualByComparingTo(precioAntes);
    }

    @Test
    @DisplayName("Actualizar un producto inexistente -> 404")
    void actualizarInexistente() throws Exception {
        putReq("/api/v1/proveedores/productos/987654", tokenFemsa, Map.of("precioUnitario", 1000))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- eliminar (archivar)
    @Test
    @DisplayName("Eliminar un producto PROPIO lo archiva (INACTIVO) y deja de listarse")
    void eliminarPropio() throws Exception {
        deleteReq("/api/v1/proveedores/productos/" + dePostobon.getId(), tokenPostobon).andExpect(status().isOk());
        assertThat(productoRepository.findById(dePostobon.getId()).orElseThrow().getEstado()).isEqualTo("INACTIVO");
        assertThat(skusDe(tokenPostobon)).isEmpty();
    }

    @Test
    @DisplayName("Un proveedor NO puede eliminar productos de otro -> 403 y el producto sigue activo")
    void noEliminaAjeno() throws Exception {
        String estadoAntes = deFemsa.getEstado();
        deleteReq("/api/v1/proveedores/productos/" + deFemsa.getId(), tokenPostobon).andExpect(status().isForbidden());
        assertThat(productoRepository.findById(deFemsa.getId()).orElseThrow().getEstado()).isEqualTo(estadoAntes);
    }

    @Test
    @DisplayName("Eliminar un producto inexistente -> 404")
    void eliminarInexistente() throws Exception {
        deleteReq("/api/v1/proveedores/productos/987654", tokenFemsa).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un tendero no puede editar ni eliminar productos -> 403")
    void tenderoNoEditaCatalogo() throws Exception {
        String tokenTendero = login(MARTA, MARTA_PASS);
        putReq("/api/v1/proveedores/productos/" + deFemsa.getId(), tokenTendero, Map.of("precioUnitario", 1)).andExpect(status().isForbidden());
        deleteReq("/api/v1/proveedores/productos/" + deFemsa.getId(), tokenTendero).andExpect(status().isForbidden());
    }
}
