package com.prisma.api.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el cliente HTTP real contra un servidor local que imita POST /oauth/token de Mercado Pago.
 */
class MercadoPagoTokenHttpUnitTest {

    private HttpServer servidor;
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private volatile int codigoRespuesta = 200;
    private volatile String respuesta;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/oauth/token", intercambio -> {
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigoRespuesta, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private MercadoPagoRestOAuthClient cliente() {
        return new MercadoPagoRestOAuthClient("APP-123", "secreto-app", "https://prisma.app/proveedor/pagos/callback",
                "https://auth.mercadopago.com/authorization", "http://127.0.0.1:" + servidor.getAddress().getPort(), true, true);
    }

    @Test
    @DisplayName("Canje del code: envía el JSON esperado por Mercado Pago y lee su respuesta en snake_case")
    void intercambiarCodigo() throws Exception {
        respuesta = """
                {"access_token":"TEST-123-abc","token_type":"bearer","expires_in":15552000,
                 "scope":"read write offline_access","user_id":987654,"refresh_token":"TG-xyz",
                 "public_key":"TEST-pk","live_mode":false}
                """;

        MercadoPagoTokens tokens = cliente().intercambiarCodigo("TG-code", "verificador-pkce");

        assertThat(tokens.accessToken()).isEqualTo("TEST-123-abc");
        assertThat(tokens.refreshToken()).isEqualTo("TG-xyz");
        assertThat(tokens.userId()).isEqualTo(987654L);
        assertThat(tokens.expiresIn()).isEqualTo(15552000L);
        assertThat(tokens.liveMode()).isFalse();

        JsonNode enviado = new ObjectMapper().readTree(cuerpoRecibido.get());
        assertThat(enviado.get("grant_type").asText()).isEqualTo("authorization_code");
        assertThat(enviado.get("code").asText()).isEqualTo("TG-code");
        assertThat(enviado.get("code_verifier").asText()).isEqualTo("verificador-pkce");
        assertThat(enviado.get("client_id").asText()).isEqualTo("APP-123");
        assertThat(enviado.get("redirect_uri").asText()).isEqualTo("https://prisma.app/proveedor/pagos/callback");
        assertThat(enviado.get("test_token").asText()).isEqualTo("true");
    }

    @Test
    @DisplayName("Renovación: usa grant_type=refresh_token")
    void renovar() throws Exception {
        respuesta = "{\"access_token\":\"TEST-nuevo\",\"user_id\":1,\"refresh_token\":\"TG-nuevo\",\"expires_in\":100}";

        assertThat(cliente().renovar("TG-viejo").accessToken()).isEqualTo("TEST-nuevo");

        JsonNode enviado = new ObjectMapper().readTree(cuerpoRecibido.get());
        assertThat(enviado.get("grant_type").asText()).isEqualTo("refresh_token");
        assertThat(enviado.get("refresh_token").asText()).isEqualTo("TG-viejo");
    }

    @Test
    @DisplayName("Si Mercado Pago responde invalid_grant (code vencido o redirect_uri distinto): 502 con el mensaje de la HU")
    void errorDeLaPasarela() {
        codigoRespuesta = 400;
        respuesta = "{\"error\":\"invalid_grant\",\"message\":\"invalid_grant\"}";

        assertThatThrownBy(() -> cliente().intercambiarCodigo("TG-vencido", "v"))
                .isInstanceOf(ApiException.class)
                .hasMessage("No se pudo vincular tu cuenta de cobro, intenta nuevamente");
    }
}
