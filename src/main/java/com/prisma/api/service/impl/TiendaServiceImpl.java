package com.prisma.api.service.impl;

import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.enums.TipoIncidente;
import com.prisma.api.exception.ApiException;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PushNotificationService;
import com.prisma.api.service.TiendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TiendaServiceImpl implements TiendaService {

    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ProveedorRepository proveedorRepository;
    private final DisputaRepository disputaRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    /** Comisión vigente (N-02): se lee de configuracion_sistema.comision_porcentaje; 2.5 % si no existe. */
    @Autowired(required = false)
    private ConfiguracionSistemaRepository configuracionRepository;

    private static final BigDecimal COMISION_POR_DEFECTO_PCT = new BigDecimal("2.5");

    BigDecimal porcentajeComision() {
        if (configuracionRepository == null) {
            return COMISION_POR_DEFECTO_PCT;
        }
        try {
            return configuracionRepository.findByClave("comision_porcentaje")
                    .map(c -> new BigDecimal(c.getValor().trim()))
                    .filter(v -> v.signum() >= 0 && v.compareTo(new BigDecimal("100")) <= 0)
                    .orElse(COMISION_POR_DEFECTO_PCT);
        } catch (RuntimeException e) {
            return COMISION_POR_DEFECTO_PCT;
        }
    }

    /** Número de pedido que no exista todavía (el sufijo de 4 dígitos colisiona rápido). */
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

    private static boolean esAdmin(UserPrincipal u) {
        return u != null && u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    public TiendaServiceImpl(TiendaRepository tiendaRepository,
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

    @Override
    @Transactional(readOnly = true)
    public Tienda getMiTienda(UserPrincipal currentUser) {
        return tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getCatalogo(Long proveedorId) {
        return productoRepository.findByProveedorIdAndEstado(proveedorId, "ACTIVO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getCatalogoExplorar(UserPrincipal currentUser, String zona, Long categoriaId, Long proveedorId) {
        String zonaFiltro = zona;
        if ((zonaFiltro == null || zonaFiltro.isBlank()) && currentUser != null) {
            Tienda miTienda = tiendaRepository.findFirstByTenderoId(currentUser.getId()).orElse(null);
            if (miTienda != null && miTienda.getBarrio() != null && !miTienda.getBarrio().isBlank()) {
                zonaFiltro = miTienda.getBarrio();
            }
        }

        List<Producto> base = proveedorId != null
                ? productoRepository.findByProveedorIdAndEstado(proveedorId, "ACTIVO")
                : productoRepository.findAll().stream().filter(p -> "ACTIVO".equalsIgnoreCase(p.getEstado())).toList();

        final String zLower = zonaFiltro != null ? zonaFiltro.trim().toLowerCase() : null;

        return base.stream()
                .filter(p -> {
                    if (zLower == null || zLower.isEmpty()) {
                        return true;
                    }
                    if (p.getProveedor() == null || p.getProveedor().getZonaCobertura() == null || p.getProveedor().getZonaCobertura().isBlank()) {
                        return true;
                    }
                    String cob = p.getProveedor().getZonaCobertura().toLowerCase();
                    return cob.contains(zLower) || cob.contains("todas") || cob.contains("bogot");
                })
                .filter(p -> categoriaId == null || (p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> getMisPedidos(UserPrincipal currentUser) {
        List<Pedido> pedidos = pedidoRepository.findByTenderoIdOrderByFechaCreacionDesc(currentUser.getId());
        if (pedidos.isEmpty()) {
            Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId()).orElse(null);
            if (tienda != null) {
                pedidos = pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(tienda.getId());
            }
        }
        return pedidos;
    }

    @Override
    public Pedido crearPedido(Map<String, Object> payload, UserPrincipal currentUser) {
        Object idemObj = payload.get("idempotencyKey");
        String idempotencyKey = idemObj != null ? idemObj.toString().trim() : null;
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            var existente = pedidoRepository.findByIdempotencyKey(idempotencyKey);
            if (existente.isPresent()) {
                Pedido previo = existente.get();
                Usuario duenio = previo.getTienda() != null ? previo.getTienda().getTendero() : null;
                if (duenio != null && !duenio.getId().equals(currentUser.getId()) && !esAdmin(currentUser)) {
                    throw new ApiException(HttpStatus.CONFLICT, "La clave de idempotencia ya fue usada por otra cuenta");
                }
                return previo;
            }
        }

        Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada para el tendero autenticado"));

        Long proveedorId;
        try {
            // La app actual envía siempre proveedorId; si falta se mantiene 1 por compatibilidad.
            proveedorId = payload.containsKey("proveedorId") ? Long.parseLong(payload.get("proveedorId").toString()) : 1L;
        } catch (NumberFormatException e) {
            throw new BadRequestException("proveedorId debe ser numérico");
        }
        Proveedor prov = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + proveedorId));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) payload.getOrDefault("items", List.of());
        if (itemsData == null || itemsData.isEmpty()) {
            throw new BadRequestException("El pedido debe incluir al menos un producto");
        }

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido(generarNumeroPedido());
        pedido.setTienda(tienda);
        pedido.setProveedor(prov);
        pedido.setEstado(EstadoPedido.CREADO);
        pedido.setFechaEstimadaEntrega(LocalDate.now());
        pedido.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            pedido.setIdempotencyKey(idempotencyKey);
        }

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
                throw new BadRequestException("productoId y cantidad deben ser numéricos");
            }
            if (cantidad <= 0) {
                throw new BadRequestException("La cantidad debe ser mayor que cero");
            }
            Producto prod = productoRepository.findById(prodId)
                    .orElseThrow(() -> new BadRequestException("Producto no encontrado: " + prodId));
            {
                if (prod.getStockDisponible() <= 0) {
                    throw new BadRequestException("El producto " + prod.getNombre() + " se encuentra Agotado");
                }
                if (prod.getProveedor() != null && !prov.getId().equals(prod.getProveedor().getId())) {
                    throw new BadRequestException("El producto " + prodId + " no pertenece al proveedor seleccionado");
                }
                if ("INACTIVO".equals(prod.getEstado())) {
                    throw new BadRequestException("El producto " + prodId + " ya no está disponible");
                }
                if (cantidad > prod.getStockDisponible()) {
                    throw new BadRequestException("Stock insuficiente de " + prod.getNombre() + ": quedan " + prod.getStockDisponible());
                }
                if (cantidad < prod.getCantidadMinimaPedido()) {
                    throw new BadRequestException("La cantidad mínima para este producto es " + prod.getCantidadMinimaPedido() + " unidades");
                }
                prod.setStockDisponible(Math.max(0, prod.getStockDisponible() - cantidad));
                productoRepository.save(prod);

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
        BigDecimal comision = subtotal.multiply(porcentajeComision()).movePointLeft(2);
        pedido.setComisionPrismaValor(comision);

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

        if (pushNotificationService != null) {
            pushNotificationService.notificarNuevoPedidoProveedor(guardado);
        }

        return guardado;
    }

    @Override
    public Pedido repetirUltimoPedido(UserPrincipal currentUser) {
        Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        List<Pedido> pedidos = pedidoRepository.findByTenderoIdOrderByFechaCreacionDesc(currentUser.getId());
        if (pedidos.isEmpty()) {
            pedidos = pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(tienda.getId());
        }
        if (pedidos.isEmpty()) {
            throw new BadRequestException("No hay pedidos previos para repetir");
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

        return guardado;
    }

    @Override
    public Disputa reportarAveria(Long pedidoId, Map<String, String> payload, UserPrincipal currentUser) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        Usuario duenio = pedido.getTienda() != null ? pedido.getTienda().getTendero() : null;
        if (!esAdmin(currentUser) && duenio != null && !duenio.getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El pedido pertenece a otra tienda");
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

        return disputaRepository.save(d);
    }
}
