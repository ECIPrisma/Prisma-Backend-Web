package com.prisma.api.proveedor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProveedorCatalogoCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenProveedor;
    private String tokenTendero;

    @BeforeEach
    void setUp() throws Exception {
        tokenProveedor = login("ventas@postobon.com.co", "postobon123");
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        String res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.datos.token");
    }

    @Test
    @DisplayName("CRUD Completo de Catálogo: Listar, Crear, Actualizar y Retirar producto")
    void testCrudCompletoCatalogo() throws Exception {
        // 1. READ: Listar catálogo actual
        mockMvc.perform(get("/api/v1/proveedores/mis-productos")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 2. CREATE: Crear un nuevo producto en catálogo
        Map<String, Object> nuevoProducto = new HashMap<>();
        nuevoProducto.put("sku", "TEST-CRUD-001");
        nuevoProducto.put("nombre", "Té Hatsu Blanco 400ml");
        nuevoProducto.put("descripcion", "Bebida de té con carambolo y flor de loto");
        nuevoProducto.put("presentacion", "Botella de vidrio 400ml");
        nuevoProducto.put("precioUnitario", 4800);
        nuevoProducto.put("precioSugeridoPublico", 6000);
        nuevoProducto.put("stockDisponible", 100);
        nuevoProducto.put("cantidadMinimaPedido", 6);
        nuevoProducto.put("esRetornable", false);
        nuevoProducto.put("imagenUrl", "/logo_icono_tienda.png");
        nuevoProducto.put("categoriaId", 1);

        String createRes = mockMvc.perform(post("/api/v1/proveedores/productos")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoProducto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nombre").value("Té Hatsu Blanco 400ml"))
                .andExpect(jsonPath("$.datos.sku").value("TEST-CRUD-001"))
                .andExpect(jsonPath("$.datos.stockDisponible").value(100))
                .andReturn().getResponse().getContentAsString();

        Number productoIdNum = JsonPath.read(createRes, "$.datos.id");
        Long productoId = productoIdNum.longValue();

        // 3. UPDATE: Actualizar precio, stock y nombre
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("nombre", "Té Hatsu Blanco 400ml (Edición Especial)");
        updateData.put("precioUnitario", 5200);
        updateData.put("stockDisponible", 150);
        updateData.put("cantidadMinimaPedido", 12);

        mockMvc.perform(put("/api/v1/proveedores/productos/" + productoId)
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nombre").value("Té Hatsu Blanco 400ml (Edición Especial)"))
                .andExpect(jsonPath("$.datos.precioUnitario").value(5200.0))
                .andExpect(jsonPath("$.datos.stockDisponible").value(150))
                .andExpect(jsonPath("$.datos.cantidadMinimaPedido").value(12));

        // 4. DELETE: Archivar / Retirar producto
        mockMvc.perform(delete("/api/v1/proveedores/productos/" + productoId)
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));

        // 5. VERIFY: Producto eliminado ya no debe aparecer en mis-productos
        mockMvc.perform(get("/api/v1/proveedores/mis-productos")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id == " + productoId + ")]").doesNotExist());
    }

    @Test
    @DisplayName("Seguridad: Un tendero no puede crear ni modificar productos del proveedor (HTTP 403)")
    void testTenderoNoPuedeModificarCatalogo() throws Exception {
        Map<String, Object> payload = Map.of("nombre", "Hack", "precioUnitario", 100);

        mockMvc.perform(post("/api/v1/proveedores/productos")
                        .header("Authorization", "Bearer " + tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/proveedores/productos/1")
                        .header("Authorization", "Bearer " + tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/proveedores/productos/1")
                        .header("Authorization", "Bearer " + tokenTendero))
                .andExpect(status().isForbidden());
    }
}
