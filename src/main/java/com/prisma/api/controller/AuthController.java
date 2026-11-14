package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Endpoints de inicio de sesión y verificación de credenciales JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con email y contraseña", description = "Genera un token JWT con el rol y tenant del usuario")
    @AuditableAction(action = "LOGIN_INTENTO", severity = SeveridadAudit.INFO, resourceType = "USUARIO")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.authenticate(loginRequest);
        return ResponseEntity.ok(ApiResponse.exito("Autenticación exitosa", authResponse));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener perfil del usuario autenticado", description = "Retorna los datos del usuario extraídos del token JWT actual")
    public ResponseEntity<ApiResponse<UserPrincipal>> me(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Usuario autenticado", currentUser));
    }
}
