package com.prisma.api.proveedor;

import com.jayway.jsonpath.JsonPath;
import com.prisma.api.entity.Camion;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.repository.CamionRepository;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.RutaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-04 – Gestión de perfil comercial con aislamiento multi-tenant.
 * FEMSA y Postobón son dos proveedores distintos sembrados por DataInitializer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PerfilProveedorIntegrationTest {

    private static final String PERFIL_VALIDO = """
            {"razonSocial":"Industria Nacional de Gaseosas S.A.S. BIC",
             "nombreComercial":"Coca-Cola FEMSA Colombia",
             "zonaCobertura":"Bogotá: Kennedy, Bosa y Soacha",
             "contactoNombre":"Carlos Morales",
             "contactoEmail":"logistica@femsa.com.co",
             "contactoTelefono":"3109876543",
             "direccionSede":"Calle 100 # 19-61",
             "ciudad":"Bogotá"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private CamionRepository camionRepository;
    @Autowired private RutaRepository rutaRepository;

    private String tokenFemsa;
    private String tokenPostobon;
    private Proveedor femsa;
    private Proveedor postobon;

    @BeforeEach
    void setUp() throws Exception {
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        tokenPostobon = login("ventas@postobon.com.co", "postobon123");
        femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
        postobon = proveedorRepository.findByNit("890.903.939-5").orElseThrow();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    // ------------------------------------------------ Criterio 1: edición de perfil propio

    @Test
    void consultaSuPropioPerfil() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/perfil").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.id").value(femsa.getId()))
                .andExpect(jsonPath("$.datos.nit").value("860.000.123-1"));
    }

    @Test
    void actualizaSuPerfilYMuestraConfirmacion() throws Exception {
        mockMvc.perform(put("/api/v1/proveedores/perfil").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON).content(PERFIL_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Perfil actualizado correctamente"))
                .andExpect(jsonPath("$.datos.razonSocial").value("Industria Nacional de Gaseosas S.A.S. BIC"))
                .andExpect(jsonPath("$.datos.zonaCobertura").value("Bogotá: Kennedy, Bosa y Soacha"));

        Proveedor guardado = proveedorRepository.findById(femsa.getId()).orElseThrow();
        assertThat(guardado.getZonaCobertura()).isEqualTo("Bogotá: Kennedy, Bosa y Soacha");
        assertThat(guardado.getNit()).isEqualTo("860.000.123-1");
    }

    @Test
    void datosInvalidosResponden400() throws Exception {
        mockMvc.perform(put("/api/v1/proveedores/perfil").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PERFIL_VALIDO.replace("logistica@femsa.com.co", "correo-invalido")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unTenderoNoAccedeAlPerfil() throws Exception {
        String tokenTendero = login("dona.marta@labendicion.com", "admin123");
        mockMvc.perform(get("/api/v1/proveedores/perfil").header("Authorization", tokenTendero))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ Criterio 2: aislamiento entre proveedores

    @Test
    void noPuedeLeerElPerfilDeOtroProveedor() throws Exception {
        String body = mockMvc.perform(get("/api/v1/proveedores/" + femsa.getId() + "/perfil").header("Authorization", tokenPostobon))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.datos").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("860.000.123-1").doesNotContain("FEMSA");
    }

    @Test
    void noPuedeModificarElPerfilDeOtroProveedor() throws Exception {
        mockMvc.perform(put("/api/v1/proveedores/" + femsa.getId() + "/perfil").header("Authorization", tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON).content(PERFIL_VALIDO.replace("S.A.S. BIC", "HACKEADO")))
                .andExpect(status().isForbidden());

        assertThat(proveedorRepository.findById(femsa.getId()).orElseThrow().getRazonSocial()).doesNotContain("HACKEADO");
    }

    @Test
    void puedeUsarLaRutaConSuPropioId() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/" + postobon.getId() + "/perfil").header("Authorization", tokenPostobon))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nit").value("890.903.939-5"));
    }

    @Test
    void elAdministradorPuedeConsultarCualquierPerfil() throws Exception {
        String tokenAdmin = login("admin@prisma.com.co", "admin123");
        mockMvc.perform(get("/api/v1/proveedores/" + femsa.getId() + "/perfil").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void noPuedeManipularPedidosDeOtroProveedor() throws Exception {
        Pedido pedidoFemsa = pedidoRepository.findAll().stream()
                .filter(p -> p.getProveedor().getId().equals(femsa.getId())).findFirst().orElseThrow();
        var estadoOriginal = pedidoFemsa.getEstado();

        String body = mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedidoFemsa.getId() + "/cambiar-estado")
                        .header("Authorization", tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.datos").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(pedidoFemsa.getNumeroPedido());

        mockMvc.perform(post("/api/v1/proveedores/pedidos/" + pedidoFemsa.getId() + "/reprogramar")
                        .header("Authorization", tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevaFecha\":\"2030-01-01\"}"))
                .andExpect(status().isForbidden());

        assertThat(pedidoRepository.findById(pedidoFemsa.getId()).orElseThrow().getEstado()).isEqualTo(estadoOriginal);
    }

    @Test
    void noPuedeManipularCamionesNiRutasDeOtroProveedor() throws Exception {
        Camion camionFemsa = camionRepository.findAll().stream()
                .filter(c -> c.getProveedor().getId().equals(femsa.getId())).findFirst().orElseThrow();
        Long rutaFemsa = rutaRepository.findByProveedorId(femsa.getId()).get(0).getId();

        mockMvc.perform(put("/api/v1/proveedores/camiones/" + camionFemsa.getId()).header("Authorization", tokenPostobon)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placa\":\"HCK000\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/proveedores/camiones/" + camionFemsa.getId()).header("Authorization", tokenPostobon))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proveedores/rutas/" + rutaFemsa + "/reportar-varado").header("Authorization", tokenPostobon))
                .andExpect(status().isForbidden());

        assertThat(camionRepository.findById(camionFemsa.getId()).orElseThrow().getPlaca()).isNotEqualTo("HCK000");
    }

    @Test
    void elDuenoSiPuedeOperarSusPropiosRecursos() throws Exception {
        Camion camionFemsa = camionRepository.findAll().stream()
                .filter(c -> c.getProveedor().getId().equals(femsa.getId())).findFirst().orElseThrow();

        mockMvc.perform(put("/api/v1/proveedores/camiones/" + camionFemsa.getId()).header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marcaModelo\":\"Chevrolet NHR 2025\"}"))
                .andExpect(status().isOk());
    }
}
