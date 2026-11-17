package com.prisma.api.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import com.prisma.api.exception.ApiException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-19 – Circuit breaker de la pasarela de pagos.
 * Circuito pequeño para la prueba: ventana de 4 llamadas, 50% de fallos, 1 s abierto, 1 llamada de prueba.
 */
class CircuitBreakerPasarelaUnitTest {

    private final PasarelaPagosSimulada pasarela =
            new PasarelaPagosSimulada(new FirmaEventosWompi("secreto_de_prueba"), new ObjectMapper());
    private final PasarelaPagosResiliente resiliente = new PasarelaPagosResiliente(pasarela, 4, 4, 50f, 1, 1);

    private static PagarPedidoRequest nequi(String celular) {
        PagarPedidoRequest r = new PagarPedidoRequest();
        r.setMedio(MedioPagoEnLinea.NEQUI);
        r.setCelular(celular);
        return r;
    }

    private ApiException pagarYFallar(String celular) {
        return catchThrowableOfType(() -> resiliente.procesar(MedioPagoEnLinea.NEQUI, nequi(celular)), ApiException.class);
    }

    private void abrirCircuito() {
        for (int i = 0; i < 4; i++) {
            ApiException ex = pagarYFallar(PasarelaPagosSimulada.NEQUI_PASARELA_CAIDA);
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getMessage()).isEqualTo(PasarelaPagosResiliente.MENSAJE_FALLA_PASARELA);
        }
    }

    @Test
    @DisplayName("Criterio 1: al superar el umbral de errores el circuito se abre y responde de inmediato")
    void abreElCircuito() {
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.CLOSED);
        abrirCircuito();
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.OPEN);

        // Aun con datos que aprobarían, no se llama a la pasarela: 503 inmediato con el mensaje de la HU
        long inicio = System.nanoTime();
        ApiException ex = pagarYFallar(PasarelaPagosSimulada.NEQUI_APRUEBA);
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getMessage()).isEqualTo("Servicio de pagos temporalmente no disponible");
        assertThat(ms).isLessThan(500);
        assertThat(((Number) resiliente.estado().get("llamadasRechazadasPorCircuitoAbierto")).longValue()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Criterio 2: pasado el tiempo de espera, una llamada de prueba exitosa (half-open) cierra el circuito")
    void recuperacionAutomatica() throws InterruptedException {
        abrirCircuito();
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(1_200); // espera configurada: 1 s

        var resultado = resiliente.procesar(MedioPagoEnLinea.NEQUI, nequi(PasarelaPagosSimulada.NEQUI_APRUEBA));
        assertThat(resultado.aprobado()).isTrue();
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Y los pagos vuelven a procesarse con normalidad
        assertThat(resiliente.procesar(MedioPagoEnLinea.NEQUI, nequi(PasarelaPagosSimulada.NEQUI_APRUEBA)).aprobado()).isTrue();
    }

    @Test
    @DisplayName("Si la llamada de prueba (half-open) vuelve a fallar, el circuito se abre de nuevo")
    void pruebaFallidaReabre() throws InterruptedException {
        abrirCircuito();
        Thread.sleep(1_200);
        pagarYFallar(PasarelaPagosSimulada.NEQUI_PASARELA_CAIDA);
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("Datos inválidos y pagos rechazados por el banco NO abren el circuito (la pasarela sí respondió)")
    void erroresDelUsuarioNoCuentan() {
        for (int i = 0; i < 6; i++) {
            ApiException ex = pagarYFallar("123"); // celular inválido -> 400
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (int i = 0; i < 6; i++) {
            assertThat(resiliente.procesar(MedioPagoEnLinea.NEQUI, nequi(PasarelaPagosSimulada.NEQUI_RECHAZA)).aprobado()).isFalse();
        }
        assertThat(resiliente.estadoActual()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
