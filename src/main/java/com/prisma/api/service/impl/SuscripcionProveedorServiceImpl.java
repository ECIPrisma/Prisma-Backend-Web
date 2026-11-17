package com.prisma.api.service.impl;

import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoSuscripcionResponse;
import com.prisma.api.dto.SuscripcionResponse;
import com.prisma.api.entity.PagoSuscripcion;
import com.prisma.api.entity.SuscripcionProveedor;
import com.prisma.api.enums.EstadoPagoSuscripcion;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.PasarelaPagosResiliente;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.pagos.PlanPrismaPro;
import com.prisma.api.pagos.ReglasSplitPayment;
import com.prisma.api.repository.PagoSuscripcionRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.SuscripcionProveedorRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.SuscripcionProveedorService;
import com.prisma.api.service.WebhookPagosService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SuscripcionProveedorServiceImpl implements SuscripcionProveedorService {

    private final SuscripcionProveedorRepository suscripcionRepository;
    private final PagoSuscripcionRepository pagoRepository;
    private final ProveedorRepository proveedorRepository;
    private final PasarelaPagosSimulada pasarela;
    private final PasarelaPagosResiliente pasarelaResiliente;
    private final WebhookPagosService webhookPagosService;

    public SuscripcionProveedorServiceImpl(SuscripcionProveedorRepository suscripcionRepository,
                                           PagoSuscripcionRepository pagoRepository,
                                           ProveedorRepository proveedorRepository,
                                           PasarelaPagosSimulada pasarela,
                                           PasarelaPagosResiliente pasarelaResiliente,
                                           WebhookPagosService webhookPagosService) {
        this.suscripcionRepository = suscripcionRepository;
        this.pagoRepository = pagoRepository;
        this.proveedorRepository = proveedorRepository;
        this.pasarela = pasarela;
        this.pasarelaResiliente = pasarelaResiliente;
        this.webhookPagosService = webhookPagosService;
    }

    @Override
    @Transactional(readOnly = true)
    public SuscripcionResponse obtenerEstado(UserPrincipal usuario) {
        Long proveedorId = proveedorDe(usuario);
        SuscripcionProveedor suscripcion = suscripcionRepository.findByProveedorId(proveedorId).orElse(null);
        BigDecimal precio = suscripcion != null ? suscripcion.getPrecioMensual() : PlanPrismaPro.PRECIO_MENSUAL_CON_IVA;

        LocalDate hoy = LocalDate.now();
        LocalDate vigenteHasta = suscripcion != null ? suscripcion.getVigenteHasta() : null;
        String estado;
        long diasRestantes = 0;
        if (vigenteHasta == null) {
            estado = "SIN_SUSCRIPCION";
        } else if (vigenteHasta.isBefore(hoy)) {
            estado = "VENCIDA";
        } else {
            estado = "ACTIVA";
            diasRestantes = ChronoUnit.DAYS.between(hoy, vigenteHasta) + 1; // incluye el día de hoy
        }

        List<PagoSuscripcionResponse> pagos = pagoRepository.findByProveedorIdOrderByCreadoEnDescIdDesc(proveedorId)
                .stream().map(PagoSuscripcionResponse::de).toList();

        return new SuscripcionResponse(PlanPrismaPro.CODIGO, PlanPrismaPro.NOMBRE, precio,
                PlanPrismaPro.valorNeto(precio), PlanPrismaPro.valorIva(precio), PlanPrismaPro.BENEFICIOS,
                estado, vigenteHasta, diasRestantes, true, pagos);
    }

    @Override
    @Transactional
    public PagoSuscripcionResponse pagarMes(UserPrincipal usuario, PagarPedidoRequest datos) {
        if (datos == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Faltan los datos del pago");
        }
        Long proveedorId = proveedorDe(usuario);
        BigDecimal precio = suscripcionRepository.findByProveedorId(proveedorId)
                .map(SuscripcionProveedor::getPrecioMensual)
                .orElse(PlanPrismaPro.PRECIO_MENSUAL_CON_IVA);

        // HU-19: la llamada a la pasarela pasa por el circuit breaker. Si está abierto responde 503 de inmediato
        // y no se crea ningún registro de pago (la suscripción no cambia).
        PasarelaPagosSimulada.Resultado resultado = pasarelaResiliente.procesar(datos.getMedio(), datos);

        PagoSuscripcion pago = new PagoSuscripcion();
        pago.setProveedorId(proveedorId);
        pago.setUsuarioId(usuario.getId());
        pago.setPlan(PlanPrismaPro.CODIGO);
        pago.setReferencia(PasarelaPagosSimulada.generarCodigo("PRISMA-SUS-" + proveedorId + "-", 8));
        pago.setTransaccionId(resultado.transaccionId());
        pago.setPasarela(PasarelaPagosSimulada.NOMBRE);
        pago.setMedio(datos.getMedio());
        pago.setDetalleMedio(resultado.detalleMedio());
        pago.setMonto(precio);
        pago.setValorNeto(PlanPrismaPro.valorNeto(precio));
        pago.setValorIva(PlanPrismaPro.valorIva(precio));
        // Tarifa de Wompi por procesar el pago: independiente del valor de la suscripción
        pago.setTarifaPasarela(ReglasSplitPayment.tarifaPasarela(datos.getMedio(), precio));
        pago.setEstado(EstadoPagoSuscripcion.PENDIENTE);
        pago.setCreadoEn(OffsetDateTime.now());
        pago = pagoRepository.save(pago);

        // HU-14: la pasarela notifica el resultado con un webhook firmado (HMAC-SHA256 en el encabezado) que pasa
        // por la MISMA validación que POST /api/v1/pagos/webhooks/wompi.
        PasarelaPagosSimulada.NotificacionWebhook webhook =
                pasarela.notificarTransaccion(resultado, pago.getReferencia(), pago.getMonto(), pago.getMedio());
        webhookPagosService.recibir(webhook.cuerpo(), webhook.firma(), "pasarela-simulada", "PasarelaPagosSimulada");

        PagoSuscripcion confirmado = pagoRepository.findById(pago.getId()).orElse(pago);
        return PagoSuscripcionResponse.de(confirmado);
    }

    private Long proveedorDe(UserPrincipal usuario) {
        if (usuario == null || usuario.getProveedorId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El usuario no está asociado a ningún proveedor");
        }
        if (!proveedorRepository.existsById(usuario.getProveedorId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado");
        }
        return usuario.getProveedorId();
    }
}
