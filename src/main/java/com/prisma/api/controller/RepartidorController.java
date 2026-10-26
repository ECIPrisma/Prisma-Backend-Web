package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.RepartidorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/repartidores")
@Tag(name = "Repartidores (App Android)", description = "Hoja de ruta, manifiesto de carga, validación con PIN y cierre de jornada")
public class RepartidorController {

    private final RepartidorService repartidorService;

    public RepartidorController(RepartidorService repartidorService) {
        this.repartidorService = repartidorService;
    }

    @GetMapping("/mis-rutas/hoy")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Obtener hoja de ruta asignada para la fecha actual (CU-R01 / CU-R03)")
    public ResponseEntity<ApiResponse<List<Ruta>>> getRutasHoy(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<Ruta> rutas = repartidorService.getRutasHoy(currentUser);
        return ResponseEntity.ok(ApiResponse.exito("Rutas del día", rutas));
    }

    @PostMapping("/rutas/{rutaId}/iniciar")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Iniciar jornada y despacho de ruta en vivo (CU-R02)")
    public ResponseEntity<ApiResponse<Ruta>> iniciarRuta(@PathVariable Long rutaId) {
        Ruta guardada = repartidorService.iniciarRuta(rutaId);
        return ResponseEntity.ok(ApiResponse.exito("Ruta iniciada en ejecución", guardada));
    }

    @GetMapping("/rutas/{rutaId}/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar paradas y pedidos secuenciales de la hoja de ruta (CU-R03)")
    public ResponseEntity<ApiResponse<List<Pedido>>> getPedidosRuta(@PathVariable Long rutaId) {
        List<Pedido> pedidos = repartidorService.getPedidosRuta(rutaId);
        return ResponseEntity.ok(ApiResponse.exito("Paradas de la ruta", pedidos));
    }

    @GetMapping("/rutas/{rutaId}/manifiesto")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar manifiesto de carga consolidado por camión (CU-R04)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getManifiestoCarga(@PathVariable Long rutaId) {
        Map<String, Object> manifiesto = repartidorService.getManifiestoCarga(rutaId);
        return ResponseEntity.ok(ApiResponse.exito("Manifiesto de carga", manifiesto));
    }

    @PostMapping("/pedidos/{pedidoId}/confirmar-entrega")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Validar entrega física con PIN de 6 dígitos generado por el tendero (CU-R05)")
    @AuditableAction(action = "ENTREGA_VALIDADA_PIN", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    public ResponseEntity<ApiResponse<Pedido>> confirmarEntrega(
            @PathVariable Long pedidoId,
            @Valid @RequestBody ConfirmarEntregaPinRequest request) {
        Pedido guardado = repartidorService.confirmarEntrega(pedidoId, request);
        return ResponseEntity.ok(ApiResponse.exito("Entrega confirmada y registrada exitosamente", guardado));
    }

    @PostMapping("/rutas/{rutaId}/finalizar")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Finalizar ruta y resumen de jornada de recaudos (CU-R07)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> finalizarRuta(@PathVariable Long rutaId) {
        Map<String, Object> resumen = repartidorService.finalizarRuta(rutaId);
        return ResponseEntity.ok(ApiResponse.exito("Ruta finalizada exitosamente", resumen));
    }
}
