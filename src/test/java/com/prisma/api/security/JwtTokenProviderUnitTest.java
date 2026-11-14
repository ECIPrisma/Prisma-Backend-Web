package com.prisma.api.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderUnitTest {

    private static final String SECRET = "EstaEsUnaClaveSecretaSuperLargaYSeguraParaPruebasUnitariasHMACSHA512PRISMA2026!";
    private static final long EXPIRATION_MS = 3600000; // 1 hora

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Generar token JWT crea una cadena firmada no vacía con 3 partes")
    void testGenerarTokenEstructura() {
        UserPrincipal principal = new UserPrincipal(
                1L, "admin@prisma.com.co", "passwordHash", "Admin General",
                null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token = tokenProvider.generateToken(auth);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Obtener Claims desde token JWT decodifica subject, email y proveedorId")
    void testGetClaimsFromJWT() {
        UserPrincipal principal = new UserPrincipal(
                5L, "repartidor@femsa.com.co", "hash", "Carlos Repartidor",
                10L, Collections.singletonList(new SimpleGrantedAuthority("ROLE_REPARTIDOR"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token = tokenProvider.generateToken(auth);
        Claims claims = tokenProvider.getClaimsFromJWT(token);

        assertThat(claims.getSubject()).isEqualTo("5");
        assertThat(claims.get("email", String.class)).isEqualTo("repartidor@femsa.com.co");
        assertThat(claims.get("nombre", String.class)).isEqualTo("Carlos Repartidor");
        assertThat(claims.get("proveedorId", Long.class)).isEqualTo(10L);
        assertThat(tokenProvider.getUserIdFromJWT(token)).isEqualTo(5L);
    }

    @Test
    @DisplayName("Validar token válido retorna true")
    void testValidateTokenValido() {
        UserPrincipal principal = new UserPrincipal(
                2L, "tendero@oasis.com", "hash", "Don Pedro",
                null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        String token = tokenProvider.generateToken(auth);

        boolean isValid = tokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Validar token corrupto retorna false")
    void testValidateTokenCorrupto() {
        boolean isValid = tokenProvider.validateToken("token.invalido.corrupto");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Validar token nulo o vacío retorna false")
    void testValidateTokenVacio() {
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }
}
