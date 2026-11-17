package com.prisma.api.dto;

import com.prisma.api.entity.PagoSuscripcion;
import com.prisma.api.enums.EstadoPagoSuscripcion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Un pago de la suscripción Prisma Pro (resultado del checkout e historial del proveedor).
 */
public record PagoSuscripcionResponse(
        Long id,
        String plan,
        String referencia,
        String transaccionId,
        String pasarela,
        String medio,
        String detalleMedio,
        BigDecimal monto,
        BigDecimal valorNeto,
        BigDecimal valorIva,
        BigDecimal tarifaPasarela,
        String estado,
        boolean aprobado,
        String motivoRechazo,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        boolean confirmadoPorWebhook,
        OffsetDateTime creadoEn,
        OffsetDateTime confirmadoEn,
        boolean simulado) {

    public static PagoSuscripcionResponse de(PagoSuscripcion p) {
        return new PagoSuscripcionResponse(p.getId(), p.getPlan(), p.getReferencia(), p.getTransaccionId(),
                p.getPasarela(), p.getMedio().name(), p.getDetalleMedio(), p.getMonto(), p.getValorNeto(),
                p.getValorIva(), p.getTarifaPasarela(), p.getEstado().name(),
                p.getEstado() == EstadoPagoSuscripcion.APROBADO, p.getMotivoRechazo(), p.getPeriodoDesde(),
                p.getPeriodoHasta(), p.getConfirmadoEn() != null, p.getCreadoEn(), p.getConfirmadoEn(), true);
    }
}
