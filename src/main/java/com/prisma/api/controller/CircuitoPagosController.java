package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.pagos.PasarelaPagosResiliente;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-19 – Monitoreo del circuit breaker de la pasarela de pagos (solo administradores de la plataforma).
 */
@RestController
@RequestMapping("/api/v1/admin/pagos/circuito")
@Tag(name = "Resiliencia de Pagos", description = "Estado del circuit breaker de la pasarela de pagos")
public class CircuitoPagosController {

    private final PasarelaPagosResiliente pasarelaResiliente;

    public CircuitoPagosController(PasarelaPagosResiliente pasarelaResiliente) {
        this.pasarelaResiliente = pasarelaResiliente;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Estado del circuito (CLOSED, OPEN, HALF_OPEN) y métricas de la ventana")
    public ResponseEntity<ApiResponse<Map<String, Object>>> estado() {
        return ResponseEntity.ok(ApiResponse.exito("Estado del circuit breaker de pagos", pasarelaResiliente.estado()));
    }

    @PostMapping("/reiniciar")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @AuditableAction(action = "CIRCUITO_PAGOS_REINICIADO", severity = SeveridadAudit.WARNING, resourceType = "PASARELA_PAGOS")
    @Operation(summary = "Cerrar manualmente el circuito y limpiar sus métricas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reiniciar() {
        pasarelaResiliente.reiniciar();
        return ResponseEntity.ok(ApiResponse.exito("Circuit breaker de pagos reiniciado", pasarelaResiliente.estado()));
    }
}
