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
import com.prisma.api.pagos.PasarelaPagosResiliente;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.pagos.ReglasSplitPayment;
import com.prisma.api.repository.PagoPedidoRepository;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PagoPedidoService;
import com.prisma.api.service.PushNotificationService;
import com.prisma.api.service.WebhookPagosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PagoPedidoServiceImpl implements PagoPedidoService {

    private static final Set<EstadoPagoPedido> PAGADO = EnumSet.of(EstadoPagoPedido.EN_GARANTIA, EstadoPagoPedido.LIBERADO);
    // Resultados finales de un pago aprobado: prevalecen sobre los intentos rechazados al mostrar el pedido
    private static final Set<EstadoPagoPedido> APROBADO_ALGUNA_VEZ =
            EnumSet.of(EstadoPagoPedido.EN_GARANTIA, EstadoPagoPedido.LIBERADO, EstadoPagoPedido.REEMBOLSADO);
    private static final Set<EstadoPagoPedido> VISIBLES_PROVEEDOR = APROBADO_ALGUNA_VEZ;
    private static final Set<EstadoPedido> ADMITE_PAGO = EnumSet.of(
            EstadoPedido.CREADO, EstadoPedido.ACEPTADO, EstadoPedido.CARGADO_EN_BODEGA, EstadoPedido.EN_RUTA);

    private final PagoPedidoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final PasarelaPagosSimulada pasarela;
    private final PasarelaPagosResiliente pasarelaResiliente;
    private final WebhookPagosService webhookPagosService;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public PagoPedidoServiceImpl(PagoPedidoRepository pagoRepository, PedidoRepository pedidoRepository,
                                 PasarelaPagosSimulada pasarela, PasarelaPagosResiliente pasarelaResiliente,
                                 WebhookPagosService webhookPagosService) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pasarela = pasarela;
        this.pasarelaResiliente = pasarelaResiliente;
        this.webhookPagosService = webhookPagosService;
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

        // HU-19: la llamada a la pasarela pasa por el circuit breaker. Si está abierto responde 503 de inmediato
        // y no se crea ningún registro de pago (el pedido no cambia).
        PasarelaPagosSimulada.Resultado resultado = pasarelaResiliente.procesar(datos.getMedio(), datos);
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

        // La pasarela simulada notifica el resultado con un webhook firmado (HMAC-SHA256 en el encabezado).
        // Pasa por la MISMA validación que POST /api/v1/pagos/webhooks/wompi (HU-14).
        PasarelaPagosSimulada.NotificacionWebhook webhook =
                pasarela.notificarTransaccion(resultado, pago.getReferencia(), pago.getMonto(), pago.getMedio());
        webhookPagosService.recibir(webhook.cuerpo(), webhook.firma(), "pasarela-simulada", "PasarelaPagosSimulada");

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
            if (actual == null || !APROBADO_ALGUNA_VEZ.contains(actual.getEstado())) {
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
    @Transactional
    public boolean reembolsarGarantia(Long pedidoId, String motivo) {
        Optional<PagoPedido> enGarantia = pagoRepository.findFirstByPedidoIdAndEstadoIn(
                pedidoId, EnumSet.of(EstadoPagoPedido.EN_GARANTIA));
        if (enGarantia.isEmpty()) {
            return false; // sin pago en línea, o ya liberado al proveedor (pedido entregado)
        }
        PagoPedido pago = enGarantia.get();
        pago.setEstado(EstadoPagoPedido.REEMBOLSADO);
        pago.setReembolsadoEn(OffsetDateTime.now());
        String detalle = motivo == null || motivo.isBlank() ? "Pedido cancelado antes de la entrega" : motivo.trim();
        pago.setMotivoReembolso(detalle.length() > 200 ? detalle.substring(0, 200) : detalle);
        pagoRepository.save(pago);

        if (pushNotificationService != null) {
            pedidoRepository.findById(pedidoId)
                    .ifPresent(pedido -> pushNotificationService.notificarReembolsoTendero(pedido, pago.getMonto()));
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public RecaudosProveedorResponse recaudosProveedor(UserPrincipal proveedor) {
        if (proveedor == null || proveedor.getProveedorId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El usuario no está asociado a ningún proveedor");
        }
        List<PagoPedido> pagos = pagoRepository.findByProveedorIdAndEstadoInOrderByCreadoEnDesc(
                proveedor.getProveedorId(), VISIBLES_PROVEEDOR);
        BigDecimal enGarantia = BigDecimal.ZERO;
        BigDecimal liberado = BigDecimal.ZERO;
        BigDecimal reembolsado = BigDecimal.ZERO;
        int nGarantia = 0;
        int nLiberados = 0;
        int nReembolsados = 0;
        for (PagoPedido p : pagos) {
            switch (p.getEstado()) {
                case EN_GARANTIA -> { enGarantia = enGarantia.add(p.getMontoProveedor()); nGarantia++; }
                case LIBERADO -> { liberado = liberado.add(p.getMontoProveedor()); nLiberados++; }
                case REEMBOLSADO -> { reembolsado = reembolsado.add(p.getMontoProveedor()); nReembolsados++; }
                default -> { }
            }
        }
        return new RecaudosProveedorResponse(enGarantia, liberado, nGarantia, nLiberados, reembolsado, nReembolsados,
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
