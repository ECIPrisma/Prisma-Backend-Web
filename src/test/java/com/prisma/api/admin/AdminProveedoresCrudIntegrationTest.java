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

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProveedoresCrudIntegrationTest {

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
    @DisplayName("CRUD Empresas Proveedoras (Admin): Listar, Crear, Actualizar Datos, Cambiar Estado y Suspender")
    void testCrudCompletoProveedoresAdmin() throws Exception {
        // 1. READ: Listar empresas proveedoras
        mockMvc.perform(get("/api/v1/admin/proveedores")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 2. CREATE: Registrar nueva empresa proveedora
        Map<String, Object> nuevoProveedor = new HashMap<>();
        nuevoProveedor.put("nit", "800.777.888-9");
        nuevoProveedor.put("razonSocial", "Bavaria & Cía S.C.A.");
        nuevoProveedor.put("nombreComercial", "Bavaria Colombia");
        nuevoProveedor.put("contactoNombre", "Alejandro Gómez");
        nuevoProveedor.put("contactoEmail", "distribucion@bavaria.com.co");
        nuevoProveedor.put("contactoTelefono", "3101112233");
        nuevoProveedor.put("direccionSede", "Autopista Norte # 200");
        nuevoProveedor.put("ciudad", "Bogotá");
        nuevoProveedor.put("comisionPorcentaje", 4.00);

        String resCrear = mockMvc.perform(post("/api/v1/admin/proveedores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoProveedor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.nit").value("800.777.888-9"))
                .andExpect(jsonPath("$.datos.nombreComercial").value("Bavaria Colombia"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andReturn().getResponse().getContentAsString();

        Number proveedorId = JsonPath.read(resCrear, "$.datos.id");

        // 3. UPDATE: Actualizar datos de contacto y comisión
        Map<String, Object> actualizacion = new HashMap<>();
        actualizacion.put("contactoTelefono", "3109990011");
        actualizacion.put("comisionPorcentaje", 3.75);

        mockMvc.perform(put("/api/v1/admin/proveedores/" + proveedorId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.contactoTelefono").value("3109990011"))
                .andExpect(jsonPath("$.datos.comisionPorcentaje").value(3.75));

        // 4. UPDATE ESTADO: Cambiar estado a SUSPENDIDO
        Map<String, String> estadoPayload = Map.of("estado", "SUSPENDIDO");
        mockMvc.perform(put("/api/v1/admin/proveedores/" + proveedorId + "/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(estadoPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.estado").value("SUSPENDIDO"));

        // 5. DELETE: Eliminar / Suspender empresa proveedora
        mockMvc.perform(delete("/api/v1/admin/proveedores/" + proveedorId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Seguridad RBAC: Proveedor regular no puede crear ni gestionar empresas de admin (403 Forbidden)")
    void testProveedorNoPuedeGestionarEmpresas() throws Exception {
        Map<String, Object> payload = Map.of("nit", "999.999.999-9", "razonSocial", "Intruso S.A.");
        mockMvc.perform(post("/api/v1/admin/proveedores")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }
}
