package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.PerfilProveedorRequest;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PerfilProveedorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * HU-04 – Gestión de perfil comercial con aislamiento multi-tenant.
 * Las rutas /perfil resuelven el proveedor desde el JWT; las rutas /{proveedorId}/perfil
 * responden 403 si el id no corresponde al proveedor autenticado.
 */
@RestController
@RequestMapping("/api/v1/proveedores")
@Tag(name = "Proveedores (Portal Web React)", description = "Gestión de catálogo, despacho de rutas, flota y facturación quincenal B2B")
public class PerfilProveedorController {

    private static final String ROLES_PERFIL = "hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')";

    private final PerfilProveedorService perfilProveedorService;

    public PerfilProveedorController(PerfilProveedorService perfilProveedorService) {
        this.perfilProveedorService = perfilProveedorService;
    }

    @GetMapping("/perfil")
    @PreAuthorize(ROLES_PERFIL)
    @Operation(summary = "Consultar el perfil comercial de mi empresa")
    public ResponseEntity<ApiResponse<Proveedor>> getMiPerfil(@AuthenticationPrincipal UserPrincipal currentUser) {
        Proveedor perfil = perfilProveedorService.obtenerPerfil(currentUser.getProveedorId(), currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Perfil comercial", perfil));
    }

    @PutMapping("/perfil")
    @PreAuthorize(ROLES_PERFIL)
    @AuditableAction(action = "PERFIL_PROVEEDOR_ACTUALIZADO", severity = SeveridadAudit.INFO, resourceType = "PROVEEDOR")
    @Operation(summary = "Actualizar razón social, zona de cobertura y datos de contacto de mi empresa")
    public ResponseEntity<ApiResponse<Proveedor>> actualizarMiPerfil(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody PerfilProveedorRequest request) {
        Proveedor perfil = perfilProveedorService.actualizarPerfil(currentUser.getProveedorId(), request, currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Perfil actualizado correctamente", perfil));
    }

    @GetMapping("/{proveedorId}/perfil")
    @PreAuthorize(ROLES_PERFIL)
    @Operation(summary = "Consultar el perfil comercial de un proveedor por id (403 si no es el propio)")
    public ResponseEntity<ApiResponse<Proveedor>> getPerfil(
            @PathVariable Long proveedorId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Proveedor perfil = perfilProveedorService.obtenerPerfil(proveedorId, currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Perfil comercial", perfil));
    }

    @PutMapping("/{proveedorId}/perfil")
    @PreAuthorize(ROLES_PERFIL)
    @AuditableAction(action = "PERFIL_PROVEEDOR_ACTUALIZADO", severity = SeveridadAudit.INFO, resourceType = "PROVEEDOR")
    @Operation(summary = "Actualizar el perfil comercial de un proveedor por id (403 si no es el propio)")
    public ResponseEntity<ApiResponse<Proveedor>> actualizarPerfil(
            @PathVariable Long proveedorId,
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody PerfilProveedorRequest request) {
        Proveedor perfil = perfilProveedorService.actualizarPerfil(proveedorId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Perfil actualizado correctamente", perfil));
    }
}
