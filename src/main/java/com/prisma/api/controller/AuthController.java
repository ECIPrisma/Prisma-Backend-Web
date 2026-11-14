package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.dto.RegisterRequest;
import com.prisma.api.entity.Usuario;
import com.prisma.api.service.RegistroService;
import org.springframework.http.HttpStatus;
import com.prisma.api.security.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    private final RegistroService registroService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthService authService, RegistroService registroService, JwtTokenProvider tokenProvider) {
        this.authService = authService;
        this.registroService = registroService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/register")
    @Operation(summary = "Registro de tendero (HU-01)", description = "Crea usuario ROLE_TENDERO y su primera tienda; devuelve un JWT listo para usar. El rol nunca lo elige el cliente.")
    @AuditableAction(action = "REGISTRO_TENDERO", severity = SeveridadAudit.INFO, resourceType = "USUARIO")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        Usuario usuario = registroService.registrarTendero(request);
        UserPrincipal principal = UserPrincipal.create(usuario);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        String jwt = tokenProvider.generateToken(authentication);
        AuthResponse body = new AuthResponse(jwt, principal.getId(), principal.getUsername(),
                principal.getNombreCompleto(), "ROLE_TENDERO", principal.getProveedorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Registro exitoso", body));
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
