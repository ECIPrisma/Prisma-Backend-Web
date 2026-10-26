package com.prisma.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.auth.dto.LoginRequest;
import com.prisma.auth.dto.RegisterRequest;
import com.prisma.auth.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void testLoginExitosoAdmin() throws Exception {
        LoginRequest loginReq = new LoginRequest("admin@prisma.com.co", "admin123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(jsonPath("$.datos.usuario.email").value("admin@prisma.com.co"))
                .andExpect(jsonPath("$.datos.usuario.rol").value("ROLE_ADMIN"));
    }

    @Test
    void testLoginPasswordIncorrecto() throws Exception {
        LoginRequest loginReq = new LoginRequest("admin@prisma.com.co", "clave_erronea");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void testRegistroNuevoUsuarioYValidacionToken() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setEmail("nuevo.tendero@tienda.com");
        registerReq.setPassword("password123");
        registerReq.setNombreCompleto("Pedro Pérez");
        registerReq.setTelefono("3112223344");
        registerReq.setRol(Rol.ROLE_TENDERO);

        String responseContent = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseContent).path("datos").path("token").asText();

        // Validar token emitido
        mockMvc.perform(post("/api/v1/auth/validate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.valido").value(true))
                .andExpect(jsonPath("$.datos.email").value("nuevo.tendero@tienda.com"))
                .andExpect(jsonPath("$.datos.rol").value("ROLE_TENDERO"));

        // Consultar /me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombreCompleto").value("Pedro Pérez"));
    }
}
