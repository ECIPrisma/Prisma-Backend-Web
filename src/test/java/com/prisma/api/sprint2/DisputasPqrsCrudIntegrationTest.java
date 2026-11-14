package com.prisma.api.sprint2;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DisputasPqrsCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenProveedor;
    private String tokenAdmin;

    @BeforeEach
    void setUp() throws Exception {
        tokenProveedor = login("ventas@postobon.com.co", "postobon123");
        tokenAdmin = login("admin@prisma.com.co", "admin123");
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
    @DisplayName("Ciclo PQRS y Disputas: Proveedor radica reclamo a Prisma y Admin responde/resuelve")
    void testCicloCompletoDisputas() throws Exception {
        // 1. CREATE: Proveedor radica disputa/PQRS a Prisma
        Map<String, Object> nuevaDisputa = new HashMap<>();
        nuevaDisputa.put("tipoIncidente", "DISPUTA_COMISION_PRISMA");
        nuevaDisputa.put("descripcion", "Reclamo por diferencia en liquidación de comisión quincenal");

        String resCrear = mockMvc.perform(post("/api/v1/proveedores/pqrs/radicar-a-prisma")
                        .header("Authorization", "Bearer " + tokenProveedor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nuevaDisputa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.ticketNumero").exists())
                .andExpect(jsonPath("$.datos.estado").value("ABIERTO"))
                .andReturn().getResponse().getContentAsString();

        Number disputaId = JsonPath.read(resCrear, "$.datos.id");

        // 2. READ (Proveedor): Proveedor consulta sus PQRS
        mockMvc.perform(get("/api/v1/proveedores/pqrs")
                        .header("Authorization", "Bearer " + tokenProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 3. READ (Admin): Administrador consulta la mesa de disputas
        mockMvc.perform(get("/api/v1/admin/disputas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos").isArray());

        // 4. UPDATE / RESOLVE (Admin): Administrador da resolución al caso
        Map<String, String> respuestaPayload = Map.of(
                "resolucion", "Se verificaron las facturas y se reajustó la comisión a favor del proveedor.",
                "nuevoEstado", "RESUELTO"
        );

        mockMvc.perform(post("/api/v1/admin/disputas/" + disputaId + "/responder")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(respuestaPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.datos.estado").value("RESUELTO"));
    }
}
