package com.prisma.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pagos en línea de los pedidos del proveedor: lo retenido en garantía, lo ya liberado y lo reembolsado al tendero.
 */
public record RecaudosProveedorResponse(
        BigDecimal totalEnGarantia,
        BigDecimal totalLiberado,
        int cantidadEnGarantia,
        int cantidadLiberados,
        BigDecimal totalReembolsado,
        int cantidadReembolsados,
        List<PagoPedidoResponse> pagos) {
}
