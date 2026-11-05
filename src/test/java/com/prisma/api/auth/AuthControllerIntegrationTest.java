package com.prisma.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.dto.LoginRequest;
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
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Login con credenciales válidas retorna token JWT y rol correcto")
    void testLoginExitoso() throws Exception {
        LoginRequest req = new LoginRequest("admin@prisma.com.co", "admin123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(jsonPath("$.datos.email").value("admin@prisma.com.co"))
                .andExpect(jsonPath("$.datos.rol").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta retorna 401 Unauthorized")
    void testLoginPasswordInvalido() throws Exception {
        LoginRequest req = new LoginRequest("admin@prisma.com.co", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login con correo inexistente retorna 401 Unauthorized")
    void testLoginUsuarioInexistente() throws Exception {
        LoginRequest req = new LoginRequest("noexiste@empresa.com", "cualquiera123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login con payload inválido retorna 400 Bad Request")
    void testLoginCamposVacios() throws Exception {
        LoginRequest req = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /auth/me con token válido retorna el perfil del usuario autenticado")
    void testMeConTokenValido() throws Exception {
        LoginRequest loginReq = new LoginRequest("admin@prisma.com.co", "admin123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.datos.token");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.username").value("admin@prisma.com.co"));
    }

    @Test
    @DisplayName("GET /auth/me sin token retorna datos nulos de usuario")
    void testMeSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").doesNotExist());
    }
}
