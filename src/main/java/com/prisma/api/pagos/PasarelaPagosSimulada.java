package com.prisma.api.pagos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.enums.MedioPagoEnLinea;
import com.prisma.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Pasarela de pagos SIMULADA (mockup del sandbox de Wompi).
 *
 * <p>No se conecta a ningún servicio externo ni mueve dinero. Responde de forma determinista con los
 * mismos datos de prueba que publica el sandbox de Wompi, para poder demostrar pagos aprobados y rechazados:</p>
 * <ul>
 *   <li>Nequi: 3991111111 aprueba, 3992222222 rechaza (cualquier otro celular válido aprueba).</li>
 *   <li>PSE: "Banco que rechaza" rechaza; cualquier otro banco aprueba.</li>
 *   <li>Tarjeta: 4111 1111 1111 1111 rechaza; cualquier otra tarjeta válida (Luhn) aprueba, ej. 4242 4242 4242 4242.</li>
 * </ul>
 */
@Component
public class PasarelaPagosSimulada {

    public static final String NOMBRE = "WOMPI_SANDBOX_SIMULADO";

    public static final String NEQUI_APRUEBA = "3991111111";
    public static final String NEQUI_RECHAZA = "3992222222";
    public static final String PSE_BANCO_RECHAZA = "Banco que rechaza";
    public static final String TARJETA_APRUEBA = "4242424242424242";
    public static final String TARJETA_RECHAZA = "4111111111111111";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFABETO = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final DateTimeFormatter VENCIMIENTO = DateTimeFormatter.ofPattern("MM/yy");

    private final FirmaEventosWompi firma;
    private final ObjectMapper objectMapper;

    public PasarelaPagosSimulada(FirmaEventosWompi firma, ObjectMapper objectMapper) {
        this.firma = firma;
        this.objectMapper = objectMapper;
    }

    /** Resultado de la transacción simulada. */
    public record Resultado(boolean aprobado, String transaccionId, String detalleMedio, String motivoRechazo) {}

    /** HU-14 – Webhook tal como viaja por HTTP: cuerpo crudo + encabezado X-Wompi-Signature. */
    public record NotificacionWebhook(byte[] cuerpo, String firma) {}

    /**
     * HU-14 – Notificación que la pasarela envía al terminar la transacción: el evento serializado y su firma
     * HMAC-SHA256 en el encabezado (calculada sobre el cuerpo exacto).
     */
    public NotificacionWebhook notificarTransaccion(Resultado resultado, String referencia, BigDecimal monto,
                                                    MedioPagoEnLinea medio) {
        try {
            byte[] cuerpo = objectMapper.writeValueAsBytes(eventoTransaccion(resultado, referencia, monto, medio));
            return new NotificacionWebhook(cuerpo, firma.firmar(cuerpo));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el evento de la pasarela", ex);
        }
    }

    /** Evento "transaction.updated" con el formato de Wompi (sin firma: la firma va en el encabezado). */
    public Map<String, Object> eventoTransaccion(Resultado resultado, String referencia, BigDecimal monto,
                                                 MedioPagoEnLinea medio) {
        Map<String, Object> transaccion = new LinkedHashMap<>();
        transaccion.put("id", resultado.transaccionId());
        transaccion.put("reference", referencia);
        transaccion.put("status", resultado.aprobado() ? "APPROVED" : "DECLINED");
        transaccion.put("amount_in_cents", enCentavos(monto));
        transaccion.put("currency", "COP");
        transaccion.put("payment_method_type", medio == MedioPagoEnLinea.TARJETA ? "CARD" : medio.name());
        transaccion.put("status_message", resultado.motivoRechazo());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transaction", transaccion);

        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", data);
        evento.put("environment", "test");
        evento.put("timestamp", timestamp);
        evento.put("sent_at", Instant.ofEpochSecond(timestamp).toString());
        return evento;
    }

    public static long enCentavos(BigDecimal monto) {
        return monto.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public Resultado procesar(MedioPagoEnLinea medio, PagarPedidoRequest datos) {
        if (medio == null) {
            throw invalido("Selecciona un medio de pago: NEQUI, PSE o TARJETA");
        }
        return switch (medio) {
            case NEQUI -> procesarNequi(datos);
            case PSE -> procesarPse(datos);
            case TARJETA -> procesarTarjeta(datos);
        };
    }

    private Resultado procesarNequi(PagarPedidoRequest datos) {
        String celular = soloDigitos(datos.getCelular());
        if (celular.length() != 10 || !celular.startsWith("3")) {
            throw invalido("Ingresa un celular Nequi válido de 10 dígitos");
        }
        String detalle = "Nequi ••• " + celular.substring(6);
        if (NEQUI_RECHAZA.equals(celular)) {
            return rechazado(detalle, "Nequi rechazó la transacción: fondos insuficientes");
        }
        return aprobado(detalle);
    }

    private Resultado procesarPse(PagarPedidoRequest datos) {
        String banco = datos.getBanco() == null ? "" : datos.getBanco().trim();
        if (!StringUtils.hasText(banco)) {
            throw invalido("Selecciona tu banco para pagar con PSE");
        }
        String documento = soloDigitos(datos.getDocumento());
        if (documento.length() < 5 || documento.length() > 15) {
            throw invalido("Ingresa el número de documento del titular de la cuenta");
        }
        String detalle = recortar("PSE • " + banco, 80);
        if (banco.equalsIgnoreCase(PSE_BANCO_RECHAZA)) {
            return rechazado(detalle, "El banco rechazó el débito de la cuenta");
        }
        return aprobado(detalle);
    }

    private Resultado procesarTarjeta(PagarPedidoRequest datos) {
        String numero = soloDigitos(datos.getNumeroTarjeta());
        if (numero.length() < 13 || numero.length() > 19 || !luhnValido(numero)) {
            throw invalido("El número de tarjeta no es válido");
        }
        if (!StringUtils.hasText(datos.getTitular())) {
            throw invalido("Ingresa el nombre que aparece en la tarjeta");
        }
        validarVencimiento(datos.getVencimiento());

        String detalle = franquicia(numero) + " •••• " + numero.substring(numero.length() - 4);
        if (TARJETA_RECHAZA.equals(numero)) {
            return rechazado(detalle, "Tarjeta rechazada por el banco emisor");
        }
        return aprobado(detalle);
    }

    // ---------------------------------------------------------------------------------------------

    private static void validarVencimiento(String vencimiento) {
        if (!StringUtils.hasText(vencimiento)) {
            throw invalido("Ingresa la fecha de vencimiento (MM/AA)");
        }
        try {
            YearMonth fecha = YearMonth.parse(vencimiento.trim(), VENCIMIENTO);
            if (fecha.isBefore(YearMonth.now())) {
                throw invalido("La tarjeta está vencida");
            }
        } catch (DateTimeParseException ex) {
            throw invalido("La fecha de vencimiento debe tener el formato MM/AA");
        }
    }

    static boolean luhnValido(String numero) {
        int suma = 0;
        boolean duplicar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (duplicar) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    static String franquicia(String numero) {
        if (numero.startsWith("4")) return "VISA";
        if (numero.startsWith("5") || numero.startsWith("2")) return "MASTERCARD";
        if (numero.startsWith("34") || numero.startsWith("37")) return "AMEX";
        return "TARJETA";
    }

    public static String generarCodigo(String prefijo, int largo) {
        StringBuilder sb = new StringBuilder(prefijo);
        for (int i = 0; i < largo; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static Resultado aprobado(String detalle) {
        return new Resultado(true, generarCodigo("SBX-", 12), detalle, null);
    }

    private static Resultado rechazado(String detalle, String motivo) {
        return new Resultado(false, generarCodigo("SBX-", 12), detalle, motivo);
    }

    private static String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static String recortar(String valor, int max) {
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
