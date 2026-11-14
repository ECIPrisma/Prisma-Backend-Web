package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.ModoProService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Modo Pro (inteligencia comercial del proveedor). HU-15: ranking de productos por zona. */
@RestController
@RequestMapping("/api/v1/proveedores/modo-pro")
@Tag(name = "Modo Pro (Portal Web React)", description = "Ranking de productos por zona y analítica comercial del proveedor")
public class ModoProController {

    private final ModoProService modoProService;

    public ModoProController(ModoProService modoProService) {
        this.modoProService = modoProService;
    }

    @GetMapping("/ranking")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Ranking de productos por zona (HU-15)",
            description = "Unidades entregadas por producto en cada zona durante los últimos N días (30 por defecto). "
                    + "Un proveedor siempre ve solo lo suyo; un admin debe indicar proveedorId.")
    public ResponseEntity<ApiResponse<ModoProService.RankingResponse>> ranking(
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) Long proveedorId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long objetivo = currentUser.getProveedorId();
        if (objetivo == null) {
            // Admin de plataforma: no pertenece a un proveedor, debe elegir cuál consultar.
            if (proveedorId == null) {
                throw new BadRequestException("Indique el proveedorId a consultar");
            }
            objetivo = proveedorId;
        }
        ModoProService.RankingResponse respuesta = modoProService.rankingPorZona(objetivo, dias);
        String mensaje = respuesta.hayDatos() ? "Ranking de productos por zona" : respuesta.mensaje();
        return ResponseEntity.ok(ApiResponse.exito(mensaje, respuesta));
    }
}
