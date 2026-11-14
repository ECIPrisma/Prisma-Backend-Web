package com.prisma.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.entity.Usuario;
import com.prisma.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "prisma.totp.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Login con credenciales válidas retorna token JWT y rol correcto")
    void testLoginExitoso() throws Exception {
        LoginRequest req = new LoginRequest("admin@prisma.com.co", "admin123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(jsonPath("$.datos.email").value("admin@prisma.com.co"))
                .andExpect(jsonPath("$.datos.rol").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta retorna 401 Unauthorized")
    void testLoginPasswordInvalido() throws Exception {
        LoginRequest req = new LoginRequest("admin@prisma.com.co", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login con correo inexistente retorna 401 Unauthorized")
    void testLoginUsuarioInexistente() throws Exception {
        LoginRequest req = new LoginRequest("noexiste@empresa.com", "cualquiera123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login con payload inválido retorna 400 Bad Request")
    void testLoginCamposVacios() throws Exception {
        LoginRequest req = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /auth/me con token válido retorna el perfil del usuario autenticado")
    void testMeConTokenValido() throws Exception {
        LoginRequest loginReq = new LoginRequest("admin@prisma.com.co", "admin123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.datos.token");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.username").value("admin@prisma.com.co"));
    }

    @Test
    @DisplayName("GET /auth/me sin token retorna datos nulos de usuario")
    void testMeSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").doesNotExist());
    }

    @Test
    @DisplayName("El usuario puede configurar TOTP y el login luego exige un código")
    void testConfigurarTotpYExigirCodigoEnLogin() throws Exception {
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin@prisma.com.co", "admin123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.datos.token");

        String setup = mockMvc.perform(post("/api/v1/auth/totp/setup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.datos.secret");
        String code = currentTotpCode(secret);

        mockMvc.perform(post("/api/v1/auth/totp/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("codigo", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.enabled").value(true));

        Usuario admin = usuarioRepository.findByEmail("admin@prisma.com.co").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(admin.isTotpEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(admin.getTotpSecretEncrypted()).doesNotContain(secret);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin@prisma.com.co", "admin123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Se requiere un código TOTP de 6 dígitos."));
    }

    private String currentTotpCode(String base32Secret) throws Exception {
        byte[] key = decodeBase32(base32Secret);
        long step = System.currentTimeMillis() / 1000 / 30;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }

    private byte[] decodeBase32(String secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] result = new byte[secret.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char character : secret.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(character);
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
