package com.prisma.api.service.impl;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.entity.RefreshToken;
import com.prisma.api.entity.Usuario;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.RefreshTokenRepository;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.JwtTokenProvider;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SESION_EXPIRADA = "Sesión expirada. Inicie sesión nuevamente.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider tokenProvider;
    private final long refreshExpirationMs;
    private final boolean cookieSecure;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                   UsuarioRepository usuarioRepository,
                                   JwtTokenProvider tokenProvider,
                                   @Value("${prisma.jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs,
                                   @Value("${prisma.jwt.refresh-cookie-secure:true}") boolean cookieSecure) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.tokenProvider = tokenProvider;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cookieSecure = cookieSecure;
    }

    @Override
    @Transactional
    public String emitir(Long usuarioId) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Usuario usuario = usuarioRepository.getReferenceById(usuarioId);
        OffsetDateTime expiraEn = OffsetDateTime.now().plus(Duration.ofMillis(refreshExpirationMs));
        refreshTokenRepository.save(new RefreshToken(usuario, hash(token), expiraEn));
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse renovar(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, SESION_EXPIRADA);
        }
        RefreshToken almacenado = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(RefreshToken::isVigente)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, SESION_EXPIRADA));

        Usuario usuario = almacenado.getUsuario();
        if (!"ACTIVO".equals(usuario.getEstado())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La cuenta del usuario no está activa.");
        }

        UserPrincipal principal = UserPrincipal.create(usuario);
        String jwt = tokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        String rol = principal.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_TENDERO");

        return new AuthResponse(jwt, principal.getId(), principal.getUsername(),
                principal.getNombreCompleto(), rol, principal.getProveedorId());
    }

    @Override
    @Transactional
    public void revocar(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(token -> {
            token.setRevocado(true);
            refreshTokenRepository.save(token);
        });
    }

    @Override
    public ResponseCookie crearCookie(String refreshToken) {
        return construirCookie(refreshToken, Duration.ofMillis(refreshExpirationMs));
    }

    @Override
    public ResponseCookie borrarCookie() {
        return construirCookie("", Duration.ZERO);
    }

    private ResponseCookie construirCookie(String valor, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, valor)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
