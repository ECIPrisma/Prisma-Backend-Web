package com.prisma.api.pagos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HU-14 – Firma criptográfica HMAC-SHA256 del encabezado de los webhooks de pago.
 *
 * <p>Encabezado: {@code X-Wompi-Signature: t=<epoch segundos>,v1=<hex HMAC-SHA256>}, donde el HMAC se calcula con el
 * secreto de eventos sobre {@code "<t>." + cuerpo crudo}. Se valida ANTES de leer el contenido del cuerpo.</p>
 *
 * <ul>
 *   <li>Comparación en tiempo constante (evita ataques de temporización).</li>
 *   <li>Tolerancia de {@value #TOLERANCIA_SEGUNDOS} s sobre el timestamp (evita reenviar un webhook capturado).</li>
 * </ul>
 * Como la pasarela es SIMULADA, el secreto es un valor de pruebas configurable (WOMPI_EVENTS_SECRET).
 */
@Component
public class FirmaEventosWompi {

    public static final String ENCABEZADO = "X-Wompi-Signature";
    public static final long TOLERANCIA_SEGUNDOS = 300;
    private static final String ALGORITMO = "HmacSHA256";

    public enum Validacion {
        VALIDA("firma válida"),
        AUSENTE("no se envió el encabezado " + ENCABEZADO),
        MALFORMADA("el encabezado de firma no tiene el formato t=...,v1=..."),
        EXPIRADA("el timestamp de la firma está fuera de la ventana permitida"),
        NO_COINCIDE("la firma HMAC-SHA256 no coincide con la esperada");

        private final String descripcion;
        Validacion(String descripcion) { this.descripcion = descripcion; }
        public String descripcion() { return descripcion; }
    }

    private final byte[] secreto;

    public FirmaEventosWompi(@Value("${prisma.pagos.wompi.events-secret:test_events_prisma_simulado}") String secreto) {
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
    }

    /** Genera el valor del encabezado para un cuerpo, como lo haría la pasarela al enviar el webhook. */
    public String firmar(byte[] cuerpo, long timestamp) {
        return "t=" + timestamp + ",v1=" + hmacHex(timestamp, cuerpo);
    }

    public String firmar(byte[] cuerpo) {
        return firmar(cuerpo, Instant.now().getEpochSecond());
    }

    public Validacion validar(String encabezado, byte[] cuerpo) {
        return validar(encabezado, cuerpo, Instant.now().getEpochSecond());
    }

    public Validacion validar(String encabezado, byte[] cuerpo, long ahora) {
        if (encabezado == null || encabezado.isBlank()) {
            return Validacion.AUSENTE;
        }
        Long timestamp = null;
        String firma = null;
        for (String parte : encabezado.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) {
                try {
                    timestamp = Long.parseLong(kv[1].trim());
                } catch (NumberFormatException ex) {
                    return Validacion.MALFORMADA;
                }
            } else if ("v1".equals(kv[0])) {
                firma = kv[1].trim().toLowerCase();
            }
        }
        if (timestamp == null || firma == null || firma.isEmpty()) {
            return Validacion.MALFORMADA;
        }
        if (Math.abs(ahora - timestamp) > TOLERANCIA_SEGUNDOS) {
            return Validacion.EXPIRADA;
        }
        byte[] esperada = hmacHex(timestamp, cuerpo == null ? new byte[0] : cuerpo).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperada, firma.getBytes(StandardCharsets.US_ASCII))
                ? Validacion.VALIDA : Validacion.NO_COINCIDE;
    }

    private String hmacHex(long timestamp, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(cuerpo));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", ex);
        }
    }
}
