package com.prisma.api.service;

import com.prisma.api.dto.CuentaCobroResponse;
import com.prisma.api.dto.DivisionPago;
import com.prisma.api.dto.VinculacionCallbackRequest;
import com.prisma.api.security.UserPrincipal;

import java.math.BigDecimal;

public interface CuentaCobroService {
    String MENSAJE_VINCULACION_FALLIDA = "No se pudo vincular tu cuenta de cobro, intenta nuevamente";

    CuentaCobroResponse obtenerEstado(UserPrincipal usuario);
    String iniciarVinculacion(UserPrincipal usuario);
    CuentaCobroResponse completarVinculacion(UserPrincipal usuario, VinculacionCallbackRequest callback);
    void desvincular(UserPrincipal usuario);
    DivisionPago calcularDivision(Long proveedorId, BigDecimal total);
    String obtenerAccessTokenVigente(Long proveedorId);
}
