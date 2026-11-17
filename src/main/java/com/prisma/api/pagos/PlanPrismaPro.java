package com.prisma.api.pagos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Plan de suscripción mensual "Prisma Pro" que pagan los distribuidores (proveedores) a PRISMA.
 *
 * <p>Modelo financiero: suscripción mensual con copiloto de IA. Para el MVP solo el distribuidor paga en línea;
 * los tenderos siguen pagando sus pedidos como siempre (contra entrega).</p>
 * El precio incluye IVA (19%): neto = precio / 1.19.
 */
public final class PlanPrismaPro {

    public static final String CODIGO = "DISTRIBUIDOR_PRO";
    public static final String NOMBRE = "Prisma Pro Distribuidor";
    public static final BigDecimal PRECIO_MENSUAL_CON_IVA = new BigDecimal("120000.00");
    public static final BigDecimal TASA_IVA = new BigDecimal("0.19");
    public static final List<String> BENEFICIOS = List.of(
            "Copiloto de IA para tu operación de distribución",
            "Catálogo y publicidad visibles para las tiendas de tu zona",
            "Analítica económica avanzada (Modo Pro)",
            "Despachos, rutas y seguimiento de flota");

    private PlanPrismaPro() {}

    /** Valor sin IVA, redondeado al centavo. */
    public static BigDecimal valorNeto(BigDecimal precioConIva) {
        return precioConIva.divide(BigDecimal.ONE.add(TASA_IVA), 2, RoundingMode.HALF_UP);
    }

    /** IVA = precio − neto (las dos partes suman exactamente el precio). */
    public static BigDecimal valorIva(BigDecimal precioConIva) {
        return precioConIva.subtract(valorNeto(precioConIva));
    }
}
