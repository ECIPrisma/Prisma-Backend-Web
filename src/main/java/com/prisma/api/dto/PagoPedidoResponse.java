package com.prisma.api.dto;

import com.prisma.api.entity.PagoPedido;
import com.prisma.api.enums.EstadoPagoPedido;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado de un pago en línea (simulado) que se muestra al tendero y al proveedor.
 * HU-13: incluye la división del pago (3.5% PRISMA / 96.5% proveedor) y la tarifa de Wompi por separado.
 */
public record PagoPedidoResponse(
        Long id,
        Long pedidoId,
        String numeroPedido,
        String nombreTienda,
        String referencia,
        String transaccionId,
        String pasarela,
        String medio,
        String detalleMedio,
        BigDecimal monto,
        BigDecimal porcentajeComision,
        BigDecimal comisionPrisma,
        BigDecimal montoProveedor,
        BigDecimal tarifaPasarela,
        String estado,
        boolean aprobado,
        String motivoRechazo,
        boolean confirmadoPorWebhook,
        OffsetDateTime creadoEn,
        OffsetDateTime confirmadoEn,
        OffsetDateTime liberadoEn,
        boolean simulado,
        OffsetDateTime reembolsadoEn,
        String motivoReembolso) {

    public static PagoPedidoResponse de(PagoPedido p) {
        // "aprobado" = la pasarela aprobó la transacción (un pago reembolsado también fue aprobado en su momento)
        boolean aprobado = p.getEstado() == EstadoPagoPedido.EN_GARANTIA || p.getEstado() == EstadoPagoPedido.LIBERADO
                || p.getEstado() == EstadoPagoPedido.REEMBOLSADO;
        return new PagoPedidoResponse(p.getId(), p.getPedidoId(), p.getNumeroPedido(), p.getNombreTienda(),
                p.getReferencia(), p.getTransaccionId(), p.getPasarela(), p.getMedio().name(), p.getDetalleMedio(),
                p.getMonto(), p.getPorcentajeComision(), p.getComisionPrisma(), p.getMontoProveedor(),
                p.getTarifaPasarela(), p.getEstado().name(), aprobado, p.getMotivoRechazo(),
                p.getConfirmadoEn() != null, p.getCreadoEn(), p.getConfirmadoEn(), p.getLiberadoEn(), true,
                p.getReembolsadoEn(), p.getMotivoReembolso());
    }
}
