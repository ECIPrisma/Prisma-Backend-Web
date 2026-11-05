package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones Push (FCM)", description = "Registro de tokens Firebase Cloud Messaging y buzón de alertas de pedidos (SCRUM-61)")
public class NotificacionController {

    private final PushNotificationService pushNotificationService;

    public NotificacionController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/fcm-token")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registrar token FCM del dispositivo móvil o navegador para recibir notificaciones push")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registrarTokenFcm(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        String token = body.getOrDefault("fcmToken", body.get("token"));
        if (token == null || token.isBlank()) {
            throw new BadRequestException("El token FCM es obligatorio");
        }
        pushNotificationService.registrarFcmToken(currentUser.getId(), token);
        return ResponseEntity.ok(ApiResponse.exito("Token FCM registrado exitosamente", Map.of(
                "usuarioId", currentUser.getId(),
                "fcmToken", token,
                "estado", "REGISTRADO"
        )));
    }

    @GetMapping("/mis-notificaciones")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consultar notificaciones push enviadas al usuario autenticado")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMisNotificaciones(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<Map<String, Object>> lista = pushNotificationService.obtenerNotificacionesUsuario(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.exito("Notificaciones del usuario", lista));
    }
}
