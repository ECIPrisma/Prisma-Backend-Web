package com.prisma.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Estado de la suscripción Prisma Pro del proveedor autenticado, con su historial de pagos.
 *
 * @param estado SIN_SUSCRIPCION (nunca ha pagado), ACTIVA (vigente hasta hoy o después) o VENCIDA
 */
public record SuscripcionResponse(
        String plan,
        String nombrePlan,
        BigDecimal precioMensual,
        BigDecimal valorNeto,
        BigDecimal valorIva,
        List<String> beneficios,
        String estado,
        LocalDate vigenteHasta,
        long diasRestantes,
        boolean simulada,
        List<PagoSuscripcionResponse> pagos) {
}
