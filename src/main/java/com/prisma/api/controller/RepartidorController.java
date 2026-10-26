package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.RutaRepository;
import com.prisma.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/repartidores")
@Tag(name = "Repartidores (App Android)", description = "Hoja de ruta, manifiesto de carga, validación con PIN y cierre de jornada")
public class RepartidorController {

    private final RutaRepository rutaRepository;
    private final PedidoRepository pedidoRepository;

    public RepartidorController(RutaRepository rutaRepository, PedidoRepository pedidoRepository) {
        this.rutaRepository = rutaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    private static boolean esAdmin(UserPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    /** Un repartidor/proveedor solo puede operar recursos de su propio proveedor (multi-tenant). */
    private static void verificarProveedor(Long proveedorDelRecurso, UserPrincipal u) {
        if (esAdmin(u)) return;
        if (u.getProveedorId() == null || !u.getProveedorId().equals(proveedorDelRecurso)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El recurso pertenece a otro proveedor");
        }
    }

    private Ruta rutaDelProveedor(Long rutaId, UserPrincipal u) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ruta no encontrada con ID: " + rutaId));
        verificarProveedor(ruta.getProveedor().getId(), u);
        return ruta;
    }

    @GetMapping("/mis-rutas/hoy")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Obtener hoja de ruta asignada para la fecha actual (CU-R01 / CU-R03)")
    public ResponseEntity<ApiResponse<List<Ruta>>> getRutasHoy(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<Ruta> rutas = rutaRepository.findByRepartidorIdAndFechaProgramada(currentUser.getId(), LocalDate.now());
        if (rutas.isEmpty()) {
            rutas = rutaRepository.findByRepartidorIdOrderByFechaProgramadaDesc(currentUser.getId());
        }
        return ResponseEntity.ok(ApiResponse.exito("Rutas del día", rutas));
    }

    @PostMapping("/rutas/{rutaId}/iniciar")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Iniciar jornada y despacho de ruta en vivo (CU-R02)")
public ResponseEntity<ApiResponse<Ruta>> iniciarRuta(@PathVariable Long rutaId,
                                                         @AuthenticationPrincipal UserPrincipal currentUser) {
        Ruta ruta = rutaDelProveedor(rutaId, currentUser);
        if ("FINALIZADA".equals(ruta.getEstado())) {
            throw new ApiException(HttpStatus.CONFLICT, "La ruta ya fue finalizada y no puede iniciarse de nuevo");
        }

        ruta.setEstado("EN_EJECUCION");
        ruta.setHoraInicioReal(OffsetDateTime.now());
        Ruta guardada = rutaRepository.save(ruta);

        // Actualizar todos los pedidos de la ruta a EN_RUTA
        List<Pedido> pedidos = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
        for (Pedido p : pedidos) {
            if (p.getEstado() == EstadoPedido.CREADO || p.getEstado() == EstadoPedido.CARGADO_EN_BODEGA || p.getEstado() == EstadoPedido.ACEPTADO) {
                p.setEstado(EstadoPedido.EN_RUTA);
                pedidoRepository.save(p);
            }
        }

        return ResponseEntity.ok(ApiResponse.exito("Ruta iniciada en ejecución", guardada));
    }

    @GetMapping("/rutas/{rutaId}/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar paradas y pedidos secuenciales de la hoja de ruta (CU-R03)")
public ResponseEntity<ApiResponse<List<Pedido>>> getPedidosRuta(@PathVariable Long rutaId,
                                                                    @AuthenticationPrincipal UserPrincipal currentUser) {
        rutaDelProveedor(rutaId, currentUser);
        List<Pedido> pedidos = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
        return ResponseEntity.ok(ApiResponse.exito("Paradas de la ruta", pedidos));
    }

    @GetMapping("/rutas/{rutaId}/manifiesto")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar manifiesto de carga consolidado por camión (CU-R04)")
public ResponseEntity<ApiResponse<Map<String, Object>>> getManifiestoCarga(@PathVariable Long rutaId,
                                                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        Ruta ruta = rutaDelProveedor(rutaId, currentUser);

        List<Pedido> pedidos = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
        Map<String, Integer> consolidadoProductos = new HashMap<>();
        BigDecimal totalDineroEsperado = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            totalDineroEsperado = totalDineroEsperado.add(p.getTotalAPagar() != null ? p.getTotalAPagar() : BigDecimal.ZERO);
            if (p.getItems() != null) {
                for (var item : p.getItems()) {
                    String nombre = item.getNombreProductoHistorico();
                    consolidadoProductos.put(nombre, consolidadoProductos.getOrDefault(nombre, 0) + item.getCantidad());
                }
            }
        }

        Map<String, Object> manifiesto = new HashMap<>();
        manifiesto.put("codigoRuta", ruta.getCodigoRuta());
        manifiesto.put("totalParadas", pedidos.size());
        manifiesto.put("totalRecaudoEsperado", totalDineroEsperado);
        manifiesto.put("itemsCargados", consolidadoProductos);

        return ResponseEntity.ok(ApiResponse.exito("Manifiesto de carga", manifiesto));
    }

    @PostMapping("/pedidos/{pedidoId}/confirmar-entrega")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Validar entrega física con PIN de 6 dígitos generado por el tendero (CU-R05)")
    @AuditableAction(action = "ENTREGA_VALIDADA_PIN", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    public ResponseEntity<ApiResponse<Pedido>> confirmarEntrega(
            @PathVariable Long pedidoId,
            @Valid @RequestBody ConfirmarEntregaPinRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado con ID: " + pedidoId));

        verificarProveedor(pedido.getProveedor().getId(), currentUser);

        if (pedido.getEstado() != EstadoPedido.EN_RUTA) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se puede confirmar la entrega de un pedido EN_RUTA (estado actual: " + pedido.getEstado() + ")");
        }

        if (!java.security.MessageDigest.isEqual(
                pedido.getPinEntregaHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                request.getPin().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("El PIN ingresado es incorrecto. Verifique con el tendero."));
        }

        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setPinValidadoEn(OffsetDateTime.now());
        pedido.setFechaEntregaReal(OffsetDateTime.now());
        pedido.setMedioPagoUtilizado(request.getMedioPago());
        if (request.getNotasEntrega() != null) {
            pedido.setNotasEntrega(request.getNotasEntrega());
        }

        Pedido guardado = pedidoRepository.save(pedido);

        // Actualizar recaudo acumulado en la ruta
        if (pedido.getRuta() != null) {
            Ruta r = pedido.getRuta();
            BigDecimal actual = r.getTotalRecaudoEfectivo() != null ? r.getTotalRecaudoEfectivo() : BigDecimal.ZERO;
            r.setTotalRecaudoEfectivo(actual.add(guardado.getTotalAPagar()));
            rutaRepository.save(r);
        }

        return ResponseEntity.ok(ApiResponse.exito("Entrega confirmada y registrada exitosamente", guardado));
    }

    @PostMapping("/rutas/{rutaId}/finalizar")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Finalizar ruta y resumen de jornada de recaudos (CU-R07)")
public ResponseEntity<ApiResponse<Map<String, Object>>> finalizarRuta(@PathVariable Long rutaId,
                                                                          @AuthenticationPrincipal UserPrincipal currentUser) {
        Ruta ruta = rutaDelProveedor(rutaId, currentUser);

        ruta.setEstado("FINALIZADA");
        ruta.setHoraFinReal(OffsetDateTime.now());
        rutaRepository.save(ruta);

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("codigoRuta", ruta.getCodigoRuta());
        resumen.put("estado", "FINALIZADA");
        resumen.put("horaFin", ruta.getHoraFinReal());
        resumen.put("totalRecaudadoEfectivo", ruta.getTotalRecaudoEfectivo());
        resumen.put("mensaje", "Jornada completada exitosamente. Fondos listos para liquidación.");

        return ResponseEntity.ok(ApiResponse.exito("Ruta finalizada exitosamente", resumen));
    }
}
