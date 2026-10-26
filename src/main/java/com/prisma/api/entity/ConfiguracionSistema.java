package com.prisma.api.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracion_sistema")
public class ConfiguracionSistema {

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(name = "tipo_dato", nullable = false, length = 20)
    private String tipoDato = "STRING";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 50)
    private String categoria = "GENERAL";

    @Column(name = "es_sensible", nullable = false)
    private boolean esSensible = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por_usuario_id")
    private Usuario actualizadoPor;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    public ConfiguracionSistema() {}

    public ConfiguracionSistema(String clave, String valor, String tipoDato, String descripcion, String categoria) {
        this.clave = clave;
        this.valor = valor;
        this.tipoDato = tipoDato;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.actualizadoEn = OffsetDateTime.now();
    }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getTipoDato() { return tipoDato; }
    public void setTipoDato(String tipoDato) { this.tipoDato = tipoDato; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public boolean isEsSensible() { return esSensible; }
    public void setEsSensible(boolean esSensible) { this.esSensible = esSensible; }

    public Usuario getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(Usuario actualizadoPor) { this.actualizadoPor = actualizadoPor; }

    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(OffsetDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
