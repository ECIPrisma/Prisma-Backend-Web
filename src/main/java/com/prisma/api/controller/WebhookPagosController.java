package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.pagos.FirmaEventosWompi;
import com.prisma.api.service.ConfirmacionPagoService;
import com.prisma.api.service.WebhookPagosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-13 / HU-14 – Endpoint público donde la pasarela notifica el resultado de las transacciones (webhook).
 * No usa JWT: la autenticidad se valida con la firma HMAC-SHA256 del encabezado X-Wompi-Signature,
 * calculada sobre el cuerpo crudo (por eso se recibe como bytes y no como objeto).
 */
@RestController
@RequestMapping("/api/v1/pagos/webhooks")
@Tag(name = "Webhooks de Pagos", description = "Confirmación de transacciones enviada por la pasarela (simulada)")
public class WebhookPagosController {

    private final WebhookPagosService webhookPagosService;

    public WebhookPagosController(WebhookPagosService webhookPagosService) {
        this.webhookPagosService = webhookPagosService;
    }

    @PostMapping("/wompi")
    @Operation(summary = "Recibir evento transaction.updated",
            description = "Requiere el encabezado X-Wompi-Signature: t=<epoch>,v1=<HMAC-SHA256 hex de \"t.cuerpo\">. "
                    + "Firma inválida, ausente o vencida (>5 min): 401 y registro en el log de seguridad. Idempotente.")
    public ResponseEntity<ApiResponse<Map<String, String>>> recibir(
            @RequestBody(required = false) byte[] cuerpo,
            @RequestHeader(value = FirmaEventosWompi.ENCABEZADO, required = false) String firma,
            HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();

        ConfirmacionPagoService.Resultado resultado =
                webhookPagosService.recibir(cuerpo, firma, ip, request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.exito("Evento recibido", Map.of("resultado", resultado.name())));
    }
}
