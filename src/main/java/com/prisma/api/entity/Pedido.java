package com.prisma.api.entity;

import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_pedido", nullable = false, unique = true, length = 30)
    private String numeroPedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "tendero"})
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruta_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Ruta ruta;

    @Column(name = "orden_parada")
    private Integer ordenParada = 0;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(name = "total_a_pagar", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAPagar;

    @Column(name = "comision_prisma_valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal comisionPrismaValor = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoPedido estado = EstadoPedido.CREADO;

    @Column(name = "pin_entrega_hash", nullable = false, length = 10)
    private String pinEntregaHash;

    @Column(name = "pin_validado_en")
    private OffsetDateTime pinValidadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago_utilizado", nullable = false, length = 25)
    private MedioPago medioPagoUtilizado = MedioPago.EFECTIVO;

    @Column(name = "notas_entrega", columnDefinition = "TEXT")
    private String notasEntrega;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "fecha_estimada_entrega", nullable = false)
    private LocalDate fechaEstimadaEntrega;

    @Column(name = "fecha_entrega_real")
    private OffsetDateTime fechaEntregaReal;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pedido"})
    private List<ItemPedido> items = new ArrayList<>();

    public Pedido() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public Tienda getTienda() { return tienda; }
    public void setTienda(Tienda tienda) { this.tienda = tienda; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Ruta getRuta() { return ruta; }
    public void setRuta(Ruta ruta) { this.ruta = ruta; }

    public Integer getOrdenParada() { return ordenParada; }
    public void setOrdenParada(Integer ordenParada) { this.ordenParada = ordenParada; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public BigDecimal getTotalAPagar() { return totalAPagar; }
    public void setTotalAPagar(BigDecimal totalAPagar) { this.totalAPagar = totalAPagar; }

    public BigDecimal getComisionPrismaValor() { return comisionPrismaValor; }
    public void setComisionPrismaValor(BigDecimal comisionPrismaValor) { this.comisionPrismaValor = comisionPrismaValor; }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }

    public String getPinEntregaHash() { return pinEntregaHash; }
    public void setPinEntregaHash(String pinEntregaHash) { this.pinEntregaHash = pinEntregaHash; }

    public OffsetDateTime getPinValidadoEn() { return pinValidadoEn; }
    public void setPinValidadoEn(OffsetDateTime pinValidadoEn) { this.pinValidadoEn = pinValidadoEn; }

    public MedioPago getMedioPagoUtilizado() { return medioPagoUtilizado; }
    public void setMedioPagoUtilizado(MedioPago medioPagoUtilizado) { this.medioPagoUtilizado = medioPagoUtilizado; }

    public String getNotasEntrega() { return notasEntrega; }
    public void setNotasEntrega(String notasEntrega) { this.notasEntrega = notasEntrega; }

    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDate getFechaEstimadaEntrega() { return fechaEstimadaEntrega; }
    public void setFechaEstimadaEntrega(LocalDate fechaEstimadaEntrega) { this.fechaEstimadaEntrega = fechaEstimadaEntrega; }

    public OffsetDateTime getFechaEntregaReal() { return fechaEntregaReal; }
    public void setFechaEntregaReal(OffsetDateTime fechaEntregaReal) { this.fechaEntregaReal = fechaEntregaReal; }

    public OffsetDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(OffsetDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }

    public List<ItemPedido> getItems() { return items; }
    public void setItems(List<ItemPedido> items) { this.items = items; }

    public void addItem(ItemPedido item) {
        items.add(item);
        item.setPedido(this);
    }
}
