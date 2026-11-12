package com.prisma.api.service.impl;

import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.VinculacionCallbackRequest;
import com.prisma.api.entity.CuentaCobroProveedor;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.SolicitudVinculacionPago;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.MercadoPagoOAuthClient;
import com.prisma.api.pagos.MercadoPagoTokens;
import com.prisma.api.repository.CuentaCobroProveedorRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.SolicitudVinculacionPagoRepository;
import com.prisma.api.security.CifradorSecretos;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.CuentaCobroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * HU-12 – Vinculación de la cuenta de cobro del proveedor con Mercado Pago (OAuth + marketplace split).
 */
@Service
public class CuentaCobroServiceImpl implements CuentaCobroService {

    private static final Logger logger = LoggerFactory.getLogger(CuentaCobroServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASARELA = "MERCADO_PAGO";
    private static final BigDecimal CIEN = new BigDecimal("100");
    // El authorization code de Mercado Pago vale 10 minutos; el intento expira al mismo tiempo
    private static final long MINUTOS_VIGENCIA_SOLICITUD = 10;
    // Se renueva el access token si le quedan menos de 7 días
    private static final long DIAS_MARGEN_RENOVACION = 7;

    private final MercadoPagoOAuthClient mercadoPago;
    private final CifradorSecretos cifrador;
    private final CuentaCobroProveedorRepository cuentaRepository;
    private final SolicitudVinculacionPagoRepository solicitudRepository;
    private final ProveedorRepository proveedorRepository;

    public CuentaCobroServiceImpl(MercadoPagoOAuthClient mercadoPago, CifradorSecretos cifrador,
                                  CuentaCobroProveedorRepository cuentaRepository,
                                  SolicitudVinculacionPagoRepository solicitudRepository,
                                  ProveedorRepository proveedorRepository) {
        this.mercadoPago = mercadoPago;
        this.cifrador = cifrador;
        this.cuentaRepository = cuentaRepository;
        this.solicitudRepository = solicitudRepository;
        this.proveedorRepository = proveedorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CuentaCobroResponse obtenerEstado(UserPrincipal usuario) {
        Proveedor proveedor = proveedorDe(usuario);
        return construirEstado(proveedor, cuentaRepository.findByProveedorId(proveedor.getId()).orElse(null));
    }

    @Override
    @Transactional
    public String iniciarVinculacion(UserPrincipal usuario) {
        exigirConfiguracion();
        Proveedor proveedor = proveedorDe(usuario);
        if (cuentaRepository.existsByProveedorId(proveedor.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes una cuenta de cobro vinculada. Desvincúlala para conectar otra.");
        }

        String state = aleatorioBase64Url(32);
        String codeVerifier = aleatorioBase64Url(48); // 64 caracteres: dentro del rango 43-128 de PKCE
        solicitudRepository.save(new SolicitudVinculacionPago(state, proveedor.getId(), usuario.getId(), codeVerifier,
                OffsetDateTime.now().plusMinutes(MINUTOS_VIGENCIA_SOLICITUD)));

        return mercadoPago.urlAutorizacion(state, codeChallengeS256(codeVerifier));
    }

    /**
     * El intento se marca como usado aunque falle (rechazo, state inválido o error de la pasarela), por eso
     * las ApiException no deshacen la transacción. La cuenta solo se guarda cuando Mercado Pago entrega el token.
     */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public CuentaCobroResponse completarVinculacion(UserPrincipal usuario, VinculacionCallbackRequest callback) {
        exigirConfiguracion();
        Proveedor proveedor = proveedorDe(usuario);

        SolicitudVinculacionPago solicitud = StringUtils.hasText(callback.getState())
                ? solicitudRepository.findByState(callback.getState()).orElse(null)
                : null;
        boolean solicitudValida = solicitud != null && solicitud.isVigente()
                && solicitud.getProveedorId().equals(proveedor.getId())
                && solicitud.getUsuarioId().equals(usuario.getId());
        if (solicitud != null && solicitud.getProveedorId().equals(proveedor.getId())) {
            solicitud.setUsada(true);
            solicitudRepository.save(solicitud);
        }

        // Criterio 2: si el proveedor canceló o rechazó, Mercado Pago no devuelve "code"
        if (!solicitudValida || StringUtils.hasText(callback.getError()) || !StringUtils.hasText(callback.getCode())) {
            logger.info("Vinculación de cuenta de cobro no completada para proveedor {} (state válido: {}, error: {})",
                    proveedor.getId(), solicitudValida, callback.getError());
            throw new ApiException(HttpStatus.BAD_REQUEST, MENSAJE_VINCULACION_FALLIDA);
        }
        if (cuentaRepository.existsByProveedorId(proveedor.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes una cuenta de cobro vinculada.");
        }

        MercadoPagoTokens tokens = mercadoPago.intercambiarCodigo(callback.getCode(), solicitud.getCodeVerifier());

        CuentaCobroProveedor cuenta = new CuentaCobroProveedor();
        cuenta.setProveedor(proveedor);
        cuenta.setPasarela(PASARELA);
        cuenta.setVinculadaEn(OffsetDateTime.now());
        aplicarTokens(cuenta, tokens);
        cuentaRepository.save(cuenta);

        return construirEstado(proveedor, cuenta);
    }

    @Override
    @Transactional
    public void desvincular(UserPrincipal usuario) {
        Proveedor proveedor = proveedorDe(usuario);
        CuentaCobroProveedor cuenta = cuentaRepository.findByProveedorId(proveedor.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No tienes una cuenta de cobro vinculada."));
        cuentaRepository.delete(cuenta);
    }

    @Override
    @Transactional(readOnly = true)
    public DivisionPago calcularDivision(Long proveedorId, BigDecimal total) {
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
        if (!cuentaRepository.existsByProveedorId(proveedorId)) {
            throw new ApiException(HttpStatus.CONFLICT, "El proveedor no tiene una cuenta de cobro vinculada.");
        }
        BigDecimal comisionPorcentaje = comisionDe(proveedor);
        BigDecimal comision = total.multiply(comisionPorcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
        return new DivisionPago(total, comision, total.subtract(comision), CIEN.subtract(comisionPorcentaje));
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public String obtenerAccessTokenVigente(Long proveedorId) {
        CuentaCobroProveedor cuenta = cuentaRepository.findByProveedorId(proveedorId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "El proveedor no tiene una cuenta de cobro vinculada."));

        boolean porVencer = cuenta.getTokenExpiraEn() != null
                && cuenta.getTokenExpiraEn().isBefore(OffsetDateTime.now().plusDays(DIAS_MARGEN_RENOVACION));
        if (porVencer && StringUtils.hasText(cuenta.getRefreshTokenCifrado())) {
            // Mercado Pago rota el refresh token en cada renovación: se guardan ambos de nuevo
            MercadoPagoTokens nuevos = mercadoPago.renovar(cifrador.descifrar(cuenta.getRefreshTokenCifrado()));
            aplicarTokens(cuenta, nuevos);
            cuentaRepository.save(cuenta);
        }
        return cifrador.descifrar(cuenta.getAccessTokenCifrado());
    }

    // ---------------------------------------------------------------------------------------------

    private void aplicarTokens(CuentaCobroProveedor cuenta, MercadoPagoTokens tokens) {
        cuenta.setCuentaExternaId(String.valueOf(tokens.userId()));
        cuenta.setAccessTokenCifrado(cifrador.cifrar(tokens.accessToken()));
        cuenta.setRefreshTokenCifrado(StringUtils.hasText(tokens.refreshToken()) ? cifrador.cifrar(tokens.refreshToken()) : null);
        cuenta.setPublicKey(tokens.publicKey());
        cuenta.setScope(tokens.scope());
        cuenta.setModoProduccion(tokens.liveMode());
        cuenta.setTokenExpiraEn(tokens.expiresIn() != null ? OffsetDateTime.now().plusSeconds(tokens.expiresIn()) : null);
        cuenta.setActualizadaEn(OffsetDateTime.now());
    }

    private CuentaCobroResponse construirEstado(Proveedor proveedor, CuentaCobroProveedor cuenta) {
        BigDecimal comision = comisionDe(proveedor);
        boolean disponible = mercadoPago.estaConfigurado() && cifrador.estaConfigurado();
        if (cuenta == null) {
            return new CuentaCobroResponse(disponible, false, PASARELA, null, false, null, comision, CIEN.subtract(comision));
        }
        return new CuentaCobroResponse(disponible, true, cuenta.getPasarela(), cuenta.getCuentaExternaId(),
                cuenta.isModoProduccion(), cuenta.getVinculadaEn(), comision, CIEN.subtract(comision));
    }

    private Proveedor proveedorDe(UserPrincipal usuario) {
        if (usuario == null || usuario.getProveedorId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El usuario no está asociado a ningún proveedor");
        }
        return proveedorRepository.findById(usuario.getProveedorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private static BigDecimal comisionDe(Proveedor proveedor) {
        return proveedor.getComisionPorcentaje() != null ? proveedor.getComisionPorcentaje() : new BigDecimal("2.50");
    }

    private void exigirConfiguracion() {
        if (!mercadoPago.estaConfigurado() || !cifrador.estaConfigurado()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La vinculación con Mercado Pago no está configurada en el servidor.");
        }
    }

    private static String aleatorioBase64Url(int bytes) {
        byte[] datos = new byte[bytes];
        SECURE_RANDOM.nextBytes(datos);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }

    static String codeChallengeS256(String codeVerifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
