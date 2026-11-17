package com.prisma.api.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.prisma.api.pagos.FirmaEventosWompi.Validacion.*;
import static org.assertj.core.api.Assertions.assertThat;

/** HU-14 – Firma HMAC-SHA256 del encabezado de los webhooks. */
class FirmaWebhookUnitTest {

    private static final long AHORA = 1_800_000_000L;
    private final FirmaEventosWompi firma = new FirmaEventosWompi("secreto_de_prueba");
    private final byte[] cuerpo = "{\"event\":\"transaction.updated\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("Una firma generada con el secreto correcto es válida")
    void firmaValida() {
        String encabezado = firma.firmar(cuerpo, AHORA);
        assertThat(encabezado).matches("t=1800000000,v1=[0-9a-f]{64}");
        assertThat(firma.validar(encabezado, cuerpo, AHORA)).isEqualTo(VALIDA);
    }

    @Test
    @DisplayName("Alterar un solo byte del cuerpo invalida la firma (manipulación)")
    void cuerpoManipulado() {
        String encabezado = firma.firmar(cuerpo, AHORA);
        byte[] alterado = Arrays.copyOf(cuerpo, cuerpo.length);
        alterado[5] = (byte) (alterado[5] + 1);
        assertThat(firma.validar(encabezado, alterado, AHORA)).isEqualTo(NO_COINCIDE);
    }

    @Test
    @DisplayName("Otro secreto, otra marca de tiempo o una firma inventada no son válidos")
    void firmasInvalidas() {
        String encabezado = firma.firmar(cuerpo, AHORA);
        assertThat(new FirmaEventosWompi("secreto_de_un_atacante").validar(encabezado, cuerpo, AHORA)).isEqualTo(NO_COINCIDE);
        // cambiar t sin recalcular el HMAC rompe la firma (el timestamp también está firmado)
        String otroT = encabezado.replace("t=" + AHORA, "t=" + (AHORA + 10));
        assertThat(firma.validar(otroT, cuerpo, AHORA)).isEqualTo(NO_COINCIDE);
        assertThat(firma.validar("t=" + AHORA + ",v1=" + "0".repeat(64), cuerpo, AHORA)).isEqualTo(NO_COINCIDE);
    }

    @Test
    @DisplayName("Encabezado ausente, mal formado o vencido (reenvío de un webhook viejo)")
    void encabezadosNoAceptados() {
        assertThat(firma.validar(null, cuerpo, AHORA)).isEqualTo(AUSENTE);
        assertThat(firma.validar("  ", cuerpo, AHORA)).isEqualTo(AUSENTE);
        assertThat(firma.validar("abc", cuerpo, AHORA)).isEqualTo(MALFORMADA);
        assertThat(firma.validar("t=xyz,v1=abc", cuerpo, AHORA)).isEqualTo(MALFORMADA);
        String viejo = firma.firmar(cuerpo, AHORA - FirmaEventosWompi.TOLERANCIA_SEGUNDOS - 1);
        assertThat(firma.validar(viejo, cuerpo, AHORA)).isEqualTo(EXPIRADA);
    }

    @Test
    @DisplayName("La pasarela simulada envía el webhook con una firma que el sistema acepta")
    void notificacionDeLaPasarelaEsValida() {
        PasarelaPagosSimulada pasarela = new PasarelaPagosSimulada(firma, new ObjectMapper());
        PagarPedidoRequest nequi = new PagarPedidoRequest();
        nequi.setMedio(MedioPagoEnLinea.NEQUI);
        nequi.setCelular(PasarelaPagosSimulada.NEQUI_APRUEBA);

        var resultado = pasarela.procesar(MedioPagoEnLinea.NEQUI, nequi);
        var webhook = pasarela.notificarTransaccion(resultado, "PRISMA-REF-1", new BigDecimal("125000"), MedioPagoEnLinea.NEQUI);

        assertThat(firma.validar(webhook.firma(), webhook.cuerpo())).isEqualTo(VALIDA);
        String json = new String(webhook.cuerpo(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"transaction.updated\"", "\"APPROVED\"", "\"amount_in_cents\":12500000");
        // PCI-DSS: el evento nunca lleva datos de tarjeta
        assertThat(json).doesNotContain("numeroTarjeta", "vencimiento", "titular");
    }
}
