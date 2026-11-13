package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoPedidoResponse;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PagoPedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * N-08 – Checkout en línea del tendero con la pasarela SIMULADA (sandbox tipo Wompi, sin cobros reales).
 */
@RestController
@RequestMapping("/api/v1/tiendas/pedidos/{pedidoId}/pago")
@Tag(name = "Pagos del Tendero", description = "Pago en línea de pedidos con pasarela simulada (Nequi, PSE, tarjeta)")
public class PagosTenderoController {

    public static final String MENSAJE_PAGO_APROBADO =
            "Pago aprobado. El dinero queda en garantía hasta que confirmes la entrega con tu PIN.";
    public static final String MENSAJE_PAGO_RECHAZADO = "El pago no pudo ser procesado, intenta con otro medio";

    private final PagoPedidoService pagoPedidoService;

    public PagosTenderoController(PagoPedidoService pagoPedidoService) {
        this.pagoPedidoService = pagoPedidoService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_TENDERO')")
    @AuditableAction(action = "PAGO_PEDIDO_EN_LINEA", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    @Operation(summary = "Pagar un pedido en línea (simulado)",
            description = "Nequi 3991111111 aprueba / 3992222222 rechaza; PSE 'Banco que rechaza' rechaza; "
                    + "tarjeta 4242424242424242 aprueba / 4111111111111111 rechaza.")
    public ResponseEntity<ApiResponse<PagoPedidoResponse>> pagar(
            @PathVariable Long pedidoId,
            @RequestBody PagarPedidoRequest datos,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PagoPedidoResponse pago = pagoPedidoService.pagar(pedidoId, datos, currentUser);
        // HU-13 criterio 2: el pedido sigue "Confirmado" sin garantía y se muestra el mensaje exacto
        String mensaje = pago.aprobado() ? MENSAJE_PAGO_APROBADO : MENSAJE_PAGO_RECHAZADO;
        return ResponseEntity.ok(ApiResponse.exito(mensaje, pago));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_TENDERO')")
    @Operation(summary = "Consultar el último pago en línea del pedido")
    public ResponseEntity<ApiResponse<PagoPedidoResponse>> consultar(
            @PathVariable Long pedidoId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(ApiResponse.exito("Estado del pago", pagoPedidoService.obtenerPago(pedidoId, currentUser)));
    }
}
