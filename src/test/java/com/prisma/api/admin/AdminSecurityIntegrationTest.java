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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenAdmin;
    private String tokenProveedor;

    @BeforeEach
    void setUp() throws Exception {
        tokenAdmin = login("admin@prisma.com.co", "admin123");
        tokenProveedor = login("logistica@femsa.com.co", "femsa123");
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
    @DisplayName("Admin puede consultar la lista de configuraciones del sistema (HTTP 200)")
    void testConsultarConfiguracionesComoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/configuraciones")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Proveedor no tiene permisos para ver configuraciones de admin (HTTP 403 Forbidden)")
    void testConsultarConfiguracionesComoProveedorBloqueado() throws Exception {
        mockMvc.perform(get("/api/v1/admin/configuraciones")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Usuario anónimo sin token recibe 403 Forbidden al acceder a admin")
    void testAccesoAnonimoAdminBloqueado() throws Exception {
        mockMvc.perform(get("/api/v1/admin/configuraciones"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede consultar el registro inmutable de auditoría (HTTP 200)")
    void testConsultarAuditoriaComoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Proveedor no puede acceder a los logs de auditoría (HTTP 403 Forbidden)")
    void testConsultarAuditoriaComoProveedorBloqueado() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auditoria")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede listar los proveedores del sistema (HTTP 200)")
    void testListarProveedoresComoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/proveedores")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Admin puede listar los usuarios del sistema (HTTP 200)")
    void testListarUsuariosComoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    @DisplayName("Proveedor no puede listar los usuarios del sistema (HTTP 403 Forbidden)")
    void testListarUsuariosComoProveedorBloqueado() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin puede restablecer y desactivar el 2FA de un usuario (HTTP 200)")
    void testResetTotpUsuarioComoAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios/1/reset-totp")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.enabled").value(false));
    }

    @Test
    @DisplayName("Proveedor no puede restablecer el 2FA de un usuario (HTTP 403 Forbidden)")
    void testResetTotpUsuarioComoProveedorBloqueado() throws Exception {
        mockMvc.perform(post("/api/v1/admin/usuarios/1/reset-totp")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isForbidden());
    }
}
