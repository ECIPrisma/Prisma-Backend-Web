package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.service.ConfirmacionPagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-13 – Endpoint público donde la pasarela notifica el resultado de las transacciones (webhook).
 * No usa JWT: la autenticidad se valida con el checksum SHA-256 del evento.
 */
@RestController
@RequestMapping("/api/v1/pagos/webhooks")
@Tag(name = "Webhooks de Pagos", description = "Confirmación de transacciones enviada por la pasarela (simulada)")
public class WebhookPagosController {

    private final ConfirmacionPagoService confirmacionPagoService;

    public WebhookPagosController(ConfirmacionPagoService confirmacionPagoService) {
        this.confirmacionPagoService = confirmacionPagoService;
    }

    @PostMapping("/wompi")
    @Operation(summary = "Recibir evento transaction.updated",
            description = "Valida el checksum (properties + timestamp + secreto de eventos) y actualiza el pago. Idempotente.")
    public ResponseEntity<ApiResponse<Map<String, String>>> recibir(@RequestBody Map<String, Object> evento) {
        ConfirmacionPagoService.Resultado resultado = confirmacionPagoService.procesarEvento(evento);
        return ResponseEntity.ok(ApiResponse.exito("Evento recibido", Map.of("resultado", resultado.name())));
    }
}
