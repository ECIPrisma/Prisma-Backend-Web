package com.prisma.api.pagos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.prisma.api.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente OAuth de Mercado Pago (flujo authorization code + PKCE S256), según
 * https://www.mercadopago.com.co/developers/en/docs/split-payments/additional-content/security/oauth/creation
 */
@Component
public class MercadoPagoRestOAuthClient implements MercadoPagoOAuthClient {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoRestOAuthClient.class);
    private static final String ERROR_PASARELA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String authUrl;
    private final boolean pkce;
    private final boolean modoPrueba;
    private final RestClient restClient;

    public MercadoPagoRestOAuthClient(
            @Value("${prisma.pagos.mercadopago.client-id:}") String clientId,
            @Value("${prisma.pagos.mercadopago.client-secret:}") String clientSecret,
            @Value("${prisma.pagos.mercadopago.redirect-uri:}") String redirectUri,
            @Value("${prisma.pagos.mercadopago.auth-url:https://auth.mercadopago.com/authorization}") String authUrl,
            @Value("${prisma.pagos.mercadopago.api-url:https://api.mercadopago.com}") String apiUrl,
            @Value("${prisma.pagos.mercadopago.pkce:true}") boolean pkce,
            @Value("${prisma.pagos.mercadopago.test-mode:true}") boolean modoPrueba) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.authUrl = authUrl;
        this.pkce = pkce;
        this.modoPrueba = modoPrueba;
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
    }

    @Override
    public boolean estaConfigurado() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(redirectUri);
    }

    @Override
    public String urlAutorizacion(String state, String codeChallenge) {
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(authUrl)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("platform_id", "mp")
                .queryParam("state", state)
                .queryParam("redirect_uri", redirectUri);
        if (pkce) {
            url.queryParam("code_challenge", codeChallenge).queryParam("code_challenge_method", "S256");
        }
        return url.encode().build().toUriString();
    }

    @Override
    public MercadoPagoTokens intercambiarCodigo(String code, String codeVerifier) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("client_id", clientId);
        cuerpo.put("client_secret", clientSecret);
        cuerpo.put("grant_type", "authorization_code");
        cuerpo.put("code", code);
        cuerpo.put("redirect_uri", redirectUri);
        if (pkce) {
            cuerpo.put("code_verifier", codeVerifier);
        }
        // test_token=true genera credenciales de prueba (sandbox)
        cuerpo.put("test_token", String.valueOf(modoPrueba));
        return solicitarToken(cuerpo);
    }

    @Override
    public MercadoPagoTokens renovar(String refreshToken) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("client_id", clientId);
        cuerpo.put("client_secret", clientSecret);
        cuerpo.put("grant_type", "refresh_token");
        cuerpo.put("refresh_token", refreshToken);
        return solicitarToken(cuerpo);
    }

    private MercadoPagoTokens solicitarToken(Map<String, Object> cuerpo) {
        try {
            TokenResponse respuesta = restClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(TokenResponse.class);
            if (respuesta == null || !StringUtils.hasText(respuesta.accessToken) || respuesta.userId == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, ERROR_PASARELA);
            }
            return new MercadoPagoTokens(respuesta.accessToken, respuesta.refreshToken, respuesta.publicKey,
                    respuesta.userId, respuesta.expiresIn, respuesta.scope, Boolean.TRUE.equals(respuesta.liveMode));
        } catch (RestClientException ex) {
            // No se registra el cuerpo: podría contener credenciales
            logger.warn("Mercado Pago rechazó la solicitud de token: {}", ex.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, ERROR_PASARELA);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class TokenResponse {
        @JsonProperty("access_token") String accessToken;
        @JsonProperty("refresh_token") String refreshToken;
        @JsonProperty("public_key") String publicKey;
        @JsonProperty("user_id") Long userId;
        @JsonProperty("expires_in") Long expiresIn;
        @JsonProperty("scope") String scope;
        @JsonProperty("live_mode") Boolean liveMode;
    }
}
