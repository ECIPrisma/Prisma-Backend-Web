package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rastreo_camion")
public class RastreoCamion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id", nullable = false)
    private Usuario repartidor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id", nullable = false)
    private Ruta ruta;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(name = "velocidad_kmh", precision = 5, scale = 2)
    private BigDecimal velocidadKmh;

    @Column(name = "rumbo_grados", precision = 5, scale = 2)
    private BigDecimal rumboGrados;

    @Column(nullable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public RastreoCamion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Camion getCamion() { return camion; }
    public void setCamion(Camion camion) { this.camion = camion; }

    public Usuario getRepartidor() { return repartidor; }
    public void setRepartidor(Usuario repartidor) { this.repartidor = repartidor; }

    public Ruta getRuta() { return ruta; }
    public void setRuta(Ruta ruta) { this.ruta = ruta; }

    public BigDecimal getLatitud() { return latitud; }
    public void setLatitud(BigDecimal latitud) { this.latitud = latitud; }

    public BigDecimal getLongitud() { return longitud; }
    public void setLongitud(BigDecimal longitud) { this.longitud = longitud; }

    public BigDecimal getVelocidadKmh() { return velocidadKmh; }
    public void setVelocidadKmh(BigDecimal velocidadKmh) { this.velocidadKmh = velocidadKmh; }

    public BigDecimal getRumboGrados() { return rumboGrados; }
    public void setRumboGrados(BigDecimal rumboGrados) { this.rumboGrados = rumboGrados; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}
