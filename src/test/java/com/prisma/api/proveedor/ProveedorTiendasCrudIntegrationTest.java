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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProveedorTiendasCrudIntegrationTest {

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
    @DisplayName("CRUD Tiendas Cliente: Listar, Registrar, Actualizar, Inactivar y Análisis de Ventas")
    void testCrudCompletoTiendaCliente() throws Exception {
        // 1. READ: Listar tiendas clientes del proveedor
        mockMvc.perform(get("/api/v1/proveedores/tiendas")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 2. CREATE: Registrar nueva tienda cliente vinculada
        Map<String, String> nuevaTienda = new HashMap<>();
        nuevaTienda.put("nombreTienda", "Cigarrería y Abarrotes El Triunfo");
        nuevaTienda.put("nombreTendero", "Don Fernando Castro");
        nuevaTienda.put("nitOCedula", "CC-80123456");
        nuevaTienda.put("direccion", "Carrera 15 # 45-60");
        nuevaTienda.put("barrio", "Chapinero");
        nuevaTienda.put("ciudad", "Bogotá");
        nuevaTienda.put("telefono", "3158889900");
        nuevaTienda.put("diaPreferidoEntrega", "Lunes y Jueves");

        String resCrear = mockMvc.perform(post("/api/v1/proveedores/tiendas")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevaTienda)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nombreTienda").value("Cigarrería y Abarrotes El Triunfo"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVA"))
                .andReturn().getResponse().getContentAsString();

        Number tiendaId = JsonPath.read(resCrear, "$.datos.id");

        // 3. UPDATE: Actualizar datos comerciales de la tienda
        Map<String, String> actualizacion = new HashMap<>();
        actualizacion.put("nombreTienda", "Super Cigarrería El Triunfo Plus");
        actualizacion.put("telefono", "3158889911");
        actualizacion.put("diaPreferidoEntrega", "Viernes");

        mockMvc.perform(put("/api/v1/proveedores/tiendas/" + tiendaId)
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nombreTienda").value("Super Cigarrería El Triunfo Plus"))
                .andExpect(jsonPath("$.datos.telefono").value("3158889911"));

        // 4. READ: Análisis comercial y de ventas de la tienda
        mockMvc.perform(get("/api/v1/proveedores/tiendas/" + tiendaId + "/analisis-ventas")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nombreTienda").exists());

        // 5. DELETE: Inactivar tienda cliente
        mockMvc.perform(delete("/api/v1/proveedores/tiendas/" + tiendaId)
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Seguridad RBAC: Tendero no puede registrar ni administrar tiendas de otros (403 Forbidden)")
    void testTenderoNoPuedeAdministrarTiendas() throws Exception {
        Map<String, String> payload = Map.of("nombreTienda", "Tienda Ilegal");
        mockMvc.perform(post("/api/v1/proveedores/tiendas")
                        .header("Authorization", "Bearer " + tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }
}
