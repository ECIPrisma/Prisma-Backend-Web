package com.prisma.api.entity;

import com.prisma.api.enums.TipoIncidente;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "disputas")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Disputa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_numero", nullable = false, unique = true, length = 30)
    private String ticketNumero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "ruta", "proveedor"})
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportado_por_usuario_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash"})
    private Usuario reportadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agente_resolutor_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash"})
    private Usuario agenteResolutor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_incidente", nullable = false, length = 50)
    private TipoIncidente tipoIncidente;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 25)
    private String estado = "ABIERTO";

    @Column(name = "resolucion_justificacion", columnDefinition = "TEXT")
    private String resolucionJustificacion;

    @Column(name = "fecha_reporte", nullable = false, updatable = false)
    private OffsetDateTime fechaReporte = OffsetDateTime.now();

    @Column(name = "fecha_resolucion")
    private OffsetDateTime fechaResolucion;

    public Disputa() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicketNumero() { return ticketNumero; }
    public void setTicketNumero(String ticketNumero) { this.ticketNumero = ticketNumero; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public Usuario getReportadoPor() { return reportadoPor; }
    public void setReportadoPor(Usuario reportadoPor) { this.reportadoPor = reportadoPor; }

    public Usuario getAgenteResolutor() { return agenteResolutor; }
    public void setAgenteResolutor(Usuario agenteResolutor) { this.agenteResolutor = agenteResolutor; }

    public TipoIncidente getTipoIncidente() { return tipoIncidente; }
    public void setTipoIncidente(TipoIncidente tipoIncidente) { this.tipoIncidente = tipoIncidente; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getResolucionJustificacion() { return resolucionJustificacion; }
    public void setResolucionJustificacion(String resolucionJustificacion) { this.resolucionJustificacion = resolucionJustificacion; }

    public OffsetDateTime getFechaReporte() { return fechaReporte; }
    public void setFechaReporte(OffsetDateTime fechaReporte) { this.fechaReporte = fechaReporte; }

    public OffsetDateTime getFechaResolucion() { return fechaResolucion; }
    public void setFechaResolucion(OffsetDateTime fechaResolucion) { this.fechaResolucion = fechaResolucion; }
}
