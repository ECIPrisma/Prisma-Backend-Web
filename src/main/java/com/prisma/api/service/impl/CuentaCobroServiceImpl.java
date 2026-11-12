package com.prisma.api.service.impl;

import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.VincularCuentaCobroRequest;
import com.prisma.api.entity.CuentaCobroProveedor;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.exception.ApiException;
import com.prisma.api.pagos.PasarelaPagosSimulada;
import com.prisma.api.repository.CuentaCobroProveedorRepository;
import com.prisma.api.repository.ProveedorRepository;
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
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * HU-12 – Vinculación (SIMULADA) de la cuenta de cobro del proveedor con la pasarela.
 *
 * <p>Regla de la simulación para demostrar el criterio de rechazo: si el número de cuenta termina en
 * {@value #SUFIJO_CUENTA_RECHAZADA}, la pasarela "no puede verificar" la cuenta y la vinculación falla.</p>
 */
@Service
public class CuentaCobroServiceImpl implements CuentaCobroService {

    private static final Logger logger = LoggerFactory.getLogger(CuentaCobroServiceImpl.class);
    public static final String SUFIJO_CUENTA_RECHAZADA = "0000";
    private static final Set<String> TIPOS_CUENTA = Set.of("AHORROS", "CORRIENTE");
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final CuentaCobroProveedorRepository cuentaRepository;
    private final ProveedorRepository proveedorRepository;

    public CuentaCobroServiceImpl(CuentaCobroProveedorRepository cuentaRepository,
                                  ProveedorRepository proveedorRepository) {
        this.cuentaRepository = cuentaRepository;
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
    public CuentaCobroResponse vincular(UserPrincipal usuario, VincularCuentaCobroRequest datos) {
        Proveedor proveedor = proveedorDe(usuario);
        if (cuentaRepository.existsByProveedorId(proveedor.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya tienes una cuenta de cobro vinculada. Desvincúlala para conectar otra.");
        }

        String banco = datos.getBanco() == null ? "" : datos.getBanco().trim();
        String tipoCuenta = datos.getTipoCuenta() == null ? "" : datos.getTipoCuenta().trim().toUpperCase();
        String numero = datos.getNumeroCuenta() == null ? "" : datos.getNumeroCuenta().replaceAll("\\D", "");
        String titular = datos.getTitular() == null ? "" : datos.getTitular().trim();

        if (!StringUtils.hasText(banco) || banco.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecciona el banco de la cuenta");
        }
        if (!TIPOS_CUENTA.contains(tipoCuenta)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El tipo de cuenta debe ser AHORROS o CORRIENTE");
        }
        if (numero.length() < 6 || numero.length() > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El número de cuenta debe tener entre 6 y 20 dígitos");
        }
        if (titular.length() < 3 || titular.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ingresa el nombre del titular de la cuenta");
        }

        // Criterio 2 (simulado): la pasarela no logra verificar la cuenta
        if (numero.endsWith(SUFIJO_CUENTA_RECHAZADA)) {
            logger.info("Vinculación simulada rechazada para proveedor {}", proveedor.getId());
            throw new ApiException(HttpStatus.BAD_REQUEST, MENSAJE_VINCULACION_FALLIDA);
        }

        CuentaCobroProveedor cuenta = new CuentaCobroProveedor();
        cuenta.setProveedor(proveedor);
        cuenta.setPasarela(PasarelaPagosSimulada.NOMBRE);
        cuenta.setCuentaExternaId(PasarelaPagosSimulada.generarCodigo("WMP-SBX-", 8));
        cuenta.setBanco(banco);
        cuenta.setTipoCuenta(tipoCuenta);
        cuenta.setNumeroCuentaUltimos4(numero.substring(numero.length() - 4));
        cuenta.setTitular(titular);
        cuenta.setModoProduccion(false);
        cuenta.setVinculadaEn(OffsetDateTime.now());
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
        BigDecimal comisionPorcentaje = comisionDe(proveedor);
        BigDecimal comision = total.multiply(comisionPorcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
        return new DivisionPago(total, comision, total.subtract(comision), CIEN.subtract(comisionPorcentaje));
    }

    // ---------------------------------------------------------------------------------------------

    private CuentaCobroResponse construirEstado(Proveedor proveedor, CuentaCobroProveedor cuenta) {
        BigDecimal comision = comisionDe(proveedor);
        return CuentaCobroResponse.de(cuenta, PasarelaPagosSimulada.NOMBRE, comision, CIEN.subtract(comision));
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
}
