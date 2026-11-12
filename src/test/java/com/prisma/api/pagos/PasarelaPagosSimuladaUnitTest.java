package com.prisma.api.pagos;

import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import com.prisma.api.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasarelaPagosSimuladaUnitTest {

    private final PasarelaPagosSimulada pasarela = new PasarelaPagosSimulada(new FirmaEventosWompi("secreto_de_prueba"));

    private static PagarPedidoRequest tarjeta(String numero, String vencimiento) {
        PagarPedidoRequest r = new PagarPedidoRequest();
        r.setMedio(MedioPagoEnLinea.TARJETA);
        r.setNumeroTarjeta(numero);
        r.setTitular("MARTA RODRIGUEZ");
        r.setVencimiento(vencimiento);
        return r;
    }

    @Test
    @DisplayName("Tarjetas de prueba del sandbox: 4242... aprueba y 4111... rechaza")
    void tarjetasDePrueba() {
        var aprobada = pasarela.procesar(MedioPagoEnLinea.TARJETA, tarjeta("4242 4242 4242 4242", "12/30"));
        assertThat(aprobada.aprobado()).isTrue();
        assertThat(aprobada.detalleMedio()).isEqualTo("VISA •••• 4242");

        var rechazada = pasarela.procesar(MedioPagoEnLinea.TARJETA, tarjeta("4111111111111111", "12/30"));
        assertThat(rechazada.aprobado()).isFalse();
        assertThat(rechazada.motivoRechazo()).isNotBlank();
    }

    @Test
    @DisplayName("Valida Luhn y vencimiento antes de 'enviar' a la pasarela")
    void validaTarjeta() {
        assertThatThrownBy(() -> pasarela.procesar(MedioPagoEnLinea.TARJETA, tarjeta("4242424242424241", "12/30")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> pasarela.procesar(MedioPagoEnLinea.TARJETA, tarjeta("4242424242424242", "13/30")))
                .isInstanceOf(ApiException.class);
        assertThat(PasarelaPagosSimulada.luhnValido("5555555555554444")).isTrue();
        assertThat(PasarelaPagosSimulada.franquicia("5555555555554444")).isEqualTo("MASTERCARD");
    }

    @Test
    @DisplayName("Nequi: 3991111111 aprueba y 3992222222 rechaza")
    void nequi() {
        PagarPedidoRequest r = new PagarPedidoRequest();
        r.setMedio(MedioPagoEnLinea.NEQUI);
        r.setCelular("399 111 1111");
        assertThat(pasarela.procesar(MedioPagoEnLinea.NEQUI, r).aprobado()).isTrue();
        r.setCelular(PasarelaPagosSimulada.NEQUI_RECHAZA);
        assertThat(pasarela.procesar(MedioPagoEnLinea.NEQUI, r).aprobado()).isFalse();
    }

    @Test
    @DisplayName("Los códigos generados tienen el prefijo y largo esperados")
    void codigos() {
        String codigo = PasarelaPagosSimulada.generarCodigo("SBX-", 12);
        assertThat(codigo).startsWith("SBX-").hasSize(16);
    }
}
