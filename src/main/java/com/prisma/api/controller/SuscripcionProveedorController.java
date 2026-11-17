package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoSuscripcionResponse;
import com.prisma.api.dto.SuscripcionResponse;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.SuscripcionProveedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * MVP – Suscripción mensual Prisma Pro que el distribuidor (proveedor) paga a PRISMA por la pasarela SIMULADA.
 */
@RestController
@RequestMapping("/api/v1/proveedores/suscripcion")
@Tag(name = "Suscripción Prisma Pro", description = "Plan mensual del distribuidor y su pago en línea (pasarela simulada)")
public class SuscripcionProveedorController {

    private static final String ROLES_PROVEEDOR = "hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR')";

    private final SuscripcionProveedorService suscripcionService;

    public SuscripcionProveedorController(SuscripcionProveedorService suscripcionService) {
        this.suscripcionService = suscripcionService;
    }

    @GetMapping
    @PreAuthorize(ROLES_PROVEEDOR)
    @Operation(summary = "Estado de mi suscripción Prisma Pro e historial de pagos")
    public ResponseEntity<ApiResponse<SuscripcionResponse>> estado(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Estado de la suscripción", suscripcionService.obtenerEstado(currentUser)));
    }

    @PostMapping("/pago")
    @PreAuthorize(ROLES_PROVEEDOR)
    @AuditableAction(action = "PAGO_SUSCRIPCION_PRISMA_PRO", severity = SeveridadAudit.INFO, resourceType = "SUSCRIPCION")
    @Operation(summary = "Pagar un mes de suscripción (simulado)",
            description = "Nequi 3991111111 aprueba / 3992222222 rechaza / 3993333333 simula la pasarela caída; "
                    + "PSE 'Banco que rechaza' rechaza; tarjeta 4242424242424242 aprueba / 4111111111111111 rechaza.")
    public ResponseEntity<ApiResponse<PagoSuscripcionResponse>> pagar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody PagarPedidoRequest datos) {
        PagoSuscripcionResponse pago = suscripcionService.pagarMes(currentUser, datos);
        String mensaje = pago.aprobado()
                ? SuscripcionProveedorService.MENSAJE_PAGO_APROBADO
                : SuscripcionProveedorService.MENSAJE_PAGO_RECHAZADO;
        return ResponseEntity.ok(ApiResponse.exito(mensaje, pago));
    }
}
