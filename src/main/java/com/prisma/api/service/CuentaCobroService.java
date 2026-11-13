package com.prisma.api.service;

import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.VincularCuentaCobroRequest;
import com.prisma.api.security.UserPrincipal;

import java.math.BigDecimal;

/**
 * HU-12 – Cuenta de cobro del proveedor en la pasarela de pagos (vinculación SIMULADA).
 */
public interface CuentaCobroService {
    String MENSAJE_VINCULACION_FALLIDA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";

    CuentaCobroResponse obtenerEstado(UserPrincipal usuario);
    CuentaCobroResponse vincular(UserPrincipal usuario, VincularCuentaCobroRequest datos);
    void desvincular(UserPrincipal usuario);
    DivisionPago calcularDivision(Long proveedorId, BigDecimal total);
}
