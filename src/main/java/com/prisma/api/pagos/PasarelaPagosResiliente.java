package com.prisma.api.pagos;

import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import com.prisma.api.exception.ApiException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-19 – Circuit breaker (Resilience4j) alrededor de la llamada a la pasarela de pagos.
 *
 * <ul>
 *   <li>CERRADO: las solicitudes pasan normalmente.</li>
 *   <li>ABIERTO: si en la ventana de las últimas N llamadas la tasa de fallos alcanza el umbral, las nuevas
 *       solicitudes responden de inmediato "Servicio de pagos temporalmente no disponible" (503), sin esperar
 *       el timeout de la pasarela.</li>
 *   <li>SEMIABIERTO: pasado el tiempo de espera, deja pasar solicitudes de prueba; si responden bien, se cierra.</li>
 * </ul>
 * Solo cuenta como fallo {@link PasarelaNoDisponibleException}. Los datos inválidos (ApiException 400) se ignoran
 * y un pago rechazado por el banco es una respuesta normal de la pasarela (éxito para el circuito).
 */
@Component
public class PasarelaPagosResiliente {

    public static final String MENSAJE_NO_DISPONIBLE = "Servicio de pagos temporalmente no disponible";
    public static final String MENSAJE_FALLA_PASARELA = "La pasarela de pagos no respondió, intenta nuevamente en unos minutos";
    public static final String NOMBRE_CIRCUITO = "pasarelaPagos";

    private static final Logger logger = LoggerFactory.getLogger(PasarelaPagosResiliente.class);

    private final PasarelaPagosSimulada pasarela;
    private final CircuitBreaker circuitBreaker;
    private final long esperaAbiertoSegundos;

    public PasarelaPagosResiliente(
            PasarelaPagosSimulada pasarela,
            @Value("${prisma.pagos.circuit-breaker.ventana-llamadas:10}") int ventanaLlamadas,
            @Value("${prisma.pagos.circuit-breaker.minimo-llamadas:5}") int minimoLlamadas,
            @Value("${prisma.pagos.circuit-breaker.umbral-fallos-porcentaje:50}") float umbralFallos,
            @Value("${prisma.pagos.circuit-breaker.espera-abierto-segundos:30}") long esperaAbiertoSegundos,
            @Value("${prisma.pagos.circuit-breaker.llamadas-prueba-semiabierto:2}") int llamadasPrueba) {
        this.pasarela = pasarela;
        this.esperaAbiertoSegundos = esperaAbiertoSegundos;
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(ventanaLlamadas)
                .minimumNumberOfCalls(minimoLlamadas)
                .failureRateThreshold(umbralFallos)
                .waitDurationInOpenState(Duration.ofSeconds(esperaAbiertoSegundos))
                .permittedNumberOfCallsInHalfOpenState(llamadasPrueba)
                .recordExceptions(PasarelaNoDisponibleException.class)
                .ignoreExceptions(ApiException.class)
                .build();
        this.circuitBreaker = CircuitBreaker.of(NOMBRE_CIRCUITO, config);
        this.circuitBreaker.getEventPublisher().onStateTransition(evento ->
                logger.warn("Circuit breaker de la pasarela de pagos: {}", evento.getStateTransition()));
    }

    /** Llama a la pasarela a través del circuito. Lanza ApiException 503 si está abierta o caída. */
    public PasarelaPagosSimulada.Resultado procesar(MedioPagoEnLinea medio, PagarPedidoRequest datos) {
        try {
            return circuitBreaker.executeSupplier(() -> pasarela.procesar(medio, datos));
        } catch (CallNotPermittedException ex) {
            // Criterio 1: circuito abierto -> respuesta inmediata, no se llama a la pasarela
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MENSAJE_NO_DISPONIBLE);
        } catch (PasarelaNoDisponibleException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, MENSAJE_FALLA_PASARELA);
        }
    }

    /** Estado actual del circuito y sus métricas (para monitoreo y la demo). */
    public Map<String, Object> estado() {
        CircuitBreaker.Metrics m = circuitBreaker.getMetrics();
        CircuitBreakerConfig c = circuitBreaker.getCircuitBreakerConfig();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("circuito", NOMBRE_CIRCUITO);
        estado.put("estado", circuitBreaker.getState().name());
        estado.put("tasaFallosPorcentaje", m.getFailureRate()); // -1 mientras no haya llamadas suficientes
        estado.put("llamadasExitosas", m.getNumberOfSuccessfulCalls());
        estado.put("llamadasFallidas", m.getNumberOfFailedCalls());
        estado.put("llamadasRechazadasPorCircuitoAbierto", m.getNumberOfNotPermittedCalls());
        estado.put("umbralFallosPorcentaje", c.getFailureRateThreshold());
        estado.put("ventanaLlamadas", c.getSlidingWindowSize());
        estado.put("minimoLlamadas", c.getMinimumNumberOfCalls());
        estado.put("esperaAbiertoSegundos", esperaAbiertoSegundos);
        estado.put("llamadasPruebaSemiabierto", c.getPermittedNumberOfCallsInHalfOpenState());
        return estado;
    }

    public CircuitBreaker.State estadoActual() {
        return circuitBreaker.getState();
    }

    /** Vuelve el circuito a CERRADO y limpia las métricas (uso administrativo y pruebas). */
    public void reiniciar() {
        circuitBreaker.reset();
    }
}
