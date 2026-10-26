package com.prisma.api.auth;

import com.prisma.api.entity.Tienda;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.Rol;
import com.prisma.api.support.IntegrationTestBase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-01 Registro y HU-02 Login: qué debe y qué NO debe pasar. */
class AuthIntegrationTest extends IntegrationTestBase {

    @Value("${prisma.jwt.secret}")
    private String jwtSecret;

    // ============================================================ LOGIN
    @Test
    @DisplayName("Login correcto devuelve JWT, rol y datos del usuario, sin exponer la contraseña")
    void loginCorrecto() throws Exception {
        String respuesta = postReq("/api/v1/auth/login", null, Map.of("email", MARTA, "password", MARTA_PASS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(jsonPath("$.datos.email").value(MARTA))
                .andExpect(jsonPath("$.datos.rol").value("ROLE_TENDERO"))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain(MARTA_PASS).doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    @DisplayName("Login del repartidor trae su proveedorId (necesario para el aislamiento multi-tenant)")
    void loginRepartidorTraeProveedor() throws Exception {
        postReq("/api/v1/auth/login", null, Map.of("email", JUAN, "password", JUAN_PASS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("ROLE_REPARTIDOR"))
                .andExpect(jsonPath("$.datos.proveedorId").value(femsa().getId()));
    }

    @Test
    @DisplayName("Contraseña incorrecta -> 401")
    void loginPasswordIncorrecta() throws Exception {
        postReq("/api/v1/auth/login", null, Map.of("email", MARTA, "password", "incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.exitoso").value(false))
                .andExpect(jsonPath("$.datos").doesNotExist());
    }

    @Test
    @DisplayName("Email inexistente -> 401 con el MISMO mensaje que contraseña incorrecta (no revela qué correos existen)")
    void loginEmailInexistenteNoRevelaCuentas() throws Exception {
        String conPasswordMala = leer(postReq("/api/v1/auth/login", null, Map.of("email", MARTA, "password", "x")))
                .path("mensaje").asText();
        postReq("/api/v1/auth/login", null, Map.of("email", "nadie@nada.com", "password", "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(conPasswordMala));
    }

    @Test
    @DisplayName("Campos vacíos -> 400")
    void loginCamposVacios() throws Exception {
        postReq("/api/v1/auth/login", null, Map.of("email", "", "password", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Email con formato inválido -> 400")
    void loginEmailMalFormado() throws Exception {
        postReq("/api/v1/auth/login", null, Map.of("email", "no-es-un-correo", "password", "admin123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JSON mal formado -> 400 (no 500)")
    void loginJsonMalFormado() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ SESIÓN / TOKEN
    @Test
    @DisplayName("/me con token válido devuelve al usuario autenticado")
    void meConTokenValido() throws Exception {
        getReq("/api/v1/auth/me", login(MARTA, MARTA_PASS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.username").value(MARTA));
    }

    @Test
    @DisplayName("/me sin token -> 401")
    void meSinToken() throws Exception {
        getReq("/api/v1/auth/me", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token con la firma alterada es rechazado -> 401")
    void tokenFirmaAlterada() throws Exception {
        String token = login(MARTA, MARTA_PASS);
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        getReq("/api/v1/auth/me", alterado).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token vencido es rechazado -> 401")
    void tokenVencido() throws Exception {
        String vencido = Jwts.builder().subject("1").claim("email", MARTA).claim("rol", "ROLE_TENDERO")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).compact();
        getReq("/api/v1/auth/me", vencido).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token firmado con otra clave (falsificado) es rechazado -> 401")
    void tokenFalsificado() throws Exception {
        String falso = Jwts.builder().subject("1").claim("email", ADMIN).claim("rol", "ROLE_ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("0123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8))).compact();
        getReq("/api/v1/admin/auditoria", falso).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El encabezado Authorization sin el prefijo 'Bearer ' no autentica -> 401")
    void sinPrefijoBearer() throws Exception {
        String token = login(MARTA, MARTA_PASS);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================ REGISTRO
    @Test
    @DisplayName("Registro correcto: crea usuario TENDERO + tienda, guarda la contraseña con BCrypt y permite iniciar sesión")
    void registroCorrecto() throws Exception {
        postReq("/api/v1/auth/register", null, cuerpoRegistro("pedro@test.com", "1.000.111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.token").isNotEmpty())
                .andExpect(jsonPath("$.datos.rol").value("ROLE_TENDERO"))
                .andExpect(jsonPath("$.datos.email").value("pedro@test.com"));

        Usuario u = usuarioRepository.findByEmail("pedro@test.com").orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.ROLE_TENDERO);
        assertThat(u.getPasswordHash()).isNotEqualTo(CLAVE_TEST).startsWith("$2");
        assertThat(passwordEncoder.matches(CLAVE_TEST, u.getPasswordHash())).isTrue();

        Tienda t = tiendaRepository.findFirstByTenderoId(u.getId()).orElseThrow();
        assertThat(t.getNombreTienda()).isEqualTo("Tienda de Pedro");
        assertThat(t.getCiudad()).isEqualTo("Bogotá");
        assertThat(t.getLatitud()).isNotNull();

        // y ya puede iniciar sesión con esa contraseña
        assertThat(login("pedro@test.com", CLAVE_TEST)).isNotBlank();
    }

    @Test
    @DisplayName("El cliente NO puede elegir su rol: aunque envíe ROLE_ADMIN se crea como TENDERO y no accede al panel admin")
    void registroIgnoraRolEnviado() throws Exception {
        Map<String, Object> cuerpo = cuerpoRegistro("intruso@test.com", "1.000.222");
        cuerpo.put("rol", "ROLE_ADMIN");
        cuerpo.put("proveedorId", femsa().getId());
        String token = leer(postReq("/api/v1/auth/register", null, cuerpo)
                .andExpect(status().isCreated())).at("/datos/token").asText();

        Usuario u = usuarioRepository.findByEmail("intruso@test.com").orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.ROLE_TENDERO);
        assertThat(u.getProveedor()).isNull();
        getReq("/api/v1/admin/auditoria", token).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Email ya registrado -> 409 (también si cambia mayúsculas/minúsculas)")
    void registroEmailDuplicado() throws Exception {
        postReq("/api/v1/auth/register", null, cuerpoRegistro(MARTA, "1.000.333"))
                .andExpect(status().isConflict());
        postReq("/api/v1/auth/register", null, cuerpoRegistro(MARTA.toUpperCase(), "1.000.334"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("NIT/cédula ya registrado -> 409 y no se crea ningún usuario a medias")
    void registroNitDuplicado() throws Exception {
        postReq("/api/v1/auth/register", null, cuerpoRegistro("otro@test.com", "52.123.456"))
                .andExpect(status().isConflict());
        assertThat(usuarioRepository.existsByEmail("otro@test.com")).isFalse();
    }

    @Test
    @DisplayName("Contraseña de menos de 8 caracteres -> 400")
    void registroPasswordCorta() throws Exception {
        Map<String, Object> cuerpo = cuerpoRegistro("corta@test.com", "1.000.444");
        cuerpo.put("password", "1234567");
        postReq("/api/v1/auth/register", null, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("password")));
        assertThat(usuarioRepository.existsByEmail("corta@test.com")).isFalse();
    }

    @Test
    @DisplayName("Email inválido -> 400")
    void registroEmailInvalido() throws Exception {
        Map<String, Object> cuerpo = cuerpoRegistro("sin-arroba", "1.000.555");
        postReq("/api/v1/auth/register", null, cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Teléfono con letras -> 400")
    void registroTelefonoInvalido() throws Exception {
        Map<String, Object> cuerpo = cuerpoRegistro("tel@test.com", "1.000.666");
        cuerpo.put("telefono", "abc123");
        postReq("/api/v1/auth/register", null, cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Faltan datos de la tienda (nombre, NIT, dirección, barrio) -> 400")
    void registroSinDatosDeTienda() throws Exception {
        for (String campo : new String[]{"nombreTienda", "nitOCedula", "direccion", "barrio"}) {
            Map<String, Object> cuerpo = cuerpoRegistro("falta-" + campo + "@test.com", "1.000.7" + campo.length());
            cuerpo.remove(campo);
            postReq("/api/v1/auth/register", null, cuerpo).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Latitud fuera de rango -> 400")
    void registroLatitudFueraDeRango() throws Exception {
        Map<String, Object> cuerpo = cuerpoRegistro("lat@test.com", "1.000.888");
        cuerpo.put("latitud", 123.4);
        postReq("/api/v1/auth/register", null, cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El token devuelto al registrarse sirve de inmediato para consultar la propia tienda")
    void registroDevuelveTokenUtilizable() throws Exception {
        String token = registrarTendero("util@test.com", "1.000.999");
        getReq("/api/v1/tiendas/mi-tienda", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombreTienda").value("Tienda de Pedro"));
    }
}
