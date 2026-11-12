package com.prisma.api.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * HU-12: intento de vinculación OAuth en curso. El "state" protege contra CSRF (solo el proveedor que
 * inició el flujo puede completarlo) y el code_verifier es el secreto PKCE que se envía al canjear el code.
 */
@Entity
@Table(name = "solicitudes_vinculacion_pago")
public class SolicitudVinculacionPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String state;

    @Column(name = "proveedor_id", nullable = false)
    private Long proveedorId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "code_verifier", nullable = false, length = 128)
    private String codeVerifier;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean usada = false;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private OffsetDateTime creadaEn = OffsetDateTime.now();

    public SolicitudVinculacionPago() {}

    public SolicitudVinculacionPago(String state, Long proveedorId, Long usuarioId, String codeVerifier, OffsetDateTime expiraEn) {
        this.state = state;
        this.proveedorId = proveedorId;
        this.usuarioId = usuarioId;
        this.codeVerifier = codeVerifier;
        this.expiraEn = expiraEn;
    }

    public boolean isVigente() {
        return !usada && expiraEn.isAfter(OffsetDateTime.now());
    }

    public Long getId() { return id; }
    public String getState() { return state; }
    public Long getProveedorId() { return proveedorId; }
    public Long getUsuarioId() { return usuarioId; }
    public String getCodeVerifier() { return codeVerifier; }
    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(OffsetDateTime expiraEn) { this.expiraEn = expiraEn; }
    public boolean isUsada() { return usada; }
    public void setUsada(boolean usada) { this.usada = usada; }
    public OffsetDateTime getCreadaEn() { return creadaEn; }
}
