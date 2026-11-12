package com.prisma.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pagos en línea de los pedidos del proveedor: lo retenido en garantía y lo ya liberado.
 */
public record RecaudosProveedorResponse(
        BigDecimal totalEnGarantia,
        BigDecimal totalLiberado,
        int cantidadEnGarantia,
        int cantidadLiberados,
        List<PagoPedidoResponse> pagos) {
}
