package com.prisma.api.dto;

import com.prisma.api.entity.PagoPedido;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Resultado de un pago en línea (simulado) que se muestra al tendero y al proveedor.
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
        BigDecimal comisionPrisma,
        BigDecimal montoProveedor,
        String estado,
        boolean aprobado,
        String motivoRechazo,
        OffsetDateTime creadoEn,
        OffsetDateTime liberadoEn,
        boolean simulado) {

    public static PagoPedidoResponse de(PagoPedido p) {
        return new PagoPedidoResponse(p.getId(), p.getPedidoId(), p.getNumeroPedido(), p.getNombreTienda(),
                p.getReferencia(), p.getTransaccionId(), p.getPasarela(), p.getMedio().name(), p.getDetalleMedio(),
                p.getMonto(), p.getComisionPrisma(), p.getMontoProveedor(), p.getEstado().name(),
                p.getEstado() != com.prisma.api.enums.EstadoPagoPedido.RECHAZADO, p.getMotivoRechazo(),
                p.getCreadoEn(), p.getLiberadoEn(), true);
    }
}
