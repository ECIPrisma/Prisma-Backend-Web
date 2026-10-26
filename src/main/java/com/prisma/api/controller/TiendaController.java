package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.entity.Disputa;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Tienda;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.TiendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tiendas")
@Tag(name = "Tenderos (App Android)", description = "Endpoints para consulta de catálogo, carrito, pedidos, 1-clic y PQRS")
public class TiendaController {

    private final TiendaService tiendaService;

    public TiendaController(TiendaService tiendaService) {
        this.tiendaService = tiendaService;
    }

    @GetMapping("/mi-tienda")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Obtener perfil de la tienda del tendero autenticado")
    public ResponseEntity<ApiResponse<Tienda>> getMiTienda(@AuthenticationPrincipal UserPrincipal currentUser) {
        Tienda tienda = tiendaService.getMiTienda(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Perfil de tienda", tienda));
    }

    @GetMapping("/catalogo/{proveedorId}")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar catálogo de un proveedor específico")
    public ResponseEntity<ApiResponse<List<Producto>>> getCatalogo(@PathVariable Long proveedorId) {
        List<Producto> productos = tiendaService.getCatalogo(proveedorId);
        return ResponseEntity.ok(ApiResponse.exito("Catálogo de productos", productos));
    }

    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar historial de pedidos de la tienda")
    public ResponseEntity<ApiResponse<List<Pedido>>> getMisPedidos(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<Pedido> pedidos = tiendaService.getMisPedidos(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Historial de pedidos", pedidos));
    }

    @PostMapping("/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Crear nuevo pedido desde el carrito de compras (CU-T03 y CU-T04)")
    @AuditableAction(action = "CREAR_PEDIDO_TENDERO", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    public ResponseEntity<ApiResponse<Pedido>> crearPedido(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pedido guardado = tiendaService.crearPedido(payload, currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Pedido creado exitosamente contra entrega", guardado));
    }

    @PostMapping("/pedidos/repetir-ultimo")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Repetir último pedido en 1-clic (CU-T05)")
    public ResponseEntity<ApiResponse<Pedido>> repetirUltimoPedido(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pedido guardado = tiendaService.repetirUltimoPedido(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("¡Pedido 'Lo de siempre' generado exitosamente!", guardado));
    }

    @PostMapping("/pedidos/{pedidoId}/reportar-averia")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Reportar mercancía averiada o faltante (CU-T09)")
    public ResponseEntity<ApiResponse<Disputa>> reportarAveria(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Disputa guardada = tiendaService.reportarAveria(pedidoId, payload, currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Reclamo radicado exitosamente ante el proveedor", guardada));
    }
}
