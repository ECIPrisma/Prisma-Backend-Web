package com.prisma.api.dto;

import com.prisma.api.entity.CuentaCobroProveedor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Estado de la cuenta de cobro del proveedor (HU-12). Solo expone datos enmascarados.
 */
public class CuentaCobroResponse {

    private final boolean disponible = true;
    private final boolean simulada = true;
    private final boolean vinculada;
    private final boolean habilitadoPagosDivididos;
    private final String pasarela;
    private final String cuentaExternaId;
    private final String banco;
    private final String tipoCuenta;
    private final String numeroCuentaEnmascarado;
    private final String titular;
    private final boolean modoProduccion;
    private final OffsetDateTime vinculadaEn;
    private final BigDecimal comisionPrismaPorcentaje;
    private final BigDecimal porcentajeProveedor;

    private CuentaCobroResponse(CuentaCobroProveedor cuenta, String pasarela,
                                BigDecimal comisionPrismaPorcentaje, BigDecimal porcentajeProveedor) {
        this.vinculada = cuenta != null;
        this.habilitadoPagosDivididos = cuenta != null;
        this.pasarela = cuenta != null ? cuenta.getPasarela() : pasarela;
        this.cuentaExternaId = cuenta != null ? cuenta.getCuentaExternaId() : null;
        this.banco = cuenta != null ? cuenta.getBanco() : null;
        this.tipoCuenta = cuenta != null ? cuenta.getTipoCuenta() : null;
        this.numeroCuentaEnmascarado = cuenta != null ? "•••• " + cuenta.getNumeroCuentaUltimos4() : null;
        this.titular = cuenta != null ? cuenta.getTitular() : null;
        this.modoProduccion = cuenta != null && cuenta.isModoProduccion();
        this.vinculadaEn = cuenta != null ? cuenta.getVinculadaEn() : null;
        this.comisionPrismaPorcentaje = comisionPrismaPorcentaje;
        this.porcentajeProveedor = porcentajeProveedor;
    }

    public static CuentaCobroResponse de(CuentaCobroProveedor cuenta, String pasarela,
                                         BigDecimal comisionPrismaPorcentaje, BigDecimal porcentajeProveedor) {
        return new CuentaCobroResponse(cuenta, pasarela, comisionPrismaPorcentaje, porcentajeProveedor);
    }

    public boolean isDisponible() { return disponible; }
    public boolean isSimulada() { return simulada; }
    public boolean isVinculada() { return vinculada; }
    public boolean isHabilitadoPagosDivididos() { return habilitadoPagosDivididos; }
    public String getPasarela() { return pasarela; }
    public String getCuentaExternaId() { return cuentaExternaId; }
    public String getBanco() { return banco; }
    public String getTipoCuenta() { return tipoCuenta; }
    public String getNumeroCuentaEnmascarado() { return numeroCuentaEnmascarado; }
    public String getTitular() { return titular; }
    public boolean isModoProduccion() { return modoProduccion; }
    public OffsetDateTime getVinculadaEn() { return vinculadaEn; }
    public BigDecimal getComisionPrismaPorcentaje() { return comisionPrismaPorcentaje; }
    public BigDecimal getPorcentajeProveedor() { return porcentajeProveedor; }
}
