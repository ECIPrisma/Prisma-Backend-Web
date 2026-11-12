package com.prisma.api.dto;

import java.math.BigDecimal;

/**
 * División de un pago entre el proveedor y PRISMA (HU-12).
 *
 * @param total              valor pagado por el tendero
 * @param comisionPrisma     marketplace_fee que se envía a Mercado Pago (monto absoluto, no porcentaje)
 * @param montoProveedor     lo que recibe el proveedor antes de la tarifa propia de la pasarela
 * @param porcentajeProveedor porcentaje que recibe el proveedor (100 - comisión)
 */
public record DivisionPago(BigDecimal total, BigDecimal comisionPrisma, BigDecimal montoProveedor,
                           BigDecimal porcentajeProveedor) {
}
