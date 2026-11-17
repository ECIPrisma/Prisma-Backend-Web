package com.prisma.api.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.enums.EstadoPagoSuscripcion;
import com.prisma.api.repository.PagoSuscripcionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVP – El proveedor (distribuidor) paga su suscripción mensual Prisma Pro por la pasarela simulada.
 * Reutiliza el circuit breaker (HU-19) y la confirmación por webhook HMAC (HU-14).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuscripcionProveedorIntegrationTest {

    private static final String RUTA = "/api/v1/proveedores/suscripcion";
    private static final String NEQUI_OK = "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasarelaPagosResiliente pasarelaResiliente;
    @Autowired private PagoSuscripcionRepository pagoRepository;
    @Autowired private FirmaEventosWompi firma;
    @Autowired private ObjectMapper objectMapper;

    private String tokenFemsa;
    private String tokenTendero;

    @BeforeEach
    void setUp() throws Exception {
        pasarelaResiliente.reiniciar();
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
    }

    @AfterEach
    void cerrarCircuito() {
        pasarelaResiliente.reiniciar();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    private ResultActions pagar(String json) throws Exception {
        return mockMvc.perform(post(RUTA + "/pago").header("Authorization", tokenFemsa)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("Sin pagos: el plan Prisma Pro Distribuidor cuesta $120.000 con IVA y no hay suscripción")
    void estadoInicial() throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.plan").value("DISTRIBUIDOR_PRO"))
                .andExpect(jsonPath("$.datos.precioMensual").value(120000.0))
                .andExpect(jsonPath("$.datos.valorNeto").value(100840.34))
                .andExpect(jsonPath("$.datos.valorIva").value(19159.66))
                .andExpect(jsonPath("$.datos.estado").value("SIN_SUSCRIPCION"))
                .andExpect(jsonPath("$.datos.pagos").isEmpty());
    }

    @Test
    @DisplayName("Pago aprobado: confirmado por webhook, la suscripción queda ACTIVA un mes y se avisa al proveedor")
    void pagoAprobadoActivaUnMes() throws Exception {
        LocalDate hoy = LocalDate.now();
        LocalDate hasta = hoy.plusMonths(1).minusDays(1);

        pagar(NEQUI_OK)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(startsWith("Pago aprobado")))
                .andExpect(jsonPath("$.datos.estado").value("APROBADO"))
                .andExpect(jsonPath("$.datos.monto").value(120000.0))
                .andExpect(jsonPath("$.datos.confirmadoPorWebhook").value(true))
                .andExpect(jsonPath("$.datos.referencia").value(startsWith("PRISMA-SUS-")))
                .andExpect(jsonPath("$.datos.periodoDesde").value(hoy.toString()))
                .andExpect(jsonPath("$.datos.periodoHasta").value(hasta.toString()))
                .andExpect(jsonPath("$.datos.tarifaPasarela").value(2148.0)); // Nequi 1.79% de 120.000

        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.datos.vigenteHasta").value(hasta.toString()))
                .andExpect(jsonPath("$.datos.pagos.length()").value(1));

        mockMvc.perform(get("/api/v1/notificaciones/mis-notificaciones").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos[?(@.tipo == 'SUSCRIPCION_PAGADA')]").isNotEmpty());
    }

    @Test
    @DisplayName("Pagar con la suscripción activa la extiende un mes más, sin perder días")
    void segundoPagoExtiende() throws Exception {
        pagar(NEQUI_OK).andExpect(status().isOk());
        LocalDate primerVencimiento = LocalDate.now().plusMonths(1).minusDays(1);
        LocalDate segundoVencimiento = primerVencimiento.plusDays(1).plusMonths(1).minusDays(1);

        pagar("{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242 4242 4242 4242\",\"titular\":\"FEMSA\",\"vencimiento\":\"12/30\"}")
                .andExpect(jsonPath("$.datos.periodoDesde").value(primerVencimiento.plusDays(1).toString()))
                .andExpect(jsonPath("$.datos.periodoHasta").value(segundoVencimiento.toString()));

        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vigenteHasta").value(segundoVencimiento.toString()))
                .andExpect(jsonPath("$.datos.pagos.length()").value(2));
    }

    @Test
    @DisplayName("Pago rechazado: mensaje 'El pago no pudo ser procesado, intenta con otro medio' y la suscripción no cambia")
    void pagoRechazado() throws Exception {
        pagar("{\"medio\":\"PSE\",\"banco\":\"Banco que rechaza\",\"tipoPersona\":\"JURIDICA\",\"documento\":\"8600001231\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("El pago no pudo ser procesado, intenta con otro medio"))
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.datos.motivoRechazo").isNotEmpty());

        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.estado").value("SIN_SUSCRIPCION"));
    }

    @Test
    @DisplayName("HU-19: con la pasarela caída el circuito se abre y el pago responde 503 de inmediato")
    void circuitBreakerProtegeElPago() throws Exception {
        for (int i = 0; i < 5; i++) {
            pagar("{\"medio\":\"NEQUI\",\"celular\":\"3993333333\"}").andExpect(status().isServiceUnavailable());
        }
        pagar(NEQUI_OK)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Servicio de pagos temporalmente no disponible"));

        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.estado").value("SIN_SUSCRIPCION"))
                .andExpect(jsonPath("$.datos.pagos").isEmpty());
    }

    @Test
    @DisplayName("HU-14: un webhook falsificado no puede activar la suscripción (401) y uno válido repetido no la cambia")
    void webhookFalsificadoNoActiva() throws Exception {
        // Pago rechazado: queda registrado con su referencia y transacción
        String body = pagar("{\"medio\":\"NEQUI\",\"celular\":\"3992222222\"}").andReturn().getResponse().getContentAsString();
        String referencia = JsonPath.read(body, "$.datos.referencia");
        String transaccion = JsonPath.read(body, "$.datos.transaccionId");

        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("id", transaccion);
        tx.put("reference", referencia);
        tx.put("status", "APPROVED");
        tx.put("amount_in_cents", 12_000_000L);
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", Map.of("transaction", tx));
        byte[] cuerpo = objectMapper.writeValueAsBytes(evento);

        // Firmado con otro secreto (atacante): 401
        mockMvc.perform(post("/api/v1/pagos/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaEventosWompi.ENCABEZADO, new FirmaEventosWompi("secreto_de_un_atacante").firmar(cuerpo)))
                .andExpect(status().isUnauthorized());
        // Firma válida, pero el pago ya tiene resultado final: no se reactiva
        mockMvc.perform(post("/api/v1/pagos/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(FirmaEventosWompi.ENCABEZADO, firma.firmar(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("YA_PROCESADO"));

        assertThat(pagoRepository.findByReferencia(referencia).orElseThrow().getEstado())
                .isEqualTo(EstadoPagoSuscripcion.RECHAZADO);
        mockMvc.perform(get(RUTA).header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.estado").value("SIN_SUSCRIPCION"));
    }

    @Test
    @DisplayName("Solo el proveedor accede a su suscripción; el tendero no (403)")
    void soloProveedor() throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", tokenTendero)).andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA + "/pago").header("Authorization", tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON).content(NEQUI_OK))
                .andExpect(status().isForbidden());
    }
}
