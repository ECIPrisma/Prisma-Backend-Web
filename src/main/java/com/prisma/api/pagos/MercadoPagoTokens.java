package com.prisma.api.pagos;

/**
 * Respuesta de POST /oauth/token de Mercado Pago (credenciales del vendedor).
 *
 * @param accessToken  token para crear cobros a nombre del vendedor (APP_USR-... o TEST-...)
 * @param refreshToken token para renovar el access token (requiere scope offline_access)
 * @param publicKey    llave pública del vendedor para el checkout
 * @param userId       id de la cuenta del vendedor en Mercado Pago
 * @param expiresIn    segundos de vigencia del access token (180 días por defecto)
 * @param scope        permisos concedidos
 * @param liveMode     true = credenciales de producción
 */
public record MercadoPagoTokens(String accessToken, String refreshToken, String publicKey, Long userId,
                                Long expiresIn, String scope, boolean liveMode) {
}
