package com.prisma.api.pagos;

import com.prisma.api.dto.DivisionPago;
import com.prisma.api.enums.MedioPagoEnLinea;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * HU-13 – Reglas del pago dividido (split payment).
 *
 * <ul>
 *   <li>Comisión de la plataforma: 3.5% fijo sobre el valor del pedido (Ccomision = Vtotal × 0.035).</li>
 *   <li>El 96.5% restante es del proveedor. Se calcula como Vtotal − Ccomision para que las dos partes
 *       sumen exactamente el total (sin pesos perdidos por redondeo).</li>
 *   <li>La tarifa de Wompi por procesamiento es independiente de la comisión de la plataforma y depende del medio:
 *       PSE desde 1.49% + COP $900, Nequi desde 1.79%, tarjetas desde 2.65% + COP $700.</li>
 * </ul>
 */
public final class ReglasSplitPayment {

    public static final BigDecimal PORCENTAJE_COMISION = new BigDecimal("3.50");
    public static final BigDecimal PORCENTAJE_PROVEEDOR = new BigDecimal("96.50");
    public static final BigDecimal TASA_COMISION = new BigDecimal("0.035");

    private static final BigDecimal CIEN = new BigDecimal("100");

    private ReglasSplitPayment() {}

    /** Ccomision = Vtotal × 0.035, redondeado al centavo. */
    public static BigDecimal comisionPlataforma(BigDecimal total) {
        return valor(total).multiply(TASA_COMISION).setScale(2, RoundingMode.HALF_UP);
    }

    /** División atómica: comisión + proveedor = total, siempre. */
    public static DivisionPago dividir(BigDecimal total) {
        BigDecimal v = valor(total).setScale(2, RoundingMode.HALF_UP);
        BigDecimal comision = comisionPlataforma(v);
        BigDecimal proveedor = v.subtract(comision);
        if (comision.add(proveedor).compareTo(v) != 0) {
            throw new IllegalStateException("La división del pago no cuadra con el total");
        }
        return new DivisionPago(v, comision, proveedor, PORCENTAJE_PROVEEDOR);
    }

    /** Tarifa (simulada) que Wompi descuenta por procesar el pago; no hace parte de la comisión de PRISMA. */
    public static BigDecimal tarifaPasarela(MedioPagoEnLinea medio, BigDecimal total) {
        BigDecimal v = valor(total);
        BigDecimal tarifa = switch (medio) {
            case PSE -> porcentaje(v, "1.49").add(new BigDecimal("900"));
            case NEQUI -> porcentaje(v, "1.79");
            case TARJETA -> porcentaje(v, "2.65").add(new BigDecimal("700"));
        };
        return tarifa.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal porcentaje(BigDecimal v, String pct) {
        return v.multiply(new BigDecimal(pct)).divide(CIEN, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal valor(BigDecimal total) {
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("El valor del pedido debe ser mayor o igual a cero");
        }
        return total;
    }
}
