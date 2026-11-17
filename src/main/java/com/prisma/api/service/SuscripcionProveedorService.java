package com.prisma.api.service;

import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoSuscripcionResponse;
import com.prisma.api.dto.SuscripcionResponse;
import com.prisma.api.security.UserPrincipal;

/**
 * Suscripción mensual Prisma Pro que el proveedor (distribuidor) paga a PRISMA por la pasarela SIMULADA.
 */
public interface SuscripcionProveedorService {

    String MENSAJE_PAGO_APROBADO = "Pago aprobado. Tu suscripción Prisma Pro quedó activa.";
    String MENSAJE_PAGO_RECHAZADO = "El pago no pudo ser procesado, intenta con otro medio";

    SuscripcionResponse obtenerEstado(UserPrincipal proveedor);

    /**
     * Paga un mes de suscripción. Pasa por el circuit breaker de la pasarela (HU-19) y se confirma con el webhook
     * firmado con HMAC-SHA256 (HU-14). Un rechazo del banco no es un error: se devuelve con estado RECHAZADO.
     */
    PagoSuscripcionResponse pagarMes(UserPrincipal proveedor, PagarPedidoRequest datos);
}
