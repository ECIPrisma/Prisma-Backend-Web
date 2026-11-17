package com.prisma.api.pagos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.prisma.api.entity.PagoPedido;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.enums.MedioPagoEnLinea;
import com.prisma.api.repository.AuditLogRepository;
import com.prisma.api.repository.PagoPedidoRepository;
import com.prisma.api.service.RegistroSeguridadService;
import com.prisma.api.service.WebhookPagosService;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProveedorRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HU-12 / N-08 – Pasarela de pagos SIMULADA: vinculación de la cuenta de cobro del proveedor,
 * checkout del tendero (Nequi / PSE / tarjeta), garantía y liberación al confirmar la entrega.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PagosSimuladosIntegrationTest {

    private static final String MENSAJE_FALLA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";
    private static final String MENSAJE_RECHAZO = "El pago no pudo ser procesado, intenta con otro medio";

    @Autowired private MockMvc mockMvc;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private FirmaEventosWompi firma;
    @Autowired private PagoPedidoRepository pagoRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ObjectMapper objectMapper;

    private String tokenTendero;
    private String tokenFemsa;
    private Proveedor femsa;

    @BeforeEach
    void setUp() throws Exception {
        tokenTendero = login("dona.marta@labendicion.com", "admin123");
        tokenFemsa = login("logistica@femsa.com.co", "femsa123");
        femsa = proveedorRepository.findByNit("860.000.123-1").orElseThrow();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.datos.token");
    }

    private Long crearPedido() throws Exception {
        String body = mockMvc.perform(post("/api/v1/tiendas/pedidos").header("Authorization", tokenTendero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedorId\":" + femsa.getId() + ",\"items\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.datos.id")).longValue();
    }

    private ResultActions pagar(Long pedidoId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------------ Checkout del tendero (N-08)

    @Test
    @DisplayName("Nequi 3991111111 aprueba: el pedido queda pagado en garantía y se ve en Mis Pedidos")
    void nequiApruebaYQuedaEnGarantia() throws Exception {
        Long pedidoId = crearPedido();

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.aprobado").value(true))
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.datos.detalleMedio").value("Nequi ••• 1111"))
                .andExpect(jsonPath("$.datos.referencia").value(startsWith("PRISMA-PED-")))
                .andExpect(jsonPath("$.datos.transaccionId").value(startsWith("SBX-")))
                .andExpect(jsonPath("$.datos.simulado").value(true));

        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("EN_GARANTIA")))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].medioPagoEnLinea").value(contains("NEQUI")));
    }

    @Test
    @DisplayName("Un pedido ya pagado no se puede pagar dos veces")
    void noSePagaDosVeces() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242 4242 4242 4242\",\"titular\":\"MARTA RODRIGUEZ\",\"vencimiento\":\"12/30\",\"cuotas\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.detalleMedio").value("VISA •••• 4242"));

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rechazos simulados (Nequi, PSE, tarjeta) dejan el pedido sin pagar y permiten reintentar")
    void rechazosPermitenReintentar() throws Exception {
        Long pedidoId = crearPedido();

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3992222222\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.aprobado").value(false))
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECHAZO));
        pagar(pedidoId, "{\"medio\":\"PSE\",\"banco\":\"Banco que rechaza\",\"tipoPersona\":\"NATURAL\",\"documento\":\"1020304050\"}")
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4111111111111111\",\"titular\":\"MARTA\",\"vencimiento\":\"12/30\"}")
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));

        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("RECHAZADO")));

        pagar(pedidoId, "{\"medio\":\"PSE\",\"banco\":\"Bancolombia\",\"tipoPersona\":\"NATURAL\",\"documento\":\"1020304050\"}")
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"))
                .andExpect(jsonPath("$.datos.detalleMedio").value("PSE • Bancolombia"));

        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));
    }

    @Test
    @DisplayName("Datos inválidos se rechazan con 400 sin crear transacción")
    void datosInvalidos() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242424242424241\",\"titular\":\"MARTA\",\"vencimiento\":\"12/30\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4242424242424242\",\"titular\":\"MARTA\",\"vencimiento\":\"01/20\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"12345\"}")
                .andExpect(status().isBadRequest());
        pagar(pedidoId, "{\"celular\":\"3991111111\"}")
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Solo el tendero dueño del pedido puede pagarlo")
    void soloElTenderoPaga() throws Exception {
        Long pedidoId = crearPedido();
        mockMvc.perform(post("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}"))
                .andExpect(status().isForbidden());
        pagar(999_999L, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Confirmar la entrega con PIN libera la garantía al proveedor y no suma efectivo a la ruta")
    void entregaLiberaGarantia() throws Exception {
        Long pedidoId = crearPedido();
        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.cantidadEnGarantia").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")].estado").value(contains("EN_GARANTIA")));

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        var recaudoAntes = pedido.getRuta() != null ? pedido.getRuta().getTotalRecaudoEfectivo() : null;

        String tokenConductor = login("conductor.juan@femsa.com.co", "admin123");
        mockMvc.perform(post("/api/v1/repartidores/pedidos/" + pedidoId + "/confirmar-entrega")
                        .header("Authorization", tokenConductor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pedido.getPinEntregaHash() + "\",\"medioPago\":\"EFECTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estadoPago").value("LIBERADO"));

        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")].estado").value(contains("LIBERADO")))
                .andExpect(jsonPath("$.datos.cantidadLiberados").value(greaterThanOrEqualTo(1)));

        Pedido entregado = pedidoRepository.findById(pedidoId).orElseThrow();
        if (entregado.getRuta() != null) {
            assertThat(entregado.getRuta().getTotalRecaudoEfectivo()).isEqualTo(recaudoAntes);
        }
    }

    // ------------------------------------------------------------- HU-13 Split payment y webhook

    private Map<String, Object> eventoFirmado(String transaccionId, String referencia, String status, long centavos) {
        Map<String, Object> transaccion = new LinkedHashMap<>();
        transaccion.put("id", transaccionId);
        transaccion.put("reference", referencia);
        transaccion.put("status", status);
        transaccion.put("amount_in_cents", centavos);
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("event", "transaction.updated");
        evento.put("data", Map.of("transaction", transaccion));
        evento.put("timestamp", java.time.Instant.now().getEpochSecond());
        return evento;
    }

    /** Envía el evento firmado con HMAC-SHA256 en el encabezado, como lo hace la pasarela. */
    private ResultActions webhook(Map<String, Object> evento) throws Exception {
        byte[] cuerpo = objectMapper.writeValueAsBytes(evento);
        return webhookConFirma(cuerpo, firma.firmar(cuerpo));
    }

    private ResultActions webhookConFirma(byte[] cuerpo, String encabezado) throws Exception {
        var peticion = post("/api/v1/pagos/webhooks/wompi").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (encabezado != null) {
            peticion.header(FirmaEventosWompi.ENCABEZADO, encabezado);
        }
        return mockMvc.perform(peticion);
    }

    private PagoPedido crearPagoPendiente(Long pedidoId, String referencia, String transaccionId) {
        PagoPedido pendiente = new PagoPedido();
        pendiente.setPedidoId(pedidoId);
        pendiente.setNumeroPedido("PED-PENDIENTE");
        pendiente.setProveedorId(femsa.getId());
        pendiente.setTenderoId(1L);
        pendiente.setReferencia(referencia);
        pendiente.setTransaccionId(transaccionId);
        pendiente.setPasarela(PasarelaPagosSimulada.NOMBRE);
        pendiente.setMedio(MedioPagoEnLinea.PSE);
        pendiente.setDetalleMedio("PSE • Bancolombia");
        pendiente.setMonto(new java.math.BigDecimal("125000.00"));
        pendiente.setComisionPrisma(new java.math.BigDecimal("4375.00"));
        pendiente.setMontoProveedor(new java.math.BigDecimal("120625.00"));
        pendiente.setEstado(EstadoPagoPedido.PENDIENTE);
        return pagoRepository.save(pendiente);
    }

    @Test
    @DisplayName("HU-13 criterio 1: un solo pago se divide 3.5% PRISMA / 96.5% proveedor y lo confirma el webhook")
    void splitPaymentConfirmadoPorWebhook() throws Exception {
        Long pedidoId = crearPedido(); // pedido sin ítems: total de 125.000

        pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(startsWith("Pago aprobado")))
                .andExpect(jsonPath("$.datos.monto").value(125000.0))
                .andExpect(jsonPath("$.datos.porcentajeComision").value(3.5))
                .andExpect(jsonPath("$.datos.comisionPrisma").value(4375.0))
                .andExpect(jsonPath("$.datos.montoProveedor").value(120625.0))
                .andExpect(jsonPath("$.datos.tarifaPasarela").value(2237.5))
                .andExpect(jsonPath("$.datos.confirmadoPorWebhook").value(true))
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));

        assertThat(pedidoRepository.findById(pedidoId).orElseThrow().getComisionPrismaValor()).isEqualByComparingTo("4375.00");
    }

    @Test
    @DisplayName("HU-13 criterio 2: rechazo de la entidad deja el pedido Confirmado sin garantía y con el mensaje exacto")
    void rechazoMantienePedidoConfirmado() throws Exception {
        Long pedidoId = crearPedido();

        pagar(pedidoId, "{\"medio\":\"TARJETA\",\"numeroTarjeta\":\"4111111111111111\",\"titular\":\"MARTA\",\"vencimiento\":\"12/30\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_RECHAZO))
                .andExpect(jsonPath("$.datos.aprobado").value(false))
                .andExpect(jsonPath("$.datos.confirmadoPorWebhook").value(true));

        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estado").value(contains("CREADO")))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("RECHAZADO")));
        mockMvc.perform(get("/api/v1/proveedores/pagos/recaudos").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.pagos[?(@.pedidoId == " + pedidoId + ")]").isEmpty());
    }

    @Test
    @DisplayName("HU-14 criterio 1: webhook con HMAC válido confirma el pago (200) y avisa al proveedor para despachar")
    void webhookValidoConfirmaYNotifica() throws Exception {
        Long pedidoId = crearPedido();
        crearPagoPendiente(pedidoId, "PRISMA-HU14-OK", "SBX-HU14OK");

        webhook(eventoFirmado("SBX-HU14OK", "PRISMA-HU14-OK", "APPROVED", 12_500_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("PROCESADO"));

        assertThat(pagoRepository.findByReferencia("PRISMA-HU14-OK").orElseThrow().getEstado())
                .isEqualTo(EstadoPagoPedido.EN_GARANTIA);
        mockMvc.perform(get("/api/v1/tiendas/mis-pedidos").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos[?(@.id == " + pedidoId + ")].estadoPago").value(contains("EN_GARANTIA")));

        String numeroPedido = pedidoRepository.findById(pedidoId).orElseThrow().getNumeroPedido();
        mockMvc.perform(get("/api/v1/notificaciones/mis-notificaciones").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.tipo == 'PAGO_CONFIRMADO' && @.numeroPedido == '" + numeroPedido + "')]").isNotEmpty());
    }

    @Test
    @DisplayName("HU-14 criterio 2: firma inválida, ausente o vencida → 401, log de seguridad y el pedido no cambia")
    void webhookFirmaInvalidaRechazadaYAuditada() throws Exception {
        Long pedidoId = crearPedido();
        crearPagoPendiente(pedidoId, "PRISMA-HU14-FRAUDE", "SBX-HU14FR");
        long antes = auditLogRepository.countByTipoEvento(RegistroSeguridadService.EVENTO_WEBHOOK_RECHAZADO);
        byte[] cuerpo = objectMapper.writeValueAsBytes(eventoFirmado("SBX-HU14FR", "PRISMA-HU14-FRAUDE", "APPROVED", 12_500_000L));

        // 1. Firma con un secreto que no es el de la pasarela (atacante)
        String firmaAtacante = new FirmaEventosWompi("secreto_de_un_atacante").firmar(cuerpo);
        webhookConFirma(cuerpo, firmaAtacante)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(WebhookPagosService.MENSAJE_FIRMA_INVALIDA));
        // 2. Sin encabezado de firma
        webhookConFirma(cuerpo, null).andExpect(status().isUnauthorized());
        // 3. Firma válida de otro cuerpo (cuerpo manipulado: se cambió el estado o el monto)
        byte[] otroCuerpo = objectMapper.writeValueAsBytes(eventoFirmado("SBX-HU14FR", "PRISMA-HU14-FRAUDE", "DECLINED", 12_500_000L));
        webhookConFirma(cuerpo, firma.firmar(otroCuerpo)).andExpect(status().isUnauthorized());
        // 4. Firma correcta pero vieja (reenvío de un webhook capturado)
        long viejo = java.time.Instant.now().getEpochSecond() - FirmaEventosWompi.TOLERANCIA_SEGUNDOS - 60;
        webhookConFirma(cuerpo, firma.firmar(cuerpo, viejo)).andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.countByTipoEvento(RegistroSeguridadService.EVENTO_WEBHOOK_RECHAZADO))
                .isEqualTo(antes + 4);
        assertThat(pagoRepository.findByReferencia("PRISMA-HU14-FRAUDE").orElseThrow().getEstado())
                .isEqualTo(EstadoPagoPedido.PENDIENTE);
    }

    @Test
    @DisplayName("HU-13 webhook: es idempotente y no cambia un pago que ya tiene resultado final")
    void webhookIdempotente() throws Exception {
        Long pedidoId = crearPedido();
        String body = pagar(pedidoId, "{\"medio\":\"NEQUI\",\"celular\":\"3991111111\"}")
                .andReturn().getResponse().getContentAsString();
        String transaccion = JsonPath.read(body, "$.datos.transaccionId");
        String referencia = JsonPath.read(body, "$.datos.referencia");

        webhook(eventoFirmado(transaccion, referencia, "DECLINED", 12_500_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("YA_PROCESADO"));
        mockMvc.perform(get("/api/v1/tiendas/pedidos/" + pedidoId + "/pago").header("Authorization", tokenTendero))
                .andExpect(jsonPath("$.datos.estado").value("EN_GARANTIA"));

        webhook(eventoFirmado("SBX-OTRO", "REFERENCIA-AJENA", "APPROVED", 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("IGNORADO"));
    }

    @Test
    @DisplayName("HU-13 webhook: un pago pendiente solo se confirma si el monto coincide")
    void webhookValidaMonto() throws Exception {
        Long pedidoId = crearPedido();
        crearPagoPendiente(pedidoId, "PRISMA-PENDIENTE-1", "SBX-PENDIENTE1");

        webhook(eventoFirmado("SBX-PENDIENTE1", "PRISMA-PENDIENTE-1", "APPROVED", 999))
                .andExpect(status().isBadRequest());
        webhook(eventoFirmado("SBX-PENDIENTE1", "PRISMA-PENDIENTE-1", "APPROVED", 12_500_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.resultado").value("PROCESADO"));

        PagoPedido confirmado = pagoRepository.findByReferencia("PRISMA-PENDIENTE-1").orElseThrow();
        assertThat(confirmado.getEstado()).isEqualTo(EstadoPagoPedido.EN_GARANTIA);
        assertThat(confirmado.getConfirmadoEn()).isNotNull();
    }

    // ------------------------------------------------------------- Cuenta de cobro del proveedor (HU-12)

    @Test
    @DisplayName("Proveedor sin cuenta: estado no vinculada con el porcentaje que recibirá")
    void estadoSinVincular() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.disponible").value(true))
                .andExpect(jsonPath("$.datos.simulada").value(true))
                .andExpect(jsonPath("$.datos.vinculada").value(false))
                .andExpect(jsonPath("$.datos.porcentajeProveedor").value(96.50))
                .andExpect(jsonPath("$.datos.comisionPrismaPorcentaje").value(3.50));
    }

    @Test
    @DisplayName("Criterio 1: vincular la cuenta la habilita y solo expone los últimos 4 dígitos")
    void vincularCuenta() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Bancolombia\",\"tipoCuenta\":\"ahorros\",\"numeroCuenta\":\"123-456789-12\",\"titular\":\"FEMSA Colombia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.vinculada").value(true))
                .andExpect(jsonPath("$.datos.habilitadoPagosDivididos").value(true))
                .andExpect(jsonPath("$.datos.tipoCuenta").value("AHORROS"))
                .andExpect(jsonPath("$.datos.numeroCuentaEnmascarado").value("•••• 8912"))
                .andExpect(jsonPath("$.datos.cuentaExternaId").value(startsWith("WMP-SBX-")))
                .andExpect(content().string(not(containsString("12345678912"))));

        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Davivienda\",\"tipoCuenta\":\"CORRIENTE\",\"numeroCuenta\":\"99998888\",\"titular\":\"FEMSA\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vinculada").value(false));
    }

    @Test
    @DisplayName("Criterio 2: si la pasarela no verifica la cuenta (termina en 0000) se muestra el mensaje de falla")
    void vinculacionRechazada() throws Exception {
        mockMvc.perform(post("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banco\":\"Bancolombia\",\"tipoCuenta\":\"AHORROS\",\"numeroCuenta\":\"1234560000\",\"titular\":\"FEMSA Colombia\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_FALLA));

        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenFemsa))
                .andExpect(jsonPath("$.datos.vinculada").value(false));
    }

    @Test
    @DisplayName("El tendero no puede administrar cuentas de cobro de proveedores")
    void tenderoNoAccedeCuentaCobro() throws Exception {
        mockMvc.perform(get("/api/v1/proveedores/pagos/cuenta").header("Authorization", tokenTendero))
                .andExpect(status().isForbidden());
    }
}
