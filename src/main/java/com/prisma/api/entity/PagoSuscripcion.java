package com.prisma.api.entity;

import com.prisma.api.enums.EstadoPagoSuscripcion;
import com.prisma.api.enums.MedioPagoEnLinea;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Pago mensual de la suscripción Prisma Pro hecho por un proveedor en la pasarela SIMULADA (sandbox tipo Wompi).
 * Nunca se guardan datos sensibles: solo un detalle enmascarado (ej. "VISA •••• 4242").
 */
@Entity
@Table(name = "pagos_suscripcion", indexes = {
        @Index(name = "idx_pagos_suscripcion_proveedor", columnList = "proveedor_id")
})
public class PagoSuscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 30)
    private String plan;

    @Column(nullable = false, unique = true, length = 60)
    private String referencia;

    @Column(name = "transaccion_id", nullable = false, length = 40)
    private String transaccionId;

    @Column(nullable = false, length = 30)
    private String pasarela;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MedioPagoEnLinea medio;

    @Column(name = "detalle_medio", nullable = false, length = 80)
    private String detalleMedio;

    /** Valor pagado (IVA incluido). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "valor_neto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorNeto;

    @Column(name = "valor_iva", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorIva;

    /** Tarifa (simulada) que cobra Wompi por procesar; es independiente del valor de la suscripción. */
    @Column(name = "tarifa_pasarela", precision = 12, scale = 2)
    private BigDecimal tarifaPasarela;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoPagoSuscripcion estado;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    /** Período que cubre este pago (se completa cuando el webhook lo aprueba). */
    @Column(name = "periodo_desde")
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta")
    private LocalDate periodoHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "confirmado_en")
    private OffsetDateTime confirmadoEn;

    public PagoSuscripcion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getTransaccionId() { return transaccionId; }
    public void setTransaccionId(String transaccionId) { this.transaccionId = transaccionId; }

    public String getPasarela() { return pasarela; }
    public void setPasarela(String pasarela) { this.pasarela = pasarela; }

    public MedioPagoEnLinea getMedio() { return medio; }
    public void setMedio(MedioPagoEnLinea medio) { this.medio = medio; }

    public String getDetalleMedio() { return detalleMedio; }
    public void setDetalleMedio(String detalleMedio) { this.detalleMedio = detalleMedio; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public BigDecimal getValorNeto() { return valorNeto; }
    public void setValorNeto(BigDecimal valorNeto) { this.valorNeto = valorNeto; }

    public BigDecimal getValorIva() { return valorIva; }
    public void setValorIva(BigDecimal valorIva) { this.valorIva = valorIva; }

    public BigDecimal getTarifaPasarela() { return tarifaPasarela; }
    public void setTarifaPasarela(BigDecimal tarifaPasarela) { this.tarifaPasarela = tarifaPasarela; }

    public EstadoPagoSuscripcion getEstado() { return estado; }
    public void setEstado(EstadoPagoSuscripcion estado) { this.estado = estado; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public void setPeriodoDesde(LocalDate periodoDesde) { this.periodoDesde = periodoDesde; }

    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public void setPeriodoHasta(LocalDate periodoHasta) { this.periodoHasta = periodoHasta; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }

    public OffsetDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(OffsetDateTime confirmadoEn) { this.confirmadoEn = confirmadoEn; }
}
