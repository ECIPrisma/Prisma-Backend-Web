package com.prisma.api.pagos;

import com.prisma.api.security.CifradorSecretos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoRestOAuthClientUnitTest {

    private MercadoPagoRestOAuthClient cliente(String clientId, String secret, boolean pkce) {
        return new MercadoPagoRestOAuthClient(clientId, secret, "https://prisma.app/proveedor/pagos/callback",
                "https://auth.mercadopago.com/authorization", "https://api.mercadopago.com", pkce, true);
    }

    @Test
    @DisplayName("La URL de autorización lleva los parámetros que exige Mercado Pago, incluido PKCE S256")
    void urlDeAutorizacion() {
        UriComponents url = UriComponentsBuilder.fromUriString(
                cliente("1234567890", "secreto", true).urlAutorizacion("state-abc", "challenge-xyz")).build();

        assertThat(url.getHost()).isEqualTo("auth.mercadopago.com");
        assertThat(url.getPath()).isEqualTo("/authorization");
        assertThat(url.getQueryParams().getFirst("client_id")).isEqualTo("1234567890");
        assertThat(url.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(url.getQueryParams().getFirst("platform_id")).isEqualTo("mp");
        assertThat(url.getQueryParams().getFirst("state")).isEqualTo("state-abc");
        assertThat(url.getQueryParams().getFirst("code_challenge")).isEqualTo("challenge-xyz");
        assertThat(url.getQueryParams().getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(url.getQueryParams().getFirst("redirect_uri")).contains("prisma.app");
    }

    @Test
    @DisplayName("Sin PKCE no se envían code_challenge ni code_challenge_method")
    void urlSinPkce() {
        UriComponents url = UriComponentsBuilder.fromUriString(
                cliente("1234567890", "secreto", false).urlAutorizacion("s", "c")).build();
        assertThat(url.getQueryParams()).doesNotContainKeys("code_challenge", "code_challenge_method");
    }

    @Test
    @DisplayName("Sin client id o client secret la pasarela no está configurada")
    void configuracion() {
        assertThat(cliente("", "secreto", true).estaConfigurado()).isFalse();
        assertThat(cliente("123", "", true).estaConfigurado()).isFalse();
        assertThat(cliente("123", "secreto", true).estaConfigurado()).isTrue();
    }

    @Test
    @DisplayName("El cifrado AES-GCM descifra lo que cifra y nunca repite el texto cifrado")
    void cifrado() {
        CifradorSecretos cifrador = new CifradorSecretos(Base64.getEncoder().encodeToString(new byte[32]));
        String a = cifrador.cifrar("APP_USR-token");
        String b = cifrador.cifrar("APP_USR-token");

        assertThat(a).isNotEqualTo(b).doesNotContain("APP_USR");
        assertThat(cifrador.descifrar(a)).isEqualTo("APP_USR-token");
    }

    @Test
    @DisplayName("Sin llave de cifrado la vinculación queda deshabilitada; una llave de tamaño incorrecto se rechaza")
    void llaveDeCifrado() {
        assertThat(new CifradorSecretos("").estaConfigurado()).isFalse();
        assertThatThrownBy(() -> new CifradorSecretos(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
