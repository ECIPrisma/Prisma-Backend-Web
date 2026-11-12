package com.prisma.api.enums;

/**
 * Estado del pago en línea de un pedido (N-08 / HU-12).
 */
public enum EstadoPagoPedido {
    /** Pago aprobado por la pasarela; PRISMA retiene el dinero hasta que el tendero confirma la entrega con el PIN. */
    EN_GARANTIA,
    /** La pasarela rechazó la transacción; el pedido sigue pendiente de pago. */
    RECHAZADO,
    /** Entrega confirmada: el dinero se libera al proveedor (menos la comisión PRISMA). */
    LIBERADO
}
