package com.prisma.api.pagos;

/**
 * HU-19 – La pasarela de pagos no respondió (caída, timeout o error interno).
 * Es la ÚNICA excepción que el circuit breaker cuenta como fallo: los datos inválidos (400) o un pago
 * rechazado por el banco no indican que la pasarela esté caída.
 */
public class PasarelaNoDisponibleException extends RuntimeException {
    public PasarelaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
