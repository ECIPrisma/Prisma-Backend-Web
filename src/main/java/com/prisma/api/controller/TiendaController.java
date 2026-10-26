package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.prisma.api.enums.TipoIncidente;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tiendas")
@Tag(name = "Tenderos (App Android)", description = "Endpoints para consulta de catálogo, carrito, pedidos, 1-clic y PQRS")
public class TiendaController {

    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProveedorRepository proveedorRepository;
    private final DisputaRepository disputaRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;

    public TiendaController(TiendaRepository tiendaRepository,
                            ProductoRepository productoRepository,
                            PedidoRepository pedidoRepository,
                            ItemPedidoRepository itemPedidoRepository,
                            ProveedorRepository proveedorRepository,
                            DisputaRepository disputaRepository,
                            RutaRepository rutaRepository,
                            UsuarioRepository usuarioRepository) {
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.proveedorRepository = proveedorRepository;
        this.disputaRepository = disputaRepository;
        this.rutaRepository = rutaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private static boolean esAdmin(UserPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    /** Genera un número de pedido que no exista aún (el sufijo aleatorio de 4 dígitos colisiona rápido). */
    private String generarNumeroPedido() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 50; i++) {
            String candidato = "PED-2026-" + String.format("%04d", random.nextInt(10000));
            if (!pedidoRepository.existsByNumeroPedido(candidato)) {
                return candidato;
            }
        }
        return "PED-2026-" + System.currentTimeMillis();
    }

    @GetMapping("/mi-tienda")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Obtener perfil de la tienda del tendero autenticado")
    public ResponseEntity<ApiResponse<Tienda>> getMiTienda(@AuthenticationPrincipal UserPrincipal currentUser) {
        Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.exito("Perfil de tienda", tienda));
    }

    @GetMapping("/catalogo/{proveedorId}")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar catálogo de un proveedor específico")
    public ResponseEntity<ApiResponse<List<Producto>>> getCatalogo(@PathVariable Long proveedorId) {
        List<Producto> productos = productoRepository.findByProveedorIdAndEstado(proveedorId, "ACTIVO");
        return ResponseEntity.ok(ApiResponse.exito("Catálogo de productos", productos));
    }

    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Operation(summary = "Consultar historial de pedidos de la tienda")
    public ResponseEntity<ApiResponse<List<Pedido>>> getMisPedidos(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<Pedido> pedidos = pedidoRepository.findByTenderoIdOrderByFechaCreacionDesc(currentUser.getId());
        if (pedidos.isEmpty()) {
            Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId()).orElse(null);
            if (tienda != null) {
                pedidos = pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(tienda.getId());
            }
        }
        return ResponseEntity.ok(ApiResponse.exito("Historial de pedidos", pedidos));
    }

    @PostMapping("/pedidos")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Crear nuevo pedido desde el carrito de compras (CU-T03 y CU-T04)")
    @AuditableAction(action = "CREAR_PEDIDO_TENDERO", severity = SeveridadAudit.INFO, resourceType = "PEDIDO")
    public ResponseEntity<ApiResponse<Pedido>> crearPedido(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {

Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tienda no encontrada para el tendero autenticado"));

        Long proveedorId;
        try {
            // La app actual envía siempre proveedorId=1; se mantiene 1 como valor por defecto.
            proveedorId = payload.containsKey("proveedorId") ? Long.parseLong(payload.get("proveedorId").toString()) : 1L;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "proveedorId debe ser numérico");
        }
        Proveedor prov = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) payload.getOrDefault("items", List.of());

if (itemsData == null || itemsData.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El pedido debe incluir al menos un producto");
        }

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido(generarNumeroPedido());
        pedido.setTienda(tienda);
        pedido.setProveedor(prov);
        pedido.setEstado(EstadoPedido.CREADO);
        pedido.setFechaEstimadaEntrega(LocalDate.now());
        pedido.setMedioPagoUtilizado(MedioPago.EFECTIVO);

        // Generar PIN criptográfico de entrega de 6 dígitos
        String pin = String.format("%06d", new SecureRandom().nextInt(1000000));
        pedido.setPinEntregaHash(pin);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<ItemPedido> itemsGuardados = new ArrayList<>();

        for (Map<String, Object> it : itemsData) {
Long prodId;
            int cantidad;
            try {
                prodId = Long.parseLong(String.valueOf(it.get("productoId")));
                cantidad = Integer.parseInt(String.valueOf(it.get("cantidad")));
            } catch (NumberFormatException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "productoId y cantidad deben ser numéricos");
            }
            if (cantidad <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que cero");
            }
            Producto prod = productoRepository.findById(prodId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Producto no encontrado: " + prodId));
            if (!prod.getProveedor().getId().equals(prov.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El producto " + prodId + " no pertenece al proveedor seleccionado");
            }
            if ("INACTIVO".equals(prod.getEstado())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El producto " + prodId + " ya no está disponible");
            }
            {
                ItemPedido itemPedido = new ItemPedido();
                itemPedido.setPedido(pedido);
                itemPedido.setProducto(prod);
                itemPedido.setNombreProductoHistorico(prod.getNombre());
                itemPedido.setPresentacionHistorica(prod.getPresentacion());
                itemPedido.setCantidad(cantidad);
                itemPedido.setPrecioUnitarioHistorico(prod.getPrecioUnitario());

                BigDecimal linea = prod.getPrecioUnitario().multiply(new BigDecimal(cantidad));
                itemPedido.setSubtotalLinea(linea);
                subtotal = subtotal.add(linea);
                itemsGuardados.add(itemPedido);
            }
        }



        pedido.setSubtotal(subtotal);
        pedido.setTotalAPagar(subtotal);
        BigDecimal comision = subtotal.multiply(new BigDecimal("0.025"));
        pedido.setComisionPrismaValor(comision);

        // Asignar automáticamente a la ruta activa del día para que el conductor y proveedor lo vean de inmediato
        List<Ruta> rutasProveedor = rutaRepository.findByProveedorIdAndFechaProgramada(prov.getId(), LocalDate.now());
        if (rutasProveedor.isEmpty()) {
            rutasProveedor = rutaRepository.findByProveedorIdOrderByFechaProgramadaDesc(prov.getId());
        }
        if (!rutasProveedor.isEmpty()) {
            Ruta rutaActiva = rutasProveedor.get(0);
            pedido.setRuta(rutaActiva);
            int siguienteParada = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaActiva.getId()).size() + 1;
            pedido.setOrdenParada(siguienteParada);

            rutaActiva.setTotalParadas(rutaActiva.getTotalParadas() + 1);
            BigDecimal recaudoActual = rutaActiva.getTotalRecaudoEsperado() != null ? rutaActiva.getTotalRecaudoEsperado() : BigDecimal.ZERO;
            rutaActiva.setTotalRecaudoEsperado(recaudoActual.add(pedido.getTotalAPagar()));
            rutaRepository.save(rutaActiva);
        }

        pedido.setItems(itemsGuardados);
        Pedido guardado = pedidoRepository.save(pedido);
        for (ItemPedido ip : itemsGuardados) {
            ip.setPedido(guardado);
            itemPedidoRepository.save(ip);
        }

        return ResponseEntity.ok(ApiResponse.exito("Pedido creado exitosamente contra entrega", guardado));
    }

    @PostMapping("/pedidos/repetir-ultimo")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Repetir último pedido en 1-clic (CU-T05)")
    public ResponseEntity<ApiResponse<Pedido>> repetirUltimoPedido(
            @AuthenticationPrincipal UserPrincipal currentUser) {

Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        List<Pedido> pedidos = pedidoRepository.findByTenderoIdOrderByFechaCreacionDesc(currentUser.getId());
        if (pedidos.isEmpty()) {
            pedidos = pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(tienda.getId());
        }
        if (pedidos.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No hay pedidos previos para repetir"));
        }

        Pedido ultimo = pedidos.get(0);

        Pedido nuevo = new Pedido();
nuevo.setNumeroPedido(generarNumeroPedido());
        nuevo.setTienda(tienda);
        Proveedor prov = ultimo.getProveedor();
        nuevo.setProveedor(prov);
        nuevo.setEstado(EstadoPedido.CREADO);
        nuevo.setFechaEstimadaEntrega(LocalDate.now());
        nuevo.setSubtotal(ultimo.getSubtotal());
        nuevo.setTotalAPagar(ultimo.getTotalAPagar());
        nuevo.setComisionPrismaValor(ultimo.getComisionPrismaValor());
        nuevo.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        nuevo.setPinEntregaHash(String.format("%06d", new SecureRandom().nextInt(1000000)));

        // Copiar items del pedido original
        List<ItemPedido> itemsNuevos = new ArrayList<>();
        if (ultimo.getItems() != null) {
            for (ItemPedido ipOld : ultimo.getItems()) {
                ItemPedido ipNew = new ItemPedido();
                ipNew.setPedido(nuevo);
                ipNew.setProducto(ipOld.getProducto());
                ipNew.setNombreProductoHistorico(ipOld.getNombreProductoHistorico());
                ipNew.setPresentacionHistorica(ipOld.getPresentacionHistorica());
                ipNew.setCantidad(ipOld.getCantidad());
                ipNew.setPrecioUnitarioHistorico(ipOld.getPrecioUnitarioHistorico());
                ipNew.setSubtotalLinea(ipOld.getSubtotalLinea());
                itemsNuevos.add(ipNew);
            }
        }

        // Asignar a la ruta del día del conductor
        List<Ruta> rutasProveedor = rutaRepository.findByProveedorIdAndFechaProgramada(prov.getId(), LocalDate.now());
        if (rutasProveedor.isEmpty()) {
            rutasProveedor = rutaRepository.findByProveedorIdOrderByFechaProgramadaDesc(prov.getId());
        }
        if (!rutasProveedor.isEmpty()) {
            Ruta rutaActiva = rutasProveedor.get(0);
            nuevo.setRuta(rutaActiva);
            int siguienteParada = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaActiva.getId()).size() + 1;
            nuevo.setOrdenParada(siguienteParada);

            rutaActiva.setTotalParadas(rutaActiva.getTotalParadas() + 1);
            BigDecimal recaudoActual = rutaActiva.getTotalRecaudoEsperado() != null ? rutaActiva.getTotalRecaudoEsperado() : BigDecimal.ZERO;
            rutaActiva.setTotalRecaudoEsperado(recaudoActual.add(nuevo.getTotalAPagar()));
            rutaRepository.save(rutaActiva);
        }

        nuevo.setItems(itemsNuevos);
        Pedido guardado = pedidoRepository.save(nuevo);
        for (ItemPedido ip : itemsNuevos) {
            ip.setPedido(guardado);
            itemPedidoRepository.save(ip);
        }

        return ResponseEntity.ok(ApiResponse.exito("¡Pedido 'Lo de siempre' generado exitosamente!", guardado));
    }

    @PostMapping("/pedidos/{pedidoId}/reportar-averia")
    @PreAuthorize("hasAnyAuthority('ROLE_TENDERO', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN')")
    @Transactional
    @Operation(summary = "Reportar mercancía averiada o faltante (CU-T09)")
    public ResponseEntity<ApiResponse<Disputa>> reportarAveria(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal currentUser) {

Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!esAdmin(currentUser) && !pedido.getTienda().getTendero().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El pedido no pertenece a este tendero");
        }

        Usuario tendero = usuarioRepository.findById(currentUser.getId()).orElse(null);

        Disputa d = new Disputa();
        d.setTicketNumero("PQRS-MOBI-" + String.format("%05d", new SecureRandom().nextInt(100000)));
        d.setPedido(pedido);
        d.setProveedor(pedido.getProveedor());
        d.setReportadoPor(tendero);
        d.setTipoIncidente(TipoIncidente.MERCANCIA_DANADA_O_FALTANTE);
        d.setDescripcion(payload.getOrDefault("descripcion", "Reporte de mercancía averiada reportado desde App Móvil"));
        d.setEstado("ABIERTO");

        Disputa guardada = disputaRepository.save(d);
        return ResponseEntity.ok(ApiResponse.exito("Reclamo radicado exitosamente ante el proveedor", guardada));
    }
}
