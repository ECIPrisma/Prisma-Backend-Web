package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "productos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"proveedor_id", "sku"})
})
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Categoria categoria;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(name = "codigo_barras_ean", length = 30)
    private String codigoBarrasEan;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String presentacion;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "precio_sugerido_publico", precision = 12, scale = 2)
    private BigDecimal precioSugeridoPublico;

    @Column(name = "stock_disponible", nullable = false)
    private int stockDisponible = 0;

    @Column(name = "cantidad_minima_pedido", nullable = false)
    private int cantidadMinimaPedido = 1;

    @Column(name = "es_retornable", nullable = false)
    private boolean esRetornable = false;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public Producto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getCodigoBarrasEan() { return codigoBarrasEan; }
    public void setCodigoBarrasEan(String codigoBarrasEan) { this.codigoBarrasEan = codigoBarrasEan; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getPresentacion() { return presentacion; }
    public void setPresentacion(String presentacion) { this.presentacion = presentacion; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getPrecioSugeridoPublico() { return precioSugeridoPublico; }
    public void setPrecioSugeridoPublico(BigDecimal precioSugeridoPublico) { this.precioSugeridoPublico = precioSugeridoPublico; }

    public int getStockDisponible() { return stockDisponible; }
    public void setStockDisponible(int stockDisponible) { this.stockDisponible = stockDisponible; }

    public int getCantidadMinimaPedido() { return cantidadMinimaPedido; }
    public void setCantidadMinimaPedido(int cantidadMinimaPedido) { this.cantidadMinimaPedido = cantidadMinimaPedido; }

    public boolean isEsRetornable() { return esRetornable; }
    public void setEsRetornable(boolean esRetornable) { this.esRetornable = esRetornable; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
