package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "camiones")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Camion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Proveedor proveedor;

    @Column(nullable = false, unique = true, length = 10)
    private String placa;

    @Column(name = "marca_modelo", nullable = false, length = 80)
    private String marcaModelo;

    @Column(name = "capacidad_peso_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal capacidadPesoKg;

    @Column(name = "capacidad_canastas", nullable = false)
    private int capacidadCanastas;

    @Column(nullable = false, length = 20)
    private String estado = "DISPONIBLE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "proveedor", "passwordHash"})
    private Usuario repartidor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public Camion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getRepartidor() { return repartidor; }
    public void setRepartidor(Usuario repartidor) { this.repartidor = repartidor; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public String getMarcaModelo() { return marcaModelo; }
    public void setMarcaModelo(String marcaModelo) { this.marcaModelo = marcaModelo; }

    public BigDecimal getCapacidadPesoKg() { return capacidadPesoKg; }
    public void setCapacidadPesoKg(BigDecimal capacidadPesoKg) { this.capacidadPesoKg = capacidadPesoKg; }

    public int getCapacidadCanastas() { return capacidadCanastas; }
    public void setCapacidadCanastas(int capacidadCanastas) { this.capacidadCanastas = capacidadCanastas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
