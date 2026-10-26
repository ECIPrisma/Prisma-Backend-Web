package com.prisma.api.entity;

import com.prisma.api.enums.SeveridadAudit;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime timestamp = OffsetDateTime.now();

    @Column(name = "agente_id", nullable = false)
    private Long agenteId;

    @Column(name = "agente_nombre", nullable = false, length = 150)
    private String agenteNombre;

    @Column(name = "agente_email", nullable = false, length = 150)
    private String agenteEmail;

    @Column(name = "agente_rol", nullable = false, length = 50)
    private String agenteRol;

    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_severidad", nullable = false, length = 20)
    private SeveridadAudit nivelSeveridad = SeveridadAudit.INFO;

    @Column(name = "recurso_afectado_tipo", nullable = false, length = 50)
    private String recursoAfectadoTipo;

    @Column(name = "recurso_afectado_id", nullable = false, length = 100)
    private String recursoAfectadoId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justificacion;

    @Column(name = "valores_anteriores", columnDefinition = "TEXT")
    private String valoresAnteriores;

    @Column(name = "valores_nuevos", columnDefinition = "TEXT")
    private String valoresNuevos;

    @Column(name = "ip_origen", nullable = false, length = 45)
    private String ipOrigen;

    @Column(name = "user_agent", nullable = false, columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false, length = 20)
    private String resultado = "EXITOSO";

    public AuditLog() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public Long getAgenteId() { return agenteId; }
    public void setAgenteId(Long agenteId) { this.agenteId = agenteId; }

    public String getAgenteNombre() { return agenteNombre; }
    public void setAgenteNombre(String agenteNombre) { this.agenteNombre = agenteNombre; }

    public String getAgenteEmail() { return agenteEmail; }
    public void setAgenteEmail(String agenteEmail) { this.agenteEmail = agenteEmail; }

    public String getAgenteRol() { return agenteRol; }
    public void setAgenteRol(String agenteRol) { this.agenteRol = agenteRol; }

    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public SeveridadAudit getNivelSeveridad() { return nivelSeveridad; }
    public void setNivelSeveridad(SeveridadAudit nivelSeveridad) { this.nivelSeveridad = nivelSeveridad; }

    public String getRecursoAfectadoTipo() { return recursoAfectadoTipo; }
    public void setRecursoAfectadoTipo(String recursoAfectadoTipo) { this.recursoAfectadoTipo = recursoAfectadoTipo; }

    public String getRecursoAfectadoId() { return recursoAfectadoId; }
    public void setRecursoAfectadoId(String recursoAfectadoId) { this.recursoAfectadoId = recursoAfectadoId; }

    public String getJustificacion() { return justificacion; }
    public void setJustificacion(String justificacion) { this.justificacion = justificacion; }

    public String getValoresAnteriores() { return valoresAnteriores; }
    public void setValoresAnteriores(String valoresAnteriores) { this.valoresAnteriores = valoresAnteriores; }

    public String getValoresNuevos() { return valoresNuevos; }
    public void setValoresNuevos(String valoresNuevos) { this.valoresNuevos = valoresNuevos; }

    public String getIpOrigen() { return ipOrigen; }
    public void setIpOrigen(String ipOrigen) { this.ipOrigen = ipOrigen; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
}
