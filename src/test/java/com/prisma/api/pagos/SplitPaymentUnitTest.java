package com.prisma.api.pagos;

import com.prisma.api.dto.DivisionPago;
import com.prisma.api.enums.MedioPagoEnLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-13 – Reglas del split payment. */
class SplitPaymentUnitTest {

    @Test
    @DisplayName("Ccomision = V × 0.035 y el proveedor recibe el 96.5% restante")
    void divisionDeUnPedido() {
        DivisionPago d = ReglasSplitPayment.dividir(new BigDecimal("125000"));
        assertThat(d.comisionPrisma()).isEqualByComparingTo("4375.00");
        assertThat(d.montoProveedor()).isEqualByComparingTo("120625.00");
        assertThat(d.porcentajeProveedor()).isEqualByComparingTo("96.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "99.99", "12345.67", "88500", "333333.33", "0"})
    @DisplayName("La división es atómica: comisión + proveedor = total exacto, sin perder centavos")
    void divisionSiempreCuadra(String total) {
        BigDecimal v = new BigDecimal(total);
        DivisionPago d = ReglasSplitPayment.dividir(v);
        assertThat(d.comisionPrisma().add(d.montoProveedor())).isEqualByComparingTo(v);
    }

    @Test
    @DisplayName("Tarifa Wompi por medio (independiente de la comisión de la plataforma)")
    void tarifaPasarela() {
        BigDecimal v = new BigDecimal("125000");
        assertThat(ReglasSplitPayment.tarifaPasarela(MedioPagoEnLinea.PSE, v)).isEqualByComparingTo("2762.50");     // 1.49% + 900
        assertThat(ReglasSplitPayment.tarifaPasarela(MedioPagoEnLinea.NEQUI, v)).isEqualByComparingTo("2237.50");   // 1.79%
        assertThat(ReglasSplitPayment.tarifaPasarela(MedioPagoEnLinea.TARJETA, v)).isEqualByComparingTo("4012.50"); // 2.65% + 700
    }

}
