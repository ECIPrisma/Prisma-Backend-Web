package com.prisma.api.enums;

/**
 * Estado del pago de la suscripción Prisma Pro de un proveedor.
 */
public enum EstadoPagoSuscripcion {
    /** Transacción creada en la pasarela; se espera el webhook que la confirma o la rechaza. */
    PENDIENTE,
    /** Webhook válido con la transacción aprobada: el mes quedó pagado. */
    APROBADO,
    /** La entidad financiera rechazó la transacción: la suscripción no cambia. */
    RECHAZADO
}
