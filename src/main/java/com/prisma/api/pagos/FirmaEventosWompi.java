package com.prisma.api.pagos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * HU-13 – Firma de los eventos (webhooks) con el mismo esquema que usa Wompi:
 * checksum = SHA-256( valores de signature.properties concatenados + timestamp + secreto de eventos ).
 *
 * <p>Como la pasarela es SIMULADA, el secreto es un valor de pruebas configurable (WOMPI_EVENTS_SECRET).</p>
 */
@Component
public class FirmaEventosWompi {

    private final String secretoEventos;

    public FirmaEventosWompi(@Value("${prisma.pagos.wompi.events-secret:test_events_prisma_simulado}") String secretoEventos) {
        this.secretoEventos = secretoEventos;
    }

    /** Calcula el checksum del evento a partir de sus propiedades firmadas y el timestamp. */
    public String calcularChecksum(Map<String, Object> data, List<String> propiedades, long timestamp) {
        StringBuilder cadena = new StringBuilder();
        for (String propiedad : propiedades) {
            Object valor = resolver(data, propiedad);
            cadena.append(valor == null ? "" : valor.toString());
        }
        cadena.append(timestamp).append(secretoEventos);
        return sha256(cadena.toString());
    }

    /** Valida el checksum recibido comparándolo en tiempo constante. */
    public boolean esValido(Map<String, Object> data, List<String> propiedades, long timestamp, String checksum) {
        if (checksum == null || propiedades == null || propiedades.isEmpty()) {
            return false;
        }
        String esperado = calcularChecksum(data, propiedades, timestamp);
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                checksum.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** Resuelve una ruta tipo "transaction.status" dentro de data. */
    @SuppressWarnings("unchecked")
    static Object resolver(Map<String, Object> data, String ruta) {
        Object actual = data;
        for (String parte : ruta.split("\\.")) {
            if (!(actual instanceof Map)) {
                return null;
            }
            actual = ((Map<String, Object>) actual).get(parte);
        }
        return actual;
    }

    private static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
