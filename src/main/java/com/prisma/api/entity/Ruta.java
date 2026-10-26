package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rutas")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;

    @Column(name = "codigo_ruta", nullable = false, unique = true, length = 30)
    private String codigoRuta;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDate fechaProgramada;

    @Column(name = "zona_sector", nullable = false, length = 100)
    private String zonaSector;

    @Column(name = "total_paradas", nullable = false)
    private int totalParadas = 0;

    @Column(name = "total_recaudo_esperado", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRecaudoEsperado = BigDecimal.ZERO;

    @Column(name = "total_recaudo_efectivo", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRecaudoEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, length = 25)
    private String estado = "PLANIFICADA";

    @Column(name = "hora_inicio_real")
    private OffsetDateTime horaInicioReal;

    @Column(name = "hora_fin_real")
    private OffsetDateTime horaFinReal;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public Ruta() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Camion getCamion() { return camion; }
    public void setCamion(Camion camion) { this.camion = camion; }

    public Usuario getRepartidor() { return repartidor; }
    public void setRepartidor(Usuario repartidor) { this.repartidor = repartidor; }

    public String getCodigoRuta() { return codigoRuta; }
    public void setCodigoRuta(String codigoRuta) { this.codigoRuta = codigoRuta; }

    public LocalDate getFechaProgramada() { return fechaProgramada; }
    public void setFechaProgramada(LocalDate fechaProgramada) { this.fechaProgramada = fechaProgramada; }

    public String getZonaSector() { return zonaSector; }
    public void setZonaSector(String zonaSector) { this.zonaSector = zonaSector; }

    public int getTotalParadas() { return totalParadas; }
    public void setTotalParadas(int totalParadas) { this.totalParadas = totalParadas; }

    public BigDecimal getTotalRecaudoEsperado() { return totalRecaudoEsperado; }
    public void setTotalRecaudoEsperado(BigDecimal totalRecaudoEsperado) { this.totalRecaudoEsperado = totalRecaudoEsperado; }

    public BigDecimal getTotalRecaudoEfectivo() { return totalRecaudoEfectivo; }
    public void setTotalRecaudoEfectivo(BigDecimal totalRecaudoEfectivo) { this.totalRecaudoEfectivo = totalRecaudoEfectivo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getHoraInicioReal() { return horaInicioReal; }
    public void setHoraInicioReal(OffsetDateTime horaInicioReal) { this.horaInicioReal = horaInicioReal; }

    public OffsetDateTime getHoraFinReal() { return horaFinReal; }
    public void setHoraFinReal(OffsetDateTime horaFinReal) { this.horaFinReal = horaFinReal; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
