package com.prisma.api.proveedor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RastreoCamionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenProveedor;
    private String tokenRepartidor;

    @BeforeEach
    void setUp() throws Exception {
        tokenProveedor = login("logistica@femsa.com.co", "femsa123");
        tokenRepartidor = login("conductor.juan@femsa.com.co", "admin123");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        String res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.datos.token");
    }

    @Test
    @DisplayName("Rastreo GPS: Conductor transmite posición GPS y Proveedor consulta mapa en vivo")
    void testTransmisionYConsultaGpsEnVivo() throws Exception {
        // 1. Conductor transmite coordenadas GPS de su ruta activa (Ruta ID 1)
        Map<String, Object> posicionGps = new HashMap<>();
        posicionGps.put("latitud", new BigDecimal("4.6789123"));
        posicionGps.put("longitud", new BigDecimal("-74.0567891"));
        posicionGps.put("velocidadKmh", new BigDecimal("42.5"));
        posicionGps.put("rumboGrados", new BigDecimal("180.0"));

        mockMvc.perform(post("/api/v1/repartidores/rutas/1/posicion")
                        .header("Authorization", "Bearer " + tokenRepartidor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(posicionGps)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.latitud").value(4.6789123))
                .andExpect(jsonPath("$.datos.longitud").value(-74.0567891))
                .andExpect(jsonPath("$.datos.velocidadKmh").value(42.5));

        // 2. Proveedor consulta progreso de rutas con coordenadas en vivo y tiendas
        mockMvc.perform(get("/api/v1/proveedores/rutas/progreso")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos[0].ubicacionActual.latitud").value(4.6789123))
                .andExpect(jsonPath("$.datos[0].ubicacionActual.longitud").value(-74.0567891))
                .andExpect(jsonPath("$.datos[0].paradas[0].latitud").exists())
                .andExpect(jsonPath("$.datos[0].paradas[0].longitud").exists());

        // 3. Simulación de avance para sustentación
        Map<String, Object> pasoSimulacion = Map.of(
                "latitud", 4.6800000,
                "longitud", -74.0550000,
                "velocidadKmh", 30.0
        );
        mockMvc.perform(post("/api/v1/proveedores/rutas/1/simular-posicion")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pasoSimulacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.latitud").value(4.68));
    }
}
