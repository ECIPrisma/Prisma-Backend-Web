package com.prisma.api.service.impl;

import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoPedidoResponse;
import com.prisma.api.dto.RecaudosProveedorResponse;
import com.prisma.api.entity.PagoPedido;
import com.prisma.api.entity.Pedido;
import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.pagos.ReglasSplitPayment;
import com.prisma.api.repository.PagoPedidoRepository;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.ConfirmacionPagoService;
import com.prisma.api.service.PagoPedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PagoPedidoServiceImpl implements PagoPedidoService {

    private static final Set<EstadoPagoPedido> PAGADO = EnumSet.of(EstadoPagoPedido.EN_GARANTIA, EstadoPagoPedido.LIBERADO);
    private static final Set<EstadoPedido> ADMITE_PAGO = EnumSet.of(
            EstadoPedido.CREADO, EstadoPedido.ACEPTADO, EstadoPedido.CARGADO_EN_BODEGA, EstadoPedido.EN_RUTA);

    private final PagoPedidoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final PasarelaPagosSimulada pasarela;
    private final ConfirmacionPagoService confirmacionPagoService;

    public PagoPedidoServiceImpl(PagoPedidoRepository pagoRepository, PedidoRepository pedidoRepository,
                                 PasarelaPagosSimulada pasarela, ConfirmacionPagoService confirmacionPagoService) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pasarela = pasarela;
        this.confirmacionPagoService = confirmacionPagoService;
    }

    @Override
    @Transactional
    public PagoPedidoResponse pagar(Long pedidoId, PagarPedidoRequest datos, UserPrincipal tendero) {
        if (datos == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Faltan los datos del pago");
        }
        Pedido pedido = pedidoDelTendero(pedidoId, tendero);

        if (pagoRepository.findFirstByPedidoIdAndEstadoIn(pedidoId, PAGADO).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Este pedido ya fue pagado.");
        }
        if (!ADMITE_PAGO.contains(pedido.getEstado())) {
            throw new ApiException(HttpStatus.CONFLICT, "Este pedido ya no admite pagos en línea.");
        }

        PasarelaPagosSimulada.Resultado resultado = pasarela.procesar(datos.getMedio(), datos);
        // HU-13: un solo movimiento del tendero; 3.5% PRISMA y 96.5% proveedor, calculados juntos (atómico)
        DivisionPago division = ReglasSplitPayment.dividir(pedido.getTotalAPagar());

        PagoPedido pago = new PagoPedido();
        pago.setPedidoId(pedido.getId());
        pago.setNumeroPedido(pedido.getNumeroPedido());
        pago.setProveedorId(pedido.getProveedor().getId());
        pago.setTenderoId(tendero.getId());
        pago.setNombreTienda(pedido.getTienda().getNombreTienda());
        pago.setReferencia(PasarelaPagosSimulada.generarCodigo("PRISMA-" + pedido.getNumeroPedido() + "-", 5));
        pago.setTransaccionId(resultado.transaccionId());
        pago.setPasarela(PasarelaPagosSimulada.NOMBRE);
        pago.setMedio(datos.getMedio());
        pago.setDetalleMedio(resultado.detalleMedio());
        pago.setMonto(division.total());
        pago.setPorcentajeComision(ReglasSplitPayment.PORCENTAJE_COMISION);
        pago.setComisionPrisma(division.comisionPrisma());
        pago.setMontoProveedor(division.montoProveedor());
        pago.setTarifaPasarela(ReglasSplitPayment.tarifaPasarela(datos.getMedio(), division.total()));
        // HU-13: la transacción queda PENDIENTE hasta que llegue el webhook de la pasarela
        pago.setEstado(EstadoPagoPedido.PENDIENTE);
        pago.setCreadoEn(OffsetDateTime.now());
        pago = pagoRepository.save(pago);

        // La pasarela simulada notifica el resultado con un webhook firmado, igual que Wompi.
        // Se procesa con el mismo servicio que atiende POST /api/v1/pagos/webhooks/wompi.
        Map<String, Object> evento = pasarela.eventoTransaccion(resultado, pago.getReferencia(), pago.getMonto(), pago.getMedio());
        confirmacionPagoService.procesarEvento(evento);

        PagoPedido confirmado = pagoRepository.findById(pago.getId()).orElse(pago);
        return PagoPedidoResponse.de(confirmado);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoPedidoResponse obtenerPago(Long pedidoId, UserPrincipal tendero) {
        pedidoDelTendero(pedidoId, tendero);
        return pagoRepository.findFirstByPedidoIdOrderByCreadoEnDescIdDesc(pedidoId)
                .map(PagoPedidoResponse::de)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Este pedido aún no tiene pagos en línea."));
    }

    @Override
    @Transactional(readOnly = true)
    public void anotarEstadoPago(Collection<Pedido> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) return;
        List<Long> ids = pedidos.stream().map(Pedido::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return;

        // Ordenados del más antiguo al más reciente: un pago aprobado prevalece sobre los rechazos
        Map<Long, PagoPedido> vigente = new HashMap<>();
        for (PagoPedido p : pagoRepository.findByPedidoIdInOrderByCreadoEnAscIdAsc(ids)) {
            PagoPedido actual = vigente.get(p.getPedidoId());
            if (actual == null || !PAGADO.contains(actual.getEstado())) {
                vigente.put(p.getPedidoId(), p);
            }
        }
        for (Pedido pedido : pedidos) {
            PagoPedido pago = vigente.get(pedido.getId());
            if (pago != null) {
                pedido.setEstadoPago(pago.getEstado().name());
                pedido.setMedioPagoEnLinea(pago.getMedio().name());
                pedido.setDetallePago(pago.getDetalleMedio());
                pedido.setReferenciaPago(pago.getReferencia());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaPagadoEnLinea(Long pedidoId) {
        return pagoRepository.findFirstByPedidoIdAndEstadoIn(pedidoId, PAGADO).isPresent();
    }

    @Override
    @Transactional
    public boolean liberarGarantia(Long pedidoId) {
        Optional<PagoPedido> pago = pagoRepository.findFirstByPedidoIdAndEstadoIn(pedidoId, PAGADO);
        pago.filter(p -> p.getEstado() == EstadoPagoPedido.EN_GARANTIA).ifPresent(p -> {
            p.setEstado(EstadoPagoPedido.LIBERADO);
            p.setLiberadoEn(OffsetDateTime.now());
            pagoRepository.save(p);
        });
        return pago.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public RecaudosProveedorResponse recaudosProveedor(UserPrincipal proveedor) {
        if (proveedor == null || proveedor.getProveedorId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El usuario no está asociado a ningún proveedor");
        }
        List<PagoPedido> pagos = pagoRepository.findByProveedorIdAndEstadoInOrderByCreadoEnDesc(proveedor.getProveedorId(), PAGADO);
        BigDecimal enGarantia = BigDecimal.ZERO;
        BigDecimal liberado = BigDecimal.ZERO;
        int nGarantia = 0;
        int nLiberados = 0;
        for (PagoPedido p : pagos) {
            if (p.getEstado() == EstadoPagoPedido.EN_GARANTIA) {
                enGarantia = enGarantia.add(p.getMontoProveedor());
                nGarantia++;
            } else {
                liberado = liberado.add(p.getMontoProveedor());
                nLiberados++;
            }
        }
        return new RecaudosProveedorResponse(enGarantia, liberado, nGarantia, nLiberados,
                pagos.stream().map(PagoPedidoResponse::de).toList());
    }

    // ---------------------------------------------------------------------------------------------

    private Pedido pedidoDelTendero(Long pedidoId, UserPrincipal tendero) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        boolean esSuyo = tendero != null && pedido.getTienda() != null && pedido.getTienda().getTendero() != null
                && pedido.getTienda().getTendero().getId().equals(tendero.getId());
        if (!esSuyo) {
            // Mismo mensaje que "no existe" para no revelar pedidos de otras tiendas
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        return pedido;
    }
}
