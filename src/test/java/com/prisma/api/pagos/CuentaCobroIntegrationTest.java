package com.prisma.api.pagos;

import com.jayway.jsonpath.JsonPath;
import com.prisma.api.dto.DivisionPago;
import com.prisma.api.entity.CuentaCobroProveedor;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.SolicitudVinculacionPago;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.CuentaCobroProveedorRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.SolicitudVinculacionPagoRepository;
import com.prisma.api.security.CifradorSecretos;
import com.prisma.api.service.CuentaCobroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-12 – Vinculación de cuenta de cobro del proveedor. Solo se simula Mercado Pago;
 * la seguridad, el state, PKCE, el cifrado y la base de datos son reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CuentaCobroIntegrationTest {

    private static final String MENSAJE_FALLA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";
    private static final MercadoPagoTokens TOKENS = new MercadoPagoTokens(
            "TEST-access-token-vendedor", "TG-refresh-token-vendedor", "TEST-public-key", 123456789L,
            15552000L, "read write offline_access", false);

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private CuentaCobroProveedorRepository cuentaRepository;
    @Autowired private SolicitudVinculacionPagoRepository solicitudRepository;
    @Autowired private CifradorSecretos cifrador;
    @Autowired private CuentaCobroService cuentaCobroService;

    @MockBean private MercadoPagoOAuthClient mercadoPago;

    private String tokenFemsa;
    private Proveedor femsa;

    @BeforeEach
    void setUp() throws Exception {
        when(mercadoPago.estaConfigurado()).thenReturn(true);
        when(mercadoPago.urlAutorizacion(anyString(), anyString()))
                .thenAnswer(inv -> "https://auth.mercadopago.com/authorization?state=" + inv.getArgument(0));
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    /** Inicia el flujo como lo hace el botón de /proveedor/pagos y devuelve el state generado. */
    private String iniciarVinculacion(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/proveedores/pagos/vinculacion").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.urlAutorizacion").value(org.hamcrest.Matchers.startsWith("https://auth.mercadopago.com/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.datos.urlAutorizacion");
        return url.substring(url.indexOf("state=") + 6);
    }

    private ResultActions callback(String token, String code, String state, String error) throws Exception {
        String json = String.format("{\"code\":%s,\"state\":%s,\"error\":%s}",
                code == null ? "null" : "\"" + code + "\"",
                state == null ? "null" : "\"" + state + "\"",
                error == null ? "null" : "\"" + error + "\"");
        return mockMvc.perform(post("/api/v1/proveedores/pagos/vinculacion/callback").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------- Criterio 1: vinculación exitosa

    @Test
    @DisplayName("Proveedor sin cuenta: el estado indica no vinculada y el porcentaje que recibirá")
    void estadoSinVincular() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.disponible").value(true))
                .andExpect(jsonPath("$.datos.vinculada").value(false))
                .andExpect(jsonPath("$.datos.habilitadoPagosDivididos").value(false))
                .andExpect(jsonPath("$.datos.porcentajeProveedor").value(97.50));
    }

    @Test
    @DisplayName("Iniciar genera un state único y un code_challenge S256 del code_verifier guardado (PKCE)")
    void iniciarGeneraStateYPkce() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);

        SolicitudVinculacionPago solicitud = solicitudRepository.findByState(state).orElseThrow();
        assertThat(solicitud.getProveedorId()).isEqualTo(femsa.getId());
        assertThat(solicitud.isVigente()).isTrue();

        ArgumentCaptor<String> challenge = ArgumentCaptor.forClass(String.class);
        verify(mercadoPago).urlAutorizacion(eq(state), challenge.capture());
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(solicitud.getCodeVerifier().getBytes(StandardCharsets.US_ASCII));
        assertThat(challenge.getValue()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
    }

    @Test
    @DisplayName("Al autorizar en Mercado Pago: 200, token guardado CIFRADO y proveedor habilitado para pagos divididos")
    void vinculacionExitosa() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);
        String verifier = solicitudRepository.findByState(state).orElseThrow().getCodeVerifier();
        when(mercadoPago.intercambiarCodigo("TG-codigo-autorizacion", verifier)).thenReturn(TOKENS);

        callback(tokenFemsa, "TG-codigo-autorizacion", state, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vinculada").value(true))
                .andExpect(jsonPath("$.datos.habilitadoPagosDivididos").value(true))
                .andExpect(jsonPath("$.datos.cuentaExternaId").value("123456789"))
                .andExpect(jsonPath("$.datos.accessTokenCifrado").doesNotExist());

        CuentaCobroProveedor cuenta = cuentaRepository.findByProveedorId(femsa.getId()).orElseThrow();
        assertThat(cuenta.getAccessTokenCifrado()).isNotEqualTo(TOKENS.accessToken());
        assertThat(cifrador.descifrar(cuenta.getAccessTokenCifrado())).isEqualTo(TOKENS.accessToken());
        assertThat(cifrador.descifrar(cuenta.getRefreshTokenCifrado())).isEqualTo(TOKENS.refreshToken());
        assertThat(solicitudRepository.findByState(state).orElseThrow().isUsada()).isTrue();
    }

    @Test
    @DisplayName("Con comisión de 3.5% el proveedor recibe el 96.5% de cada pedido")
    void divisionDelPago() throws Exception {
        femsa.setComisionPorcentaje(new BigDecimal("3.50"));
        proveedorRepository.save(femsa);
        String state = iniciarVinculacion(tokenFemsa);
        when(mercadoPago.intercambiarCodigo(anyString(), anyString())).thenReturn(TOKENS);
        callback(tokenFemsa, "code", state, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.porcentajeProveedor").value(96.50));

        DivisionPago division = cuentaCobroService.calcularDivision(femsa.getId(), new BigDecimal("100000"));

        assertThat(division.comisionPrisma()).isEqualByComparingTo("3500.00");
        assertThat(division.montoProveedor()).isEqualByComparingTo("96500.00");
        assertThat(division.porcentajeProveedor()).isEqualByComparingTo("96.50");
    }

    // ------------------------------------------------------- Criterio 2: autorización rechazada

    @Test
    @DisplayName("Si el proveedor cancela o rechaza en Mercado Pago: no se vincula nada y se muestra el mensaje")
    void autorizacionRechazada() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);

        callback(tokenFemsa, null, state, "access_denied")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_FALLA));

        assertThat(cuentaRepository.existsByProveedorId(femsa.getId())).isFalse();
        verify(mercadoPago, never()).intercambiarCodigo(anyString(), anyString());
        // El intento queda consumido: no se puede reintentar con el mismo state
        assertThat(solicitudRepository.findByState(state).orElseThrow().isUsada()).isTrue();
    }

    @Test
    @DisplayName("Si Mercado Pago rechaza el canje del code: no se vincula nada")
    void pasarelaRechazaElCodigo() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);
        when(mercadoPago.intercambiarCodigo(anyString(), anyString()))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, MENSAJE_FALLA));

        callback(tokenFemsa, "code-vencido", state, null)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_FALLA));

        assertThat(cuentaRepository.existsByProveedorId(femsa.getId())).isFalse();
    }

    @Test
    @DisplayName("Seguridad: un state inventado, reutilizado o de otro proveedor no vincula ninguna cuenta")
    void stateInvalido() throws Exception {
        when(mercadoPago.intercambiarCodigo(anyString(), anyString())).thenReturn(TOKENS);

        callback(tokenFemsa, "code", "state-inventado", null).andExpect(status().isBadRequest());

        // State iniciado por Postobón no sirve para FEMSA (anti-CSRF)
        String tokenPostobon = login("ventas@postobon.com.co", "postobon123");
        String statePostobon = iniciarVinculacion(tokenPostobon);
        callback(tokenFemsa, "code", statePostobon, null).andExpect(status().isBadRequest());

        // State vencido
        String state = iniciarVinculacion(tokenFemsa);
        SolicitudVinculacionPago solicitud = solicitudRepository.findByState(state).orElseThrow();
        solicitud.setExpiraEn(OffsetDateTime.now().minusMinutes(1));
        solicitudRepository.save(solicitud);
        callback(tokenFemsa, "code", state, null).andExpect(status().isBadRequest());

        assertThat(cuentaRepository.existsByProveedorId(femsa.getId())).isFalse();
        verify(mercadoPago, never()).intercambiarCodigo(anyString(), anyString());
    }

    // ------------------------------------------------------- Otras reglas

    @Test
    @DisplayName("Con la cuenta ya vinculada no se puede iniciar otra vinculación (409); al desvincular sí")
    void yaVinculadaYDesvincular() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);
        when(mercadoPago.intercambiarCodigo(anyString(), anyString())).thenReturn(TOKENS);
        callback(tokenFemsa, "code", state, null).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/proveedores/pagos/vinculacion").header("Authorization", tokenFemsa))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vinculada").value(false));
    }

    @Test
    @DisplayName("Un tendero no puede acceder a las cuentas de cobro (403)")
    void tenderoNoAccede() throws Exception {
        String tokenTendero = login("dona.marta@labendicion.com", "admin123");
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenTendero))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin credenciales de Mercado Pago en el servidor la vinculación responde 503")
    void pasarelaNoConfigurada() throws Exception {
        when(mercadoPago.estaConfigurado()).thenReturn(false);
        mockMvc.perform(post("/api/v1/proveedores/pagos/vinculacion").header("Authorization", tokenFemsa))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("El access token se renueva automáticamente si está por vencer (y se guarda el nuevo refresh token)")
    void renovacionDelToken() throws Exception {
        String state = iniciarVinculacion(tokenFemsa);
        when(mercadoPago.intercambiarCodigo(anyString(), anyString())).thenReturn(TOKENS);
        callback(tokenFemsa, "code", state, null).andExpect(status().isOk());

        CuentaCobroProveedor cuenta = cuentaRepository.findByProveedorId(femsa.getId()).orElseThrow();
        cuenta.setTokenExpiraEn(OffsetDateTime.now().plusDays(1));
        cuentaRepository.save(cuenta);
        when(mercadoPago.renovar(TOKENS.refreshToken())).thenReturn(new MercadoPagoTokens(
                "TEST-access-renovado", "TG-refresh-renovado", "TEST-public-key", 123456789L, 15552000L, "read write offline_access", false));

        assertThat(cuentaCobroService.obtenerAccessTokenVigente(femsa.getId())).isEqualTo("TEST-access-renovado");
        CuentaCobroProveedor actualizada = cuentaRepository.findByProveedorId(femsa.getId()).orElseThrow();
        assertThat(cifrador.descifrar(actualizada.getRefreshTokenCifrado())).isEqualTo("TG-refresh-renovado");
    }
}
