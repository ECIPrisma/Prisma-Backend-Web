package com.prisma.api.auth;

import com.prisma.api.service.RefreshTokenService;
import com.prisma.api.repository.RefreshTokenRepository;
import com.prisma.api.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-03 – Renovación de sesión con refresh token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"admin@prisma.com.co\",\"password\":\"admin123\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(cookie().httpOnly(RefreshTokenService.COOKIE_NAME, true))
                .andExpect(cookie().path(RefreshTokenService.COOKIE_NAME, "/api/v1/auth"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(RefreshTokenService.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    @Test
    void refreshConCookieValidaEmiteNuevoJwt() throws Exception {
        Cookie refresh = login();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.email").value("admin@prisma.com.co"))
                .andExpect(jsonPath("$.datos.rol").value("ROLE_ADMIN"))
                .andReturn();

        String nuevoJwt = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.datos.token");
        assertThat(tokenProvider.validateToken(nuevoJwt)).isTrue();
    }

    @Test
    void refreshSinCookieResponde401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exitoso").value(false));
    }

    @Test
    void refreshConTokenDesconocidoResponde401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenService.COOKIE_NAME, "no-existe")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTrasLogoutResponde401() throws Exception {
        Cookie refresh = login();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(RefreshTokenService.COOKIE_NAME, 0));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshConTokenExpiradoResponde401() throws Exception {
        Cookie refresh = login();

        refreshTokenRepository.findAll().forEach(token -> {
            token.setExpiraEn(OffsetDateTime.now().minusMinutes(1));
            refreshTokenRepository.save(token);
        });

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }
}
