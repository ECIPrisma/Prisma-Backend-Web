package com.prisma.api.pagos;

/**
 * Llamadas OAuth a Mercado Pago. Separado en una interfaz para simularlo en las pruebas.
 */
public interface MercadoPagoOAuthClient {

    /** Canjea el authorization code devuelto en el redirect por las credenciales del vendedor. */
    MercadoPagoTokens intercambiarCodigo(String code, String codeVerifier);

    /** Renueva el access token del vendedor con su refresh token. */
    MercadoPagoTokens renovar(String refreshToken);

    /** URL a la que se redirige al proveedor para autorizar a PRISMA en Mercado Pago. */
    String urlAutorizacion(String state, String codeChallenge);

    /** true si hay client id, client secret y redirect uri configurados. */
    boolean estaConfigurado();
}
