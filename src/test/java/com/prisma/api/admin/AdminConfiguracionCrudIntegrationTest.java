package com.prisma.api.admin;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminConfiguracionCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenAdmin;
    private String tokenProveedor;

    @BeforeEach
    void setUp() throws Exception {
        tokenAdmin = login("admin@prisma.com.co", "admin123");
        tokenProveedor = login("ventas@postobon.com.co", "postobon123");
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
    @DisplayName("CRUD Configuraciones del Sistema: Listar parámetros y actualizar valor en caliente")
    void testCrudConfiguraciones() throws Exception {
        // 1. READ: Consultar configuraciones del sistema
        String res = mockMvc.perform(get("/api/v1/admin/configuraciones")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray())
                .andReturn().getResponse().getContentAsString();

        // 2. UPDATE: Modificar un parámetro operativo existente
        Map<String, String> payload = Map.of(
                "valor", "3.00",
                "descripcion", "Porcentaje de comisión actualizado para pruebas"
        );

        mockMvc.perform(put("/api/v1/admin/configuraciones/comision_porcentaje")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.clave").value("comision_porcentaje"))
                .andExpect(jsonPath("$.datos.valor").value("3.00"));
    }

    @Test
    @DisplayName("Seguridad RBAC: Proveedor no puede modificar configuraciones globales (403 Forbidden)")
    void testProveedorNoPuedeModificarConfiguraciones() throws Exception {
        Map<String, String> payload = Map.of("valor", "999");
        mockMvc.perform(put("/api/v1/admin/configuraciones/comision_porcentaje")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }
}
