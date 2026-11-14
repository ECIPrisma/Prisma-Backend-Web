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

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProveedorCamionesCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenPostobon;
    private String tokenFemsa;
    private String tokenTendero;

    @BeforeEach
    void setUp() throws Exception {
        tokenPostobon = login("ventas@postobon.com.co", "postobon123");
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
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
    @DisplayName("CRUD Flota: Listar, Crear, Actualizar y Dar de Baja Camión")
    void testCrudCompletoCamion() throws Exception {
        // 1. READ: Listar flota inicial
        mockMvc.perform(get("/api/v1/proveedores/camiones")
                        .header("Authorization", "Bearer " + tokenPostobon))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 2. CREATE: Registrar nuevo camión a la flota con terminal móvil única
        Map<String, Object> nuevoCamion = new HashMap<>();
        nuevoCamion.put("placa", "PST999");
        nuevoCamion.put("marcaModelo", "Chevrolet FTR 2024");
        nuevoCamion.put("capacidadPesoKg", 5000);
        nuevoCamion.put("capacidadCanastas", 200);
        nuevoCamion.put("pin", "654321");
        nuevoCamion.put("telefono", "3119998877");

        String resCrear = mockMvc.perform(post("/api/v1/proveedores/camiones")
                        .header("Authorization", "Bearer " + tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevoCamion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.placa").value("PST999"))
                .andExpect(jsonPath("$.datos.repartidorEmail").value("camion.pst999@distribucion.prisma.com"))
                .andReturn().getResponse().getContentAsString();

        Number camionId = JsonPath.read(resCrear, "$.datos.id");

        // 3. UPDATE: Actualizar estado operativo y modelo
        Map<String, Object> actualizacion = new HashMap<>();
        actualizacion.put("marcaModelo", "Chevrolet FTR 2024 Reforzado");
        actualizacion.put("estado", "DISPONIBLE");
        actualizacion.put("capacidadCanastas", 220);

        mockMvc.perform(put("/api/v1/proveedores/camiones/" + camionId)
                        .header("Authorization", "Bearer " + tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.marcaModelo").value("Chevrolet FTR 2024 Reforzado"))
                .andExpect(jsonPath("$.datos.capacidadCanastas").value(220));

        // 4. DELETE: Dar de baja el camión (pasa a estado MANTENIMIENTO)
        mockMvc.perform(delete("/api/v1/proveedores/camiones/" + camionId)
                        .header("Authorization", "Bearer " + tokenPostobon))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true));
    }

    @Test
    @DisplayName("Aislamiento Multi-Tenant: Proveedor FEMSA no puede modificar camión de Postobón (403 Forbidden)")
    void testAislamientoMultiTenantCamion() throws Exception {
        // Crear camión como Postobón
        Map<String, Object> camionPostobon = new HashMap<>();
        camionPostobon.put("placa", "PST888");
        camionPostobon.put("marcaModelo", "Hino Serie 300");
        camionPostobon.put("capacidadPesoKg", 4000);
        camionPostobon.put("capacidadCanastas", 180);

        String res = mockMvc.perform(post("/api/v1/proveedores/camiones")
                        .header("Authorization", "Bearer " + tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(camionPostobon)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number camionId = JsonPath.read(res, "$.datos.id");

        // Intentar actualizar como FEMSA -> debe arrojar 403 Forbidden
        Map<String, Object> payloadAtaque = Map.of("marcaModelo", "Hackeado por competencia");
        mockMvc.perform(put("/api/v1/proveedores/camiones/" + camionId)
                        .header("Authorization", "Bearer " + tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadAtaque)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Seguridad RBAC: Tendero no puede acceder a endpoints de flota (403 Forbidden)")
    void testTenderoNoPuedeAccederFlota() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/camiones")
                        .header("Authorization", "Bearer " + tokenTendero))
                .andExpect(status().isForbidden());
    }
}
