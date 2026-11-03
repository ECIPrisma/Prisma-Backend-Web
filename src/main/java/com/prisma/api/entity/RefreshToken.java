package com.prisma.api.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Refresh token opaco para renovar el JWT de acceso (HU-03).
 * Solo se persiste el hash SHA-256 del token; el valor real viaja únicamente en la cookie HttpOnly.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public RefreshToken() {}

    public RefreshToken(Usuario usuario, String tokenHash, OffsetDateTime expiraEn) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
        this.revocado = false;
        this.creadoEn = OffsetDateTime.now();
    }

    public boolean isVigente() {
        return !revocado && expiraEn.isAfter(OffsetDateTime.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public OffsetDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(OffsetDateTime expiraEn) { this.expiraEn = expiraEn; }

    public boolean isRevocado() { return revocado; }
    public void setRevocado(boolean revocado) { this.revocado = revocado; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
