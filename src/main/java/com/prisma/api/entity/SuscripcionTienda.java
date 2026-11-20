package com.prisma.api.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Suscripción mensual Prisma Pro para Tenderos de barrio.
 * Habilita el "Radar de mi Barrio" y sugerencias de IA para su tienda.
 */
@Entity
@Table(name = "suscripciones_tienda")
public class SuscripcionTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tienda_id", nullable = false, unique = true)
    private Long tiendaId;

    @Column(nullable = false, length = 30)
    private String plan;

    @Column(name = "precio_mensual", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "activada_en", nullable = false, updatable = false)
    private OffsetDateTime activadaEn = OffsetDateTime.now();

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn = OffsetDateTime.now();

    public SuscripcionTienda() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTiendaId() { return tiendaId; }
    public void setTiendaId(Long tiendaId) { this.tiendaId = tiendaId; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public BigDecimal getPrecioMensual() { return precioMensual; }
    public void setPrecioMensual(BigDecimal precioMensual) { this.precioMensual = precioMensual; }

    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }

    public OffsetDateTime getActivadaEn() { return activadaEn; }
    public void setActivadaEn(OffsetDateTime activadaEn) { this.activadaEn = activadaEn; }

    public OffsetDateTime getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(OffsetDateTime actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
