package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administración Prisma (Backoffice Web)", description = "Operaciones de plataforma, resolución de incidentes, feature flags y auditoría")
public class AdminController {

    private final ProveedorRepository proveedorRepository;
    private final PedidoRepository pedidoRepository;
    private final DisputaRepository disputaRepository;
    private final ConfiguracionSistemaRepository configRepository;
    private final AuditLogRepository auditLogRepository;
    private final TiendaRepository tiendaRepository;
    private final CamionRepository camionRepository;
    private final RutaRepository rutaRepository;

    public AdminController(ProveedorRepository proveedorRepository,
                           PedidoRepository pedidoRepository,
                           DisputaRepository disputaRepository,
                           ConfiguracionSistemaRepository configRepository,
                           AuditLogRepository auditLogRepository,
                           TiendaRepository tiendaRepository,
                           CamionRepository camionRepository,
                           RutaRepository rutaRepository) {
        this.proveedorRepository = proveedorRepository;
        this.pedidoRepository = pedidoRepository;
        this.disputaRepository = disputaRepository;
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.tiendaRepository = tiendaRepository;
        this.camionRepository = camionRepository;
        this.rutaRepository = rutaRepository;
    }

    @GetMapping("/proveedores")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Listar todas las empresas proveedoras registradas en Prisma")
    public ResponseEntity<ApiResponse<List<Proveedor>>> getProveedores() {
        return ResponseEntity.ok(ApiResponse.exito("Proveedores registrados", proveedorRepository.findAll()));
    }

    @PostMapping("/proveedores")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @AuditableAction(action = "CREAR_PROVEEDOR", severity = SeveridadAudit.INFO, resourceType = "PROVEEDOR")
    @Operation(summary = "Registrar nueva empresa proveedora / distribuidora en PRISMA")
    public ResponseEntity<ApiResponse<Proveedor>> crearProveedor(@RequestBody Map<String, Object> body) {
        Proveedor p = new Proveedor();
        p.setNit((String) body.getOrDefault("nit", "900.000.000-1"));
        p.setRazonSocial((String) body.getOrDefault("razonSocial", "Empresa Distribuidora S.A.S."));
        p.setNombreComercial((String) body.getOrDefault("nombreComercial", p.getRazonSocial()));
        p.setContactoNombre((String) body.getOrDefault("contactoNombre", "Contacto Principal"));
        p.setContactoEmail((String) body.getOrDefault("contactoEmail", "contacto@distribuidora.com"));
        p.setContactoTelefono((String) body.getOrDefault("contactoTelefono", "3000000000"));
        p.setDireccionSede((String) body.getOrDefault("direccionSede", "Zona Industrial"));
        p.setCiudad((String) body.getOrDefault("ciudad", "Bogotá"));
        p.setLogoUrl((String) body.getOrDefault("logoUrl", "/logo_icono_tienda.png"));
        
        Object comision = body.get("comisionPorcentaje");
        p.setComisionPorcentaje(comision != null ? new BigDecimal(comision.toString()) : new BigDecimal("2.50"));
        p.setEstado("ACTIVO");

        Proveedor guardado = proveedorRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Proveedor registrado exitosamente", guardado));
    }

    @PutMapping("/proveedores/{id}/estado")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @AuditableAction(action = "CAMBIAR_ESTADO_PROVEEDOR", severity = SeveridadAudit.WARNING, resourceType = "PROVEEDOR")
    @Operation(summary = "Activar o suspender una empresa proveedora")
    public ResponseEntity<ApiResponse<Proveedor>> cambiarEstadoProveedor(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Proveedor p = proveedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + id));

        String nuevoEstado = body.getOrDefault("estado", "ACTIVO");
        p.setEstado(nuevoEstado);
        p.setActualizadoEn(OffsetDateTime.now());

        Proveedor guardado = proveedorRepository.save(p);
        return ResponseEntity.ok(ApiResponse.exito("Estado de proveedor actualizado a " + nuevoEstado, guardado));
    }

    @PutMapping("/proveedores/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @AuditableAction(action = "ACTUALIZAR_PROVEEDOR", severity = SeveridadAudit.INFO, resourceType = "PROVEEDOR")
    @Operation(summary = "Actualizar información de una empresa proveedora")
    public ResponseEntity<ApiResponse<Proveedor>> actualizarProveedor(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Proveedor p = proveedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + id));

        if (body.containsKey("razonSocial")) p.setRazonSocial((String) body.get("razonSocial"));
        if (body.containsKey("nombreComercial")) p.setNombreComercial((String) body.get("nombreComercial"));
        if (body.containsKey("nit")) p.setNit((String) body.get("nit"));
        if (body.containsKey("contactoNombre")) p.setContactoNombre((String) body.get("contactoNombre"));
        if (body.containsKey("contactoTelefono")) p.setContactoTelefono((String) body.get("contactoTelefono"));
        if (body.containsKey("direccionSede")) p.setDireccionSede((String) body.get("direccionSede"));
        if (body.containsKey("ciudad")) p.setCiudad((String) body.get("ciudad"));
        if (body.containsKey("logoUrl")) p.setLogoUrl((String) body.get("logoUrl"));
        if (body.containsKey("comisionPorcentaje")) {
            p.setComisionPorcentaje(new BigDecimal(body.get("comisionPorcentaje").toString()));
        }
        if (body.containsKey("estado")) p.setEstado((String) body.get("estado"));
        p.setActualizadoEn(OffsetDateTime.now());

        Proveedor guardado = proveedorRepository.save(p);
        return ResponseEntity.ok(ApiResponse.exito("Proveedor actualizado exitosamente", guardado));
    }

    @DeleteMapping("/proveedores/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @AuditableAction(action = "ELIMINAR_PROVEEDOR", severity = SeveridadAudit.WARNING, resourceType = "PROVEEDOR")
    @Operation(summary = "Desactivar o suspender empresa proveedora")
    public ResponseEntity<ApiResponse<String>> eliminarProveedor(@PathVariable Long id) {
        Proveedor p = proveedorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + id));
        p.setEstado("SUSPENDIDO");
        p.setActualizadoEn(OffsetDateTime.now());
        proveedorRepository.save(p);
        return ResponseEntity.ok(ApiResponse.exito("Proveedor suspendido del sistema", "OK"));
    }

    @GetMapping("/disputas")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Operation(summary = "Consultar tickets abiertos en la mesa de incidentes y disputas")
    public ResponseEntity<ApiResponse<List<Disputa>>> getDisputas() {
        return ResponseEntity.ok(ApiResponse.exito("Tickets de disputa", disputaRepository.findAll()));
    }

    @PostMapping("/disputas/{id}/responder")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Transactional
    @Operation(summary = "Mesa de resolución: Responder o dar solución administrativa a un ticket de disputa")
    public ResponseEntity<ApiResponse<Map<String, Object>>> responderDisputaAdmin(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Disputa d = disputaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Disputa no encontrada con ID: " + id));

        String resolucion = payload != null && payload.containsKey("resolucion") ? payload.get("resolucion") : "Caso atendido por Soporte Prisma";
        String nuevoEstado = payload != null && payload.containsKey("nuevoEstado") ? payload.get("nuevoEstado") : "RESUELTO";

        d.setResolucionJustificacion(resolucion);
        d.setEstado(nuevoEstado);
        if ("RESUELTO".equalsIgnoreCase(nuevoEstado)) {
            d.setFechaResolucion(OffsetDateTime.now());
        }

        Disputa guardada = disputaRepository.save(d);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", guardada.getId());
        resp.put("ticketNumero", guardada.getTicketNumero());
        resp.put("estado", guardada.getEstado());
        resp.put("resolucion", guardada.getResolucionJustificacion());

        return ResponseEntity.ok(ApiResponse.exito("Respuesta a disputa registrada por administración Prisma", resp));
    }

    @PostMapping("/pedidos/{pedidoId}/override-pin")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Transactional
    @Operation(summary = "Mesa de resolución: Autorizar entrega de emergencia sin PIN (Soporte)")
    @AuditableAction(action = "PIN_OVERRIDE_ADMIN", severity = SeveridadAudit.CRITICAL, resourceType = "PEDIDO", requiresJustification = true)
    public ResponseEntity<ApiResponse<Pedido>> overridePin(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String justificacion = payload.get("justificacion");
        if (justificacion == null || justificacion.trim().length() < 10) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La justificación es obligatoria y debe tener al menos 10 caracteres"));
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new com.prisma.api.exception.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() == EstadoPedido.ENTREGADO || pedido.getEstado() == EstadoPedido.CANCELADO
                || pedido.getEstado() == EstadoPedido.RECHAZADO) {
            throw new com.prisma.api.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede forzar la entrega de un pedido en estado " + pedido.getEstado());
        }

        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setFechaEntregaReal(OffsetDateTime.now());
        String adminNombre = currentUser != null ? currentUser.getUsername() : "ADMINISTRADOR_SISTEMA";
        pedido.setNotasEntrega("ENTREGA AUTORIZADA POR SOPORTE (" + adminNombre + "): " + justificacion);

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Entrega forzada autorizada exitosamente", guardado));
    }

    @GetMapping("/configuraciones")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar parámetros operativos y feature flags en caliente")
    public ResponseEntity<ApiResponse<List<ConfiguracionSistema>>> getConfiguraciones() {
        return ResponseEntity.ok(ApiResponse.exito("Configuración del sistema", configRepository.findAll()));
    }

    @PutMapping("/configuraciones/{clave}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Modificar un parámetro operativo en memoria sin reiniciar servidores")
    @AuditableAction(action = "PARAMETRO_ACTUALIZADO", severity = SeveridadAudit.CRITICAL, resourceType = "CONFIGURACION", requiresJustification = true)
    public ResponseEntity<ApiResponse<ConfiguracionSistema>> updateConfiguracion(
            @PathVariable String clave,
            @RequestBody Map<String, String> payload) {

        ConfiguracionSistema config = configRepository.findById(clave)
                .orElseThrow(() -> new RuntimeException("Parámetro no encontrado: " + clave));

        config.setValor(payload.get("valor"));
        config.setActualizadoEn(OffsetDateTime.now());

        ConfiguracionSistema guardado = configRepository.save(config);
        return ResponseEntity.ok(ApiResponse.exito("Parámetro actualizado en caliente", guardado));
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar el registro inmutable de auditoría forense")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditoria(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        Page<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(pagina, tamano));
        return ResponseEntity.ok(ApiResponse.exito("Rastro de auditoría forense", logs));
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Operation(summary = "Consultar todos los pedidos de la red para monitoreo y soporte")
    public ResponseEntity<ApiResponse<List<Pedido>>> getPedidos() {
        return ResponseEntity.ok(ApiResponse.exito("Todos los pedidos de la red", pedidoRepository.findAll()));
    }

    @PostMapping("/pedidos/{pedidoId}/soporte-cancelar")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Transactional
    @AuditableAction(action = "CANCELAR_PEDIDO_SOPORTE", severity = SeveridadAudit.WARNING, resourceType = "PEDIDO", requiresJustification = true)
    @Operation(summary = "Intervención de soporte: Cancelar pedido fallido o con problemas insalvables")
    public ResponseEntity<ApiResponse<Pedido>> soporteCancelarPedido(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String motivo = body.getOrDefault("motivo", "CANCELADO POR INTERVENCIÓN DE SOPORTE PRISMA");
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        pedido.setEstado(EstadoPedido.CANCELADO);
        String usuarioSoporte = currentUser != null ? currentUser.getUsername() : "ADMIN_SOPORTE";
        pedido.setNotasEntrega("CANCELADO POR SOPORTE (" + usuarioSoporte + "): " + motivo);
        pedido.setActualizadoEn(OffsetDateTime.now());

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Pedido cancelado por soporte exitosamente", guardado));
    }

    @PostMapping("/pedidos/{pedidoId}/reprogramar")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Transactional
    @AuditableAction(action = "REPROGRAMAR_PEDIDO_SOPORTE", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    @Operation(summary = "Reprogramar fecha de entrega de pedido que no pudo entregarse hoy")
    public ResponseEntity<ApiResponse<Pedido>> reprogramarPedido(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> body) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        String nuevaFechaStr = body.get("nuevaFecha");
        if (nuevaFechaStr != null) {
            pedido.setFechaEstimadaEntrega(java.time.LocalDate.parse(nuevaFechaStr));
        } else {
            pedido.setFechaEstimadaEntrega(java.time.LocalDate.now().plusDays(1));
        }

        String motivo = body.getOrDefault("motivo", "Reprogramado para el siguiente ciclo de ruta");
        pedido.setNotasEntrega("REPROGRAMADO: " + motivo);
        pedido.setEstado(EstadoPedido.ACEPTADO);
        pedido.setActualizadoEn(OffsetDateTime.now());

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Pedido reprogramado exitosamente", guardado));
    }

    @GetMapping("/cartera-empresas")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_CONTABILIDAD')")
    @Operation(summary = "Consultar cartera y comisiones que cada empresa le va debiendo a Prisma")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCarteraEmpresas() {
        List<Proveedor> proveedores = proveedorRepository.findAll();
        List<Pedido> todosPedidos = pedidoRepository.findAll();
        List<Camion> todosCamiones = camionRepository.findAll();
        List<Ruta> todasRutas = rutaRepository.findAll();

        List<Map<String, Object>> res = proveedores.stream().map(prov -> {
            List<Pedido> pedidosProv = todosPedidos.stream()
                    .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(prov.getId()))
                    .toList();

            BigDecimal ventasTotales = pedidosProv.stream()
                    .filter(p -> p.getEstado() == EstadoPedido.ENTREGADO)
                    .map(Pedido::getTotalAPagar)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (ventasTotales.compareTo(BigDecimal.ZERO) == 0) {
                ventasTotales = pedidosProv.stream()
                        .map(Pedido::getTotalAPagar)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            BigDecimal porcentaje = prov.getComisionPorcentaje() != null ? prov.getComisionPorcentaje() : new BigDecimal("2.50");
            BigDecimal deudaPrisma = ventasTotales.multiply(porcentaje).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);

            long totalCamiones = todosCamiones.stream()
                    .filter(c -> c.getProveedor() != null && c.getProveedor().getId().equals(prov.getId()))
                    .count();

            long rutasVaradas = todasRutas.stream()
                    .filter(r -> r.getProveedor() != null && r.getProveedor().getId().equals(prov.getId()))
                    .filter(r -> "VARADO_EN_PERCANCE".equalsIgnoreCase(r.getEstado()))
                    .count();

            long totalTiendas = pedidosProv.stream()
                    .map(p -> p.getTienda() != null ? p.getTienda().getId() : null)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            if (totalTiendas == 0) {
                totalTiendas = tiendaRepository.count();
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("proveedorId", prov.getId());
            dto.put("nit", prov.getNit());
            dto.put("nombreComercial", prov.getNombreComercial());
            dto.put("razonSocial", prov.getRazonSocial());
            dto.put("contactoNombre", prov.getContactoNombre());
            dto.put("contactoTelefono", prov.getContactoTelefono());
            dto.put("contactoEmail", prov.getContactoEmail());
            dto.put("estado", prov.getEstado());
            dto.put("totalTiendas", totalTiendas);
            dto.put("totalCamiones", totalCamiones);
            dto.put("ventasTotales", ventasTotales);
            dto.put("porcentajeComision", porcentaje);
            dto.put("deudaComisionPrisma", deudaPrisma);
            dto.put("rutasVaradas", rutasVaradas);

            return dto;
        }).toList();

        return ResponseEntity.ok(ApiResponse.exito("Cartera de comisiones por empresa", res));
    }

    @GetMapping("/proveedores/{id}/tiendas")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Operation(summary = "Listar las tiendas atendidas por una empresa proveedora específica")
    public ResponseEntity<ApiResponse<List<Tienda>>> getTiendasProveedor(@PathVariable Long id) {
        List<Tienda> todas = tiendaRepository.findAll();
        return ResponseEntity.ok(ApiResponse.exito("Tiendas de la empresa", todas));
    }

    @PostMapping("/rutas/{rutaId}/auxilio-soporte")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN', 'ROLE_SOPORTE')")
    @Transactional
    @AuditableAction(action = "AUXILIO_RUTA_PRISMA", severity = SeveridadAudit.WARNING, resourceType = "RUTA")
    @Operation(summary = "Intervención de emergencia de Prisma para auxiliar ruta varada o reanudar despacho")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auxilioRutaSoporte(
            @PathVariable Long rutaId,
            @RequestBody Map<String, String> body) {
        Ruta r = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new RuntimeException("Ruta no encontrada con ID: " + rutaId));

        String accion = body.getOrDefault("accion", "ASISTENCIA_ENVIADA");
        String notas = body.getOrDefault("notas", "Equipo de auxilio logístico Prisma activado en zona");

        if ("REANUDAR".equalsIgnoreCase(accion)) {
            r.setEstado("EN_RUTA");
            if (r.getCamion() != null) {
                r.getCamion().setEstado("EN_RUTA");
                camionRepository.save(r.getCamion());
            }
        } else {
            r.setEstado("AUXILIO_PRISMA_EN_CAMINO");
        }

        Ruta guardada = rutaRepository.save(r);

        Map<String, Object> resp = new HashMap<>();
        resp.put("rutaId", guardada.getId());
        resp.put("codigoRuta", guardada.getCodigoRuta());
        resp.put("estado", guardada.getEstado());
        resp.put("notas", notas);

        return ResponseEntity.ok(ApiResponse.exito("Auxilio administrativo de Prisma registrado", resp));
    }
}
