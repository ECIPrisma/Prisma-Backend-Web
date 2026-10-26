package com.prisma.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias del emisor/validador de JWT (sin Spring). */
class JwtTokenProviderTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String OTRO_SECRET = "9F8E7D6C5B4A39281716051413121110FEDCBA98765432100123456789ABCDEF";

    private Authentication auth;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = UserPrincipal.createFromClaims(7L, "ana@test.com", "Ana Test", "ROLE_PROVEEDOR", 3L);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("Un token recién emitido es válido y conserva identidad, rol y proveedor")
    void tokenValidoConservaClaims() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        String token = provider.generateToken(auth);

        assertThat(provider.validateToken(token)).isTrue();
        Claims claims = provider.getClaimsFromJWT(token);
        assertThat(provider.getUserIdFromJWT(token)).isEqualTo(7L);
        assertThat(claims.get("email", String.class)).isEqualTo("ana@test.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("ROLE_PROVEEDOR");
        assertThat(claims.get("proveedorId", Long.class)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Un token vencido NO es válido")
    void tokenVencidoEsRechazado() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1_000);
        assertThat(provider.validateToken(provider.generateToken(auth))).isFalse();
    }

    @Test
    @DisplayName("Un token firmado con otra clave NO es válido")
    void tokenConOtraClaveEsRechazado() {
        JwtTokenProvider ajeno = new JwtTokenProvider(OTRO_SECRET, 60_000);
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        assertThat(provider.validateToken(ajeno.generateToken(auth))).isFalse();
    }

    @Test
    @DisplayName("Un token con el payload alterado NO es válido")
    void tokenAlteradoEsRechazado() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        String[] partes = provider.generateToken(auth).split("\\.");
        // Payload falso con rol de administrador pero la firma original
        String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"7\",\"rol\":\"ROLE_ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String alterado = partes[0] + "." + payloadFalso + "." + partes[2];
        assertThat(provider.validateToken(alterado)).isFalse();
    }

    @Test
    @DisplayName("Un token sin firma (alg=none) NO es válido")
    void tokenSinFirmaEsRechazado() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        String sinFirma = Jwts.builder().subject("7").claim("rol", "ROLE_ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        assertThat(provider.validateToken(sinFirma)).isFalse();
    }

    @Test
    @DisplayName("Textos que no son JWT (basura, vacío, nulo) NO son válidos")
    void basuraEsRechazada() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        assertThat(provider.validateToken("esto-no-es-un-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
        assertThat(provider.validateToken(null)).isFalse();
    }
}
