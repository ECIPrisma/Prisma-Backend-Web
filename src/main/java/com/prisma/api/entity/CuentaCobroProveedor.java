package com.prisma.api.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * HU-12: cuenta de cobro del proveedor en la pasarela (Mercado Pago), vinculada por OAuth.
 * Los tokens se guardan cifrados con AES-GCM; nunca se exponen en la API.
 */
@Entity
@Table(name = "cuentas_cobro_proveedor")
public class CuentaCobroProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, unique = true)
    private Proveedor proveedor;

    @Column(nullable = false, length = 30)
    private String pasarela = "MERCADO_PAGO";

    // Identificador de la cuenta del vendedor en Mercado Pago ("user_id" del token)
    @Column(name = "cuenta_externa_id", nullable = false, length = 40)
    private String cuentaExternaId;

    @Column(name = "access_token_cifrado", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCifrado;

    @Column(name = "refresh_token_cifrado", columnDefinition = "TEXT")
    private String refreshTokenCifrado;

    @Column(name = "public_key", length = 200)
    private String publicKey;

    @Column(length = 120)
    private String scope;

    // false = credenciales de prueba (sandbox)
    @Column(name = "modo_produccion", nullable = false)
    private boolean modoProduccion;

    @Column(name = "token_expira_en")
    private OffsetDateTime tokenExpiraEn;

    @Column(name = "vinculada_en", nullable = false)
    private OffsetDateTime vinculadaEn = OffsetDateTime.now();

    @Column(name = "actualizada_en", nullable = false)
    private OffsetDateTime actualizadaEn = OffsetDateTime.now();

    public CuentaCobroProveedor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public String getPasarela() { return pasarela; }
    public void setPasarela(String pasarela) { this.pasarela = pasarela; }

    public String getCuentaExternaId() { return cuentaExternaId; }
    public void setCuentaExternaId(String cuentaExternaId) { this.cuentaExternaId = cuentaExternaId; }

    public String getAccessTokenCifrado() { return accessTokenCifrado; }
    public void setAccessTokenCifrado(String accessTokenCifrado) { this.accessTokenCifrado = accessTokenCifrado; }

    public String getRefreshTokenCifrado() { return refreshTokenCifrado; }
    public void setRefreshTokenCifrado(String refreshTokenCifrado) { this.refreshTokenCifrado = refreshTokenCifrado; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public boolean isModoProduccion() { return modoProduccion; }
    public void setModoProduccion(boolean modoProduccion) { this.modoProduccion = modoProduccion; }

    public OffsetDateTime getTokenExpiraEn() { return tokenExpiraEn; }
    public void setTokenExpiraEn(OffsetDateTime tokenExpiraEn) { this.tokenExpiraEn = tokenExpiraEn; }

    public OffsetDateTime getVinculadaEn() { return vinculadaEn; }
    public void setVinculadaEn(OffsetDateTime vinculadaEn) { this.vinculadaEn = vinculadaEn; }

    public OffsetDateTime getActualizadaEn() { return actualizadaEn; }
    public void setActualizadaEn(OffsetDateTime actualizadaEn) { this.actualizadaEn = actualizadaEn; }
}
