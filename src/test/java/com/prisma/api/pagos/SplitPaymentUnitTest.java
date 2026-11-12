package com.prisma.api.pagos;

import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-13 – Reglas del split payment y firma de los webhooks. */
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

    @Test
    @DisplayName("El evento de la pasarela trae una firma válida y cualquier alteración la invalida")
    @SuppressWarnings("unchecked")
    void firmaDelWebhook() {
        FirmaEventosWompi firma = new FirmaEventosWompi("secreto_de_prueba");
        PasarelaPagosSimulada pasarela = new PasarelaPagosSimulada(firma);
        PagarPedidoRequest nequi = new PagarPedidoRequest();
        nequi.setMedio(MedioPagoEnLinea.NEQUI);
        nequi.setCelular(PasarelaPagosSimulada.NEQUI_APRUEBA);

        var resultado = pasarela.procesar(MedioPagoEnLinea.NEQUI, nequi);
        Map<String, Object> evento = pasarela.eventoTransaccion(resultado, "PRISMA-REF-1", new BigDecimal("125000"), MedioPagoEnLinea.NEQUI);

        Map<String, Object> data = (Map<String, Object>) evento.get("data");
        Map<String, Object> signature = (Map<String, Object>) evento.get("signature");
        List<String> props = (List<String>) signature.get("properties");
        long ts = (Long) evento.get("timestamp");
        String checksum = (String) signature.get("checksum");

        assertThat(evento.get("event")).isEqualTo("transaction.updated");
        assertThat(((Map<String, Object>) data.get("transaction")).get("amount_in_cents")).isEqualTo(12_500_000L);
        assertThat(firma.esValido(data, props, ts, checksum)).isTrue();

        // Alterar el estado (DECLINED -> APPROVED o viceversa) rompe la firma
        Map<String, Object> transaccion = new HashMap<>((Map<String, Object>) data.get("transaction"));
        transaccion.put("status", "DECLINED");
        assertThat(firma.esValido(Map.of("transaction", transaccion), props, ts, checksum)).isFalse();
        // Otro secreto tampoco valida
        assertThat(new FirmaEventosWompi("otro").esValido(data, props, ts, checksum)).isFalse();
    }
}
