package com.prisma.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "liquidaciones_quincenales")
public class LiquidacionQuincenal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_liquidacion", nullable = false, unique = true, length = 30)
    private String numeroLiquidacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Proveedor proveedor;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fin", nullable = false)
    private LocalDate periodoFin;

    @Column(name = "total_pedidos_entregados", nullable = false)
    private int totalPedidosEntregados = 0;

    @Column(name = "total_ventas_brutas", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVentasBrutas;

    @Column(name = "porcentaje_comision", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeComision = new BigDecimal("2.50");

    @Column(name = "valor_comision_prisma", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorComisionPrisma;

    @Column(name = "valor_iva_comision", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorIvaComision = BigDecimal.ZERO;

    @Column(name = "total_facturado_prisma", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFacturadoPrisma;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "cufe_dian", length = 100)
    private String cufeDian;

    @Column(name = "fecha_pago")
    private OffsetDateTime fechaPago;

    @Column(name = "comprobante_bancario_ref", length = 100)
    private String comprobanteBancarioRef;

    @Column(name = "generado_en", nullable = false, updatable = false)
    private OffsetDateTime generadoEn = OffsetDateTime.now();

    public LiquidacionQuincenal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumeroLiquidacion() { return numeroLiquidacion; }
    public void setNumeroLiquidacion(String numeroLiquidacion) { this.numeroLiquidacion = numeroLiquidacion; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public LocalDate getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDate periodoInicio) { this.periodoInicio = periodoInicio; }

    public LocalDate getPeriodoFin() { return periodoFin; }
    public void setPeriodoFin(LocalDate periodoFin) { this.periodoFin = periodoFin; }

    public int getTotalPedidosEntregados() { return totalPedidosEntregados; }
    public void setTotalPedidosEntregados(int totalPedidosEntregados) { this.totalPedidosEntregados = totalPedidosEntregados; }

    public BigDecimal getTotalVentasBrutas() { return totalVentasBrutas; }
    public void setTotalVentasBrutas(BigDecimal totalVentasBrutas) { this.totalVentasBrutas = totalVentasBrutas; }

    public BigDecimal getPorcentajeComision() { return porcentajeComision; }
    public void setPorcentajeComision(BigDecimal porcentajeComision) { this.porcentajeComision = porcentajeComision; }

    public BigDecimal getValorComisionPrisma() { return valorComisionPrisma; }
    public void setValorComisionPrisma(BigDecimal valorComisionPrisma) { this.valorComisionPrisma = valorComisionPrisma; }

    public BigDecimal getValorIvaComision() { return valorIvaComision; }
    public void setValorIvaComision(BigDecimal valorIvaComision) { this.valorIvaComision = valorIvaComision; }

    public BigDecimal getTotalFacturadoPrisma() { return totalFacturadoPrisma; }
    public void setTotalFacturadoPrisma(BigDecimal totalFacturadoPrisma) { this.totalFacturadoPrisma = totalFacturadoPrisma; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCufeDian() { return cufeDian; }
    public void setCufeDian(String cufeDian) { this.cufeDian = cufeDian; }

    public OffsetDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(OffsetDateTime fechaPago) { this.fechaPago = fechaPago; }

    public String getComprobanteBancarioRef() { return comprobanteBancarioRef; }
    public void setComprobanteBancarioRef(String comprobanteBancarioRef) { this.comprobanteBancarioRef = comprobanteBancarioRef; }

    public OffsetDateTime getGeneradoEn() { return generadoEn; }
    public void setGeneradoEn(OffsetDateTime generadoEn) { this.generadoEn = generadoEn; }
}
