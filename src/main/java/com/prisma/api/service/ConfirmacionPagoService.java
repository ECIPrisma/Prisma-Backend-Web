package com.prisma.api.service;

import com.prisma.api.entity.PagoPedido;
import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.repository.PagoPedidoRepository;
import com.prisma.api.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * HU-13 / HU-14 – Aplica el resultado de un evento "transaction.updated" YA AUTENTICADO.
 * La firma HMAC del encabezado se valida antes, en {@link WebhookPagosService}; este servicio nunca debe
 * llamarse con un evento sin validar.
 *
 * <ul>
 *   <li>Idempotente: un evento repetido o tardío no cambia un pago que ya tiene resultado final.</li>
 *   <li>Verifica que la transacción y el monto coincidan con los registrados (400 si no).</li>
 *   <li>Pago aprobado → "pago confirmado" (en garantía) y aviso al proveedor para iniciar el despacho.</li>
 * </ul>
 */
@Service
public class ConfirmacionPagoService {

    public enum Resultado { PROCESADO, YA_PROCESADO, IGNORADO }

    public static final String MOTIVO_RECHAZO_POR_DEFECTO = "La entidad financiera rechazó la transacción";
    private static final Set<String> ESTADOS_RECHAZO = Set.of("DECLINED", "ERROR", "VOIDED");

    private final PagoPedidoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public ConfirmacionPagoService(PagoPedidoRepository pagoRepository, PedidoRepository pedidoRepository) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public Resultado procesarEvento(Map<String, Object> evento) {
        if (evento == null || !"transaction.updated".equals(evento.get("event"))) {
            return Resultado.IGNORADO;
        }
        Map<String, Object> data = comoMapa(evento.get("data"));
        Map<String, Object> transaccion = data == null ? null : comoMapa(data.get("transaction"));
        if (transaccion == null || transaccion.get("reference") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El evento no trae la transacción");
        }

        PagoPedido pago = pagoRepository.findByReferencia(String.valueOf(transaccion.get("reference"))).orElse(null);
        if (pago == null) {
            // La pasarela reintenta mientras no reciba 200: una referencia ajena se acepta y se ignora
            return Resultado.IGNORADO;
        }
        if (pago.getEstado() != EstadoPagoPedido.PENDIENTE) {
            return Resultado.YA_PROCESADO;
        }

        Object centavos = transaccion.get("amount_in_cents");
        boolean montoCorrecto = centavos instanceof Number
                && ((Number) centavos).longValue() == PasarelaPagosSimulada.enCentavos(pago.getMonto());
        if (!montoCorrecto || !pago.getTransaccionId().equals(String.valueOf(transaccion.get("id")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El evento no coincide con la transacción registrada");
        }

        String estado = String.valueOf(transaccion.get("status"));
        if ("APPROVED".equals(estado)) {
            // Pago confirmado: el split (3.5% PRISMA + 96.5% proveedor) queda en garantía hasta la entrega
            pago.setEstado(EstadoPagoPedido.EN_GARANTIA);
        } else if (ESTADOS_RECHAZO.contains(estado)) {
            pago.setEstado(EstadoPagoPedido.RECHAZADO);
            Object mensaje = transaccion.get("status_message");
            pago.setMotivoRechazo(mensaje != null ? String.valueOf(mensaje) : MOTIVO_RECHAZO_POR_DEFECTO);
        } else {
            return Resultado.IGNORADO; // PENDING u otro estado intermedio
        }
        pago.setConfirmadoEn(OffsetDateTime.now());
        pagoRepository.save(pago);

        if (pago.getEstado() == EstadoPagoPedido.EN_GARANTIA && pushNotificationService != null) {
            // HU-14 criterio 1: avisar al proveedor para que inicie el despacho
            pedidoRepository.findById(pago.getPedidoId())
                    .ifPresent(pedido -> pushNotificationService.notificarPagoConfirmadoProveedor(pedido, pago.getMonto()));
        }
        return Resultado.PROCESADO;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comoMapa(Object valor) {
        return valor instanceof Map ? (Map<String, Object>) valor : null;
    }
}
