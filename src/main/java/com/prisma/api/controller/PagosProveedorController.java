package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.RecaudosProveedorResponse;
import com.prisma.api.dto.VincularCuentaCobroRequest;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.CuentaCobroService;
import com.prisma.api.service.PagoPedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * HU-12 – Cuenta de cobro del proveedor y pagos recibidos. La pasarela (Wompi sandbox) está SIMULADA.
 */
@RestController
@RequestMapping("/api/v1/proveedores/pagos")
@Tag(name = "Pagos del Proveedor", description = "Cuenta de cobro y pagos en línea recibidos (pasarela simulada)")
public class PagosProveedorController {

    private static final String ROLES_PROVEEDOR = "hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR')";

    private final CuentaCobroService cuentaCobroService;
    private final PagoPedidoService pagoPedidoService;

    public PagosProveedorController(CuentaCobroService cuentaCobroService, PagoPedidoService pagoPedidoService) {
        this.cuentaCobroService = cuentaCobroService;
        this.pagoPedidoService = pagoPedidoService;
    }

    @GetMapping("/cuenta")
    @PreAuthorize(ROLES_PROVEEDOR)
    @Operation(summary = "Estado de mi cuenta de cobro", description = "Indica si está vinculada y el porcentaje que recibe el proveedor por pedido")
    public ResponseEntity<ApiResponse<CuentaCobroResponse>> estado(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Estado de la cuenta de cobro", cuentaCobroService.obtenerEstado(currentUser)));
    }

    @PostMapping("/cuenta")
    @PreAuthorize(ROLES_PROVEEDOR)
    @AuditableAction(action = "CUENTA_COBRO_VINCULADA", severity = SeveridadAudit.SECURITY, resourceType = "PROVEEDOR")
    @Operation(summary = "Vincular mi cuenta de cobro (simulado)",
            description = "Simula la verificación de la cuenta bancaria en la pasarela. Una cuenta terminada en 0000 simula un rechazo.")
    public ResponseEntity<ApiResponse<CuentaCobroResponse>> vincular(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody VincularCuentaCobroRequest datos) {
        return ResponseEntity.ok(ApiResponse.exito("Cuenta de cobro vinculada correctamente",
                cuentaCobroService.vincular(currentUser, datos)));
    }

    @DeleteMapping("/cuenta")
    @PreAuthorize(ROLES_PROVEEDOR)
    @AuditableAction(action = "CUENTA_COBRO_DESVINCULADA", severity = SeveridadAudit.SECURITY, resourceType = "PROVEEDOR")
    @Operation(summary = "Desvincular mi cuenta de cobro")
    public ResponseEntity<ApiResponse<Void>> desvincular(@AuthenticationPrincipal UserPrincipal currentUser) {
        cuentaCobroService.desvincular(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Cuenta de cobro desvinculada", null));
    }

    @GetMapping("/recaudos")
    @PreAuthorize(ROLES_PROVEEDOR)
    @Operation(summary = "Pagos en línea de mis pedidos", description = "Pagos retenidos en garantía y pagos liberados tras la entrega")
    public ResponseEntity<ApiResponse<RecaudosProveedorResponse>> recaudos(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Pagos en línea recibidos", pagoPedidoService.recaudosProveedor(currentUser)));
    }
}
