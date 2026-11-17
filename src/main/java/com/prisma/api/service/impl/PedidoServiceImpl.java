package com.prisma.api.service.impl;

import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.Tienda;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.event.OrderCreatedEvent;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.TiendaRepository;
import com.prisma.api.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación de PedidoService con publicación desacoplada de eventos de dominio.
 * (HU-22 / SCRUM-34).
 */
@Service
public class PedidoServiceImpl implements PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoServiceImpl.class);
    private static final BigDecimal TARIFA_COMISION_PRISMA = new BigDecimal("0.035"); // 3.50%

    private final PedidoRepository pedidoRepository;
    private final TiendaRepository tiendaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PedidoServiceImpl(
            PedidoRepository pedidoRepository,
            TiendaRepository tiendaRepository,
            ProveedorRepository proveedorRepository,
            ApplicationEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.tiendaRepository = tiendaRepository;
        this.proveedorRepository = proveedorRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Pedido crearPedido(Long tiendaId, Long proveedorId, BigDecimal total, int itemsCount) {
        log.info("Creando pedido para Tienda ID: {}, Proveedor ID: {}, Total: {}", tiendaId, proveedorId, total);

        Tienda tienda = tiendaRepository.findById(tiendaId).orElse(null);
        Proveedor proveedor = proveedorRepository.findById(proveedorId).orElse(null);

        Pedido pedido = new Pedido();
        pedido.setNumeroPedido("PED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pedido.setTienda(tienda);
        pedido.setProveedor(proveedor);
        pedido.setSubtotal(total);
        pedido.setDescuento(BigDecimal.ZERO);
        pedido.setTotalAPagar(total);
        pedido.setComisionPrismaValor(total.multiply(TARIFA_COMISION_PRISMA).setScale(2, RoundingMode.HALF_UP));
        pedido.setEstado(EstadoPedido.CREADO);
        pedido.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        pedido.setPinEntregaHash("1234");
        pedido.setFechaEstimadaEntrega(LocalDate.now().plusDays(1));
        pedido.setFechaCreacion(OffsetDateTime.now());
        pedido.setActualizadoEn(OffsetDateTime.now());

        Pedido guardado = pedidoRepository.save(pedido);
        log.info("Pedido guardado exitosamente con ID: {}. Publicando OrderCreatedEvent...", guardado.getId());

        // Publicación desacoplada del evento de dominio
        OrderCreatedEvent event = new OrderCreatedEvent(
                this,
                guardado.getId(),
                tiendaId,
                proveedorId,
                total,
                itemsCount
        );
        eventPublisher.publishEvent(event);

        return guardado;
    }

    @Override
    @Transactional
    public Pedido finalizarPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado: " + pedidoId));
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setFechaEntregaReal(OffsetDateTime.now());
        pedido.setActualizadoEn(OffsetDateTime.now());
        return pedidoRepository.save(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pedido> obtenerPorId(Long pedidoId) {
        return pedidoRepository.findById(pedidoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }
}
