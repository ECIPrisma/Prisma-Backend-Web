package com.prisma.api.service.impl;

import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.enums.TipoIncidente;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.TiendaService;
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
        Tienda tienda = tiendaRepository.findFirstByTenderoId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada para el tendero autenticado"));

        Long proveedorId = payload.containsKey("proveedorId") ? Long.parseLong(payload.get("proveedorId").toString()) : 1L;
        Proveedor prov = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + proveedorId));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) payload.getOrDefault("items", List.of());

        Pedido pedido = new Pedido();
        String randomSuffix = String.format("%04d", new SecureRandom().nextInt(10000));
        pedido.setNumeroPedido("PED-2026-" + randomSuffix);
        pedido.setTienda(tienda);
        pedido.setProveedor(prov);
        pedido.setEstado(EstadoPedido.CREADO);
        pedido.setFechaEstimadaEntrega(LocalDate.now());
        pedido.setMedioPagoUtilizado(MedioPago.EFECTIVO);

        String pin = String.format("%06d", new SecureRandom().nextInt(1000000));
        pedido.setPinEntregaHash(pin);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<ItemPedido> itemsGuardados = new ArrayList<>();

        for (Map<String, Object> it : itemsData) {
            Long prodId = Long.parseLong(it.get("productoId").toString());
            int cantidad = Integer.parseInt(it.get("cantidad").toString());
            Producto prod = productoRepository.findById(prodId).orElse(null);
            if (prod != null) {
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

        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
            subtotal = new BigDecimal("125000.00");
        }

        pedido.setSubtotal(subtotal);
        pedido.setTotalAPagar(subtotal);
        BigDecimal comision = subtotal.multiply(new BigDecimal("0.025"));
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
        nuevo.setNumeroPedido("PED-2026-" + String.format("%04d", new SecureRandom().nextInt(10000)));
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
