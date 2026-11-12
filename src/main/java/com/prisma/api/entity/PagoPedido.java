package com.prisma.api.entity;

import com.prisma.api.enums.EstadoPagoPedido;
import com.prisma.api.enums.MedioPagoEnLinea;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Transacción de pago en línea de un pedido, procesada por la pasarela SIMULADA (sandbox tipo Wompi).
 * Nunca se guardan datos sensibles: solo un detalle enmascarado (ej. "VISA •••• 4242").
 */
@Entity
@Table(name = "pagos_pedido", indexes = {
        @Index(name = "idx_pagos_pedido_pedido", columnList = "pedido_id"),
        @Index(name = "idx_pagos_pedido_proveedor", columnList = "proveedor_id")
})
public class PagoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "numero_pedido", nullable = false, length = 30)
    private String numeroPedido;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "tendero_id", nullable = false)
    private Long tenderoId;

    @Column(name = "nombre_tienda", length = 150)
    private String nombreTienda;

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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "comision_prisma", nullable = false, precision = 12, scale = 2)
    private BigDecimal comisionPrisma;

    @Column(name = "monto_proveedor", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoProveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoPagoPedido estado;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "liberado_en")
    private OffsetDateTime liberadoEn;

    public PagoPedido() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }

    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }

    public Long getTenderoId() { return tenderoId; }
    public void setTenderoId(Long tenderoId) { this.tenderoId = tenderoId; }

    public String getNombreTienda() { return nombreTienda; }
    public void setNombreTienda(String nombreTienda) { this.nombreTienda = nombreTienda; }

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

    public BigDecimal getComisionPrisma() { return comisionPrisma; }
    public void setComisionPrisma(BigDecimal comisionPrisma) { this.comisionPrisma = comisionPrisma; }

    public BigDecimal getMontoProveedor() { return montoProveedor; }
    public void setMontoProveedor(BigDecimal montoProveedor) { this.montoProveedor = montoProveedor; }

    public EstadoPagoPedido getEstado() { return estado; }
    public void setEstado(EstadoPagoPedido estado) { this.estado = estado; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }

    public OffsetDateTime getLiberadoEn() { return liberadoEn; }
    public void setLiberadoEn(OffsetDateTime liberadoEn) { this.liberadoEn = liberadoEn; }
}
