package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tiendas")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tendero_id", nullable = false)
    private Usuario tendero;

    @Column(name = "nombre_tienda", nullable = false, length = 120)
    private String nombreTienda;

    @Column(name = "nit_o_cedula", nullable = false, unique = true, length = 25)
    private String nitOCedula;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Column(nullable = false, length = 100)
    private String barrio;

    @Column(nullable = false, length = 80)
    private String ciudad = "Bogotá";

    @Column(name = "telefono_tienda", nullable = false, length = 20)
    private String telefonoTienda;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(name = "foto_fachada_url", length = 500)
    private String fotoFachadaUrl;

    @Column(name = "dia_preferido_entrega", length = 15)
    private String diaPreferidoEntrega;

    @Column(name = "horario_atencion", length = 80)
    private String horarioAtencion = "07:00 - 21:00";

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public Tienda() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getTendero() { return tendero; }
    public void setTendero(Usuario tendero) { this.tendero = tendero; }

    public String getNombreTienda() { return nombreTienda; }
    public void setNombreTienda(String nombreTienda) { this.nombreTienda = nombreTienda; }

    public String getNitOCedula() { return nitOCedula; }
    public void setNitOCedula(String nitOCedula) { this.nitOCedula = nitOCedula; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getTelefonoTienda() { return telefonoTienda; }
    public void setTelefonoTienda(String telefonoTienda) { this.telefonoTienda = telefonoTienda; }

    public BigDecimal getLatitud() { return latitud; }
    public void setLatitud(BigDecimal latitud) { this.latitud = latitud; }

    public BigDecimal getLongitud() { return longitud; }
    public void setLongitud(BigDecimal longitud) { this.longitud = longitud; }

    public String getFotoFachadaUrl() { return fotoFachadaUrl; }
    public void setFotoFachadaUrl(String fotoFachadaUrl) { this.fotoFachadaUrl = fotoFachadaUrl; }

    public String getDiaPreferidoEntrega() { return diaPreferidoEntrega; }
    public void setDiaPreferidoEntrega(String diaPreferidoEntrega) { this.diaPreferidoEntrega = diaPreferidoEntrega; }

    public String getHorarioAtencion() { return horarioAtencion; }
    public void setHorarioAtencion(String horarioAtencion) { this.horarioAtencion = horarioAtencion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
