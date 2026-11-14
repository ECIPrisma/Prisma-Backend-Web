package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.AuthResponse;
import com.prisma.api.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HU-03 – Renovación de sesión con refresh token.
 * El refresh token se emite en /auth/login y /auth/register (ver {@link RefreshTokenCookieAdvice}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Endpoints de inicio de sesión y verificación de credenciales JWT")
public class SesionController {

    private final RefreshTokenService refreshTokenService;

    public SesionController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el JWT de acceso", description = "Emite un nuevo JWT a partir del refresh token de la cookie. Responde 401 si expiró o fue revocado")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken) {
        AuthResponse authResponse = refreshTokenService.renovar(refreshToken);
        return ResponseEntity.ok(ApiResponse.exito("Sesión renovada", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token actual y elimina la cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken) {
        refreshTokenService.revocar(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenService.borrarCookie().toString())
                .body(ApiResponse.exito("Sesión cerrada", null));
    }
}
