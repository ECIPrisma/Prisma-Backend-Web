package com.prisma.auth.controller;

import com.prisma.auth.dto.*;
import com.prisma.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Endpoints de Login, Registro, Validación de JWT y Perfil de Usuario")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener JWT con claims de rol")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.ok("Inicio de sesión exitoso", response));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error en el servidor al autenticar: " + ex.getMessage()));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario en el sistema")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Usuario registrado exitosamente", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error al registrar usuario: " + ex.getMessage()));
        }
    }

    @PostMapping("/validate")
    @Operation(summary = "Validar token JWT para otros microservicios o Gateway")
    public ResponseEntity<ApiResponse<ValidateTokenResponse>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> body) {

        String token = null;
        if (authHeader != null && !authHeader.isBlank()) {
            token = authHeader;
        } else if (body != null && body.containsKey("token")) {
            token = body.get("token");
        }

        if (token == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("No se proporcionó token en Authorization header ni en el body"));
        }

        ValidateTokenResponse response = authService.validateToken(token);
        if (response.isValido()) {
            return ResponseEntity.ok(ApiResponse.ok("Token verificado correctamente", response));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.ok(response.getMensaje(), response));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener información del usuario actual mediante token Bearer")
    public ResponseEntity<ApiResponse<AuthResponse.UserData>> getMe(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Cabecera Authorization ausente"));
        }

        try {
            AuthResponse.UserData user = authService.getProfileFromToken(authHeader);
            return ResponseEntity.ok(ApiResponse.ok("Perfil recuperado exitosamente", user));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Sesión inválida: " + ex.getMessage()));
        }
    }
}
