package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.TotpCodeRequest;
import com.prisma.api.dto.TotpDisableRequest;
import com.prisma.api.dto.TotpSetupResponse;
import com.prisma.api.dto.TotpStatusResponse;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.TotpAuthService;
import com.prisma.api.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/totp")
@Tag(name = "Autenticación en dos pasos", description = "Configuración opcional de TOTP compatible con Google Authenticator")
public class TotpController {

    private final TotpAuthService totpAuthService;
    private final RefreshTokenService refreshTokenService;

    public TotpController(TotpAuthService totpAuthService, RefreshTokenService refreshTokenService) {
        this.totpAuthService = totpAuthService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/status")
    @Operation(summary = "Consultar si el usuario tiene TOTP activado")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> status(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito(
                "Estado de autenticación en dos pasos",
                totpAuthService.status(currentUser.getId())));
    }

    @PostMapping("/setup")
    @Operation(summary = "Generar secreto y URI para registrar una app autenticadora")
    public ResponseEntity<ApiResponse<TotpSetupResponse>> setup(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito(
                "Escanea el código QR y confirma con el código de tu app.",
                totpAuthService.beginSetup(currentUser.getId())));
    }

    @PostMapping("/enable")
    @Operation(summary = "Confirmar configuración y activar TOTP")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> enable(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TotpCodeRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenService.borrarCookie().toString())
                .body(ApiResponse.exito(
                        "Autenticación en dos pasos activada. Inicia sesión de nuevo usando tu código.",
                        totpAuthService.confirmSetup(currentUser.getId(), request.getCodigo())));
    }

    @DeleteMapping
    @Operation(summary = "Desactivar TOTP tras confirmar contraseña y código actual")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> disable(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TotpDisableRequest request) {
        return ResponseEntity.ok(ApiResponse.exito(
                "Autenticación en dos pasos desactivada.",
                totpAuthService.disable(currentUser.getId(), request.getPassword(), request.getCodigo())));
    }
}
