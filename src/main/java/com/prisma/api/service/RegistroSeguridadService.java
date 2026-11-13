package com.prisma.api.service;

import com.prisma.api.entity.AuditLog;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-14 – Log de seguridad de los webhooks de pago rechazados.
 *
 * <p>Se guarda en {@code audit_logs} con severidad SECURITY en una transacción PROPIA (REQUIRES_NEW):
 * así el registro queda aunque la petición termine en 401 y no toca nada del pedido ni del pago.</p>
 */
@Service
public class RegistroSeguridadService {

    public static final String EVENTO_WEBHOOK_RECHAZADO = "WEBHOOK_PAGO_FIRMA_INVALIDA";
    private static final Logger SEGURIDAD = LoggerFactory.getLogger("SEGURIDAD");
    private static final int MAX_USER_AGENT = 500;

    private final AuditLogRepository auditLogRepository;

    public RegistroSeguridadService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarWebhookRechazado(String motivo, String ipOrigen, String userAgent, int tamanoCuerpo) {
        String ip = ipOrigen == null || ipOrigen.isBlank() ? "desconocida" : recortar(ipOrigen, 45);
        // Nunca se registra el cuerpo ni la firma recibida: solo el motivo y metadatos de la petición
        SEGURIDAD.warn("Webhook de pago RECHAZADO (401): {} | ip={} | bytes={}", motivo, ip, tamanoCuerpo);

        AuditLog log = new AuditLog();
        log.setAgenteId(0L);
        log.setAgenteNombre("Pasarela externa (no autenticada)");
        log.setAgenteEmail("webhook@pasarela");
        log.setAgenteRol("EXTERNO");
        log.setTipoEvento(EVENTO_WEBHOOK_RECHAZADO);
        log.setNivelSeveridad(SeveridadAudit.SECURITY);
        log.setRecursoAfectadoTipo("WEBHOOK_PAGO");
        log.setRecursoAfectadoId("POST /api/v1/pagos/webhooks/wompi");
        log.setJustificacion("Notificación de pago rechazada sin modificar pedidos: " + motivo
                + " (" + tamanoCuerpo + " bytes recibidos)");
        log.setIpOrigen(ip);
        log.setUserAgent(userAgent == null || userAgent.isBlank() ? "Unknown" : recortar(userAgent, MAX_USER_AGENT));
        log.setResultado("RECHAZADO");
        auditLogRepository.save(log);
    }

    private static String recortar(String valor, int max) {
        return valor.length() <= max ? valor : valor.substring(0, max);
    }
}
