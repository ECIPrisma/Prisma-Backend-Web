package com.prisma.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.FirmaEventosWompi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * HU-14 – Puerta de entrada de los webhooks de pago.
 *
 * <ol>
 *   <li>Valida la firma HMAC-SHA256 del encabezado sobre el cuerpo crudo, ANTES de leer su contenido.</li>
 *   <li>Si no es válida: registra el intento en el log de seguridad y responde 401 sin tocar ningún pedido.</li>
 *   <li>Si es válida: interpreta el evento y lo procesa (pago confirmado + aviso al proveedor).</li>
 * </ol>
 * Este servicio NO es transaccional a propósito: el registro de seguridad debe sobrevivir al rechazo.
 */
@Service
public class WebhookPagosService {

    public static final String MENSAJE_FIRMA_INVALIDA = "Notificación rechazada: firma inválida";

    private final FirmaEventosWompi firma;
    private final ConfirmacionPagoService confirmacionPagoService;
    private final RegistroSeguridadService registroSeguridad;
    private final ObjectMapper objectMapper;

    public WebhookPagosService(FirmaEventosWompi firma, ConfirmacionPagoService confirmacionPagoService,
                               RegistroSeguridadService registroSeguridad, ObjectMapper objectMapper) {
        this.firma = firma;
        this.confirmacionPagoService = confirmacionPagoService;
        this.registroSeguridad = registroSeguridad;
        this.objectMapper = objectMapper;
    }

    public ConfirmacionPagoService.Resultado recibir(byte[] cuerpo, String encabezadoFirma, String ipOrigen, String userAgent) {
        byte[] datos = cuerpo == null ? new byte[0] : cuerpo;
        FirmaEventosWompi.Validacion validacion = firma.validar(encabezadoFirma, datos);
        if (validacion != FirmaEventosWompi.Validacion.VALIDA) {
            registroSeguridad.registrarWebhookRechazado(validacion.descripcion(), ipOrigen, userAgent, datos.length);
            throw new ApiException(HttpStatus.UNAUTHORIZED, MENSAJE_FIRMA_INVALIDA);
        }

        Map<String, Object> evento;
        try {
            evento = objectMapper.readValue(datos, new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El cuerpo del evento no es un JSON válido");
        }
        return confirmacionPagoService.procesarEvento(evento);
    }
}
