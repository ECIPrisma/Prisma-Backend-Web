package com.prisma.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Estado de la cuenta de cobro del proveedor (HU-12). Nunca incluye tokens.
 */
public class CuentaCobroResponse {

    private final boolean disponible;
    private final boolean vinculada;
    private final boolean habilitadoPagosDivididos;
    private final String pasarela;
    private final String cuentaExternaId;
    private final boolean modoProduccion;
    private final OffsetDateTime vinculadaEn;
    private final BigDecimal comisionPrismaPorcentaje;
    private final BigDecimal porcentajeProveedor;

    public CuentaCobroResponse(boolean disponible, boolean vinculada, String pasarela, String cuentaExternaId,
                               boolean modoProduccion, OffsetDateTime vinculadaEn,
                               BigDecimal comisionPrismaPorcentaje, BigDecimal porcentajeProveedor) {
        this.disponible = disponible;
        this.vinculada = vinculada;
        this.habilitadoPagosDivididos = vinculada;
        this.pasarela = pasarela;
        this.cuentaExternaId = cuentaExternaId;
        this.modoProduccion = modoProduccion;
        this.vinculadaEn = vinculadaEn;
        this.comisionPrismaPorcentaje = comisionPrismaPorcentaje;
        this.porcentajeProveedor = porcentajeProveedor;
    }

    public boolean isDisponible() { return disponible; }
    public boolean isVinculada() { return vinculada; }
    public boolean isHabilitadoPagosDivididos() { return habilitadoPagosDivididos; }
    public String getPasarela() { return pasarela; }
    public String getCuentaExternaId() { return cuentaExternaId; }
    public boolean isModoProduccion() { return modoProduccion; }
    public OffsetDateTime getVinculadaEn() { return vinculadaEn; }
    public BigDecimal getComisionPrismaPorcentaje() { return comisionPrismaPorcentaje; }
    public BigDecimal getPorcentajeProveedor() { return porcentajeProveedor; }
}
