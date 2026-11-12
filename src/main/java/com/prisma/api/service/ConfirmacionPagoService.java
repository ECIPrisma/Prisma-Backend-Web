package com.prisma.api.service;

import com.prisma.api.entity.PagoPedido;
import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.FirmaEventosWompi;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.repository.PagoPedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HU-13 – Confirmación del pago por webhook (evento "transaction.updated" con el formato de Wompi).
 *
 * <ul>
 *   <li>Valida el checksum SHA-256 antes de tocar cualquier dato (401 si no coincide).</li>
 *   <li>Es idempotente: un evento repetido o tardío no cambia un pago que ya tiene resultado final.</li>
 *   <li>Verifica que la transacción y el monto coincidan con los registrados (400 si no).</li>
 * </ul>
 */
@Service
public class ConfirmacionPagoService {

    public enum Resultado { PROCESADO, YA_PROCESADO, IGNORADO }

    public static final String MOTIVO_RECHAZO_POR_DEFECTO = "La entidad financiera rechazó la transacción";
    private static final Set<String> ESTADOS_RECHAZO = Set.of("DECLINED", "ERROR", "VOIDED");
    private static final Logger logger = LoggerFactory.getLogger(ConfirmacionPagoService.class);

    private final PagoPedidoRepository pagoRepository;
    private final FirmaEventosWompi firma;

    public ConfirmacionPagoService(PagoPedidoRepository pagoRepository, FirmaEventosWompi firma) {
        this.pagoRepository = pagoRepository;
        this.firma = firma;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Resultado procesarEvento(Map<String, Object> evento) {
        if (evento == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evento vacío");
        }
        Map<String, Object> data = comoMapa(evento.get("data"));
        Map<String, Object> signature = comoMapa(evento.get("signature"));
        Object timestamp = evento.get("timestamp");
        if (data == null || signature == null || !(timestamp instanceof Number)
                || !(signature.get("properties") instanceof List) || !(signature.get("checksum") instanceof String)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evento con formato inválido");
        }

        List<String> propiedades = ((List<Object>) signature.get("properties")).stream().map(String::valueOf).toList();
        if (!firma.esValido(data, propiedades, ((Number) timestamp).longValue(), (String) signature.get("checksum"))) {
            logger.warn("Webhook de pagos con firma inválida rechazado");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma del evento inválida");
        }

        if (!"transaction.updated".equals(evento.get("event"))) {
            return Resultado.IGNORADO;
        }
        Map<String, Object> transaccion = comoMapa(data.get("transaction"));
        if (transaccion == null || transaccion.get("reference") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El evento no trae la transacción");
        }

        PagoPedido pago = pagoRepository.findByReferencia(String.valueOf(transaccion.get("reference"))).orElse(null);
        if (pago == null) {
            // Wompi reintenta mientras no reciba 200: una referencia ajena se acepta y se ignora
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
            // Split atómico ya calculado en el mismo registro: 3.5% PRISMA + 96.5% proveedor = total
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
        return Resultado.PROCESADO;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> comoMapa(Object valor) {
        return valor instanceof Map ? (Map<String, Object>) valor : null;
    }
}
