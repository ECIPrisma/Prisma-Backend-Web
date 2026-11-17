package com.prisma.api.service;

import com.prisma.api.entity.PagoSuscripcion;
import com.prisma.api.entity.SuscripcionProveedor;
import com.prisma.api.enums.EstadoPagoSuscripcion;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.pagos.PlanPrismaPro;
import com.prisma.api.repository.PagoSuscripcionRepository;
import com.prisma.api.repository.SuscripcionProveedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Aplica el resultado de un webhook "transaction.updated" YA AUTENTICADO (HMAC validado en
 * {@link WebhookPagosService}) a un pago de suscripción Prisma Pro.
 *
 * <ul>
 *   <li>Idempotente: un evento repetido o tardío no cambia un pago que ya tiene resultado final.</li>
 *   <li>Verifica que la transacción y el monto coincidan con los registrados (400 si no).</li>
 *   <li>Pago aprobado: la suscripción se extiende un mes (desde hoy si estaba vencida, o desde el día siguiente
 *       al vencimiento si seguía activa) y se avisa al proveedor.</li>
 * </ul>
 */
@Service
public class ConfirmacionSuscripcionService {

    private static final Set<String> ESTADOS_RECHAZO = Set.of("DECLINED", "ERROR", "VOIDED");

    private final PagoSuscripcionRepository pagoRepository;
    private final SuscripcionProveedorRepository suscripcionRepository;

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    public ConfirmacionSuscripcionService(PagoSuscripcionRepository pagoRepository,
                                          SuscripcionProveedorRepository suscripcionRepository) {
        this.pagoRepository = pagoRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    @Transactional
    public ConfirmacionPagoService.Resultado procesar(Map<String, Object> transaccion) {
        Optional<PagoSuscripcion> encontrado = pagoRepository.findByReferencia(String.valueOf(transaccion.get("reference")));
        if (encontrado.isEmpty()) {
            return ConfirmacionPagoService.Resultado.IGNORADO;
        }
        PagoSuscripcion pago = encontrado.get();
        if (pago.getEstado() != EstadoPagoSuscripcion.PENDIENTE) {
            return ConfirmacionPagoService.Resultado.YA_PROCESADO;
        }

        Object centavos = transaccion.get("amount_in_cents");
        boolean montoCorrecto = centavos instanceof Number
                && ((Number) centavos).longValue() == PasarelaPagosSimulada.enCentavos(pago.getMonto());
        if (!montoCorrecto || !pago.getTransaccionId().equals(String.valueOf(transaccion.get("id")))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El evento no coincide con la transacción registrada");
        }

        String estado = String.valueOf(transaccion.get("status"));
        if ("APPROVED".equals(estado)) {
            extenderSuscripcion(pago);
            pago.setEstado(EstadoPagoSuscripcion.APROBADO);
        } else if (ESTADOS_RECHAZO.contains(estado)) {
            pago.setEstado(EstadoPagoSuscripcion.RECHAZADO);
            Object mensaje = transaccion.get("status_message");
            pago.setMotivoRechazo(mensaje != null ? String.valueOf(mensaje) : ConfirmacionPagoService.MOTIVO_RECHAZO_POR_DEFECTO);
        } else {
            return ConfirmacionPagoService.Resultado.IGNORADO; // PENDING u otro estado intermedio
        }
        pago.setConfirmadoEn(OffsetDateTime.now());
        pagoRepository.save(pago);

        if (pago.getEstado() == EstadoPagoSuscripcion.APROBADO && pushNotificationService != null) {
            pushNotificationService.notificarSuscripcionPagada(pago.getProveedorId(), pago.getReferencia(), pago.getPeriodoHasta());
        }
        return ConfirmacionPagoService.Resultado.PROCESADO;
    }

    private void extenderSuscripcion(PagoSuscripcion pago) {
        LocalDate hoy = LocalDate.now();
        SuscripcionProveedor suscripcion = suscripcionRepository.findByProveedorId(pago.getProveedorId())
                .orElseGet(() -> {
                    SuscripcionProveedor nueva = new SuscripcionProveedor();
                    nueva.setProveedorId(pago.getProveedorId());
                    nueva.setPlan(PlanPrismaPro.CODIGO);
                    nueva.setPrecioMensual(PlanPrismaPro.PRECIO_MENSUAL_CON_IVA);
                    return nueva;
                });

        LocalDate vigente = suscripcion.getVigenteHasta();
        LocalDate desde = vigente != null && !vigente.isBefore(hoy) ? vigente.plusDays(1) : hoy;
        LocalDate hasta = desde.plusMonths(1).minusDays(1);

        suscripcion.setVigenteHasta(hasta);
        suscripcion.setActualizadaEn(OffsetDateTime.now());
        suscripcionRepository.save(suscripcion);

        pago.setPeriodoDesde(desde);
        pago.setPeriodoHasta(hasta);
    }
}
