package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.VinculacionCallbackRequest;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.CuentaCobroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HU-12 – Vinculación de la cuenta de cobro del proveedor con Mercado Pago por OAuth.
 */
@RestController
@RequestMapping("/api/v1/proveedores/pagos")
@Tag(name = "Pagos del Proveedor", description = "Vinculación de la cuenta de cobro (OAuth Mercado Pago) para recibir pagos divididos")
public class PagosProveedorController {

    private static final String ROLES_PROVEEDOR = "hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR')";

    private final CuentaCobroService cuentaCobroService;

    public PagosProveedorController(CuentaCobroService cuentaCobroService) {
        this.cuentaCobroService = cuentaCobroService;
    }

    @GetMapping("/cuenta")
    @PreAuthorize(ROLES_PROVEEDOR)
    @Operation(summary = "Estado de mi cuenta de cobro", description = "Indica si está vinculada y el porcentaje que recibe el proveedor por pedido")
    public ResponseEntity<ApiResponse<CuentaCobroResponse>> estado(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Estado de la cuenta de cobro", cuentaCobroService.obtenerEstado(currentUser)));
    }

    @PostMapping("/vinculacion")
    @PreAuthorize(ROLES_PROVEEDOR)
    @Operation(summary = "Iniciar la vinculación con Mercado Pago", description = "Devuelve la URL de autorización OAuth a la que se debe redirigir al proveedor")
    public ResponseEntity<ApiResponse<Map<String, String>>> iniciar(@AuthenticationPrincipal UserPrincipal currentUser) {
        String url = cuentaCobroService.iniciarVinculacion(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Redirija al proveedor a Mercado Pago", Map.of("urlAutorizacion", url)));
    }

    @PostMapping("/vinculacion/callback")
    @PreAuthorize(ROLES_PROVEEDOR)
    @AuditableAction(action = "CUENTA_COBRO_VINCULADA", severity = SeveridadAudit.SECURITY, resourceType = "PROVEEDOR")
    @Operation(summary = "Completar la vinculación", description = "Recibe el code y el state del redirect de Mercado Pago, guarda el token cifrado y habilita los pagos divididos")
    public ResponseEntity<ApiResponse<CuentaCobroResponse>> completar(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody VinculacionCallbackRequest callback) {
        CuentaCobroResponse estado = cuentaCobroService.completarVinculacion(currentUser, callback);
        return ResponseEntity.ok(ApiResponse.exito("Cuenta de cobro vinculada correctamente", estado));
    }

    @DeleteMapping("/cuenta")
    @PreAuthorize(ROLES_PROVEEDOR)
    @AuditableAction(action = "CUENTA_COBRO_DESVINCULADA", severity = SeveridadAudit.SECURITY, resourceType = "PROVEEDOR")
    @Operation(summary = "Desvincular mi cuenta de cobro")
    public ResponseEntity<ApiResponse<Void>> desvincular(@AuthenticationPrincipal UserPrincipal currentUser) {
        cuentaCobroService.desvincular(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Cuenta de cobro desvinculada", null));
    }
}
