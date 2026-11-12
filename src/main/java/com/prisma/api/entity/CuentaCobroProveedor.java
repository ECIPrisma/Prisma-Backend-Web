package com.prisma.api.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * HU-12 – Cuenta bancaria donde el proveedor recibe su parte de los pagos en línea.
 * La vinculación con la pasarela (Wompi) es SIMULADA: no se guardan credenciales ni el número completo.
 */
@Entity
@Table(name = "cuentas_recaudo_proveedor")
public class CuentaCobroProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false, unique = true)
    private Proveedor proveedor;

    @Column(nullable = false, length = 30)
    private String pasarela;

    /** Identificador de comercio que "asigna" la pasarela simulada, ej. WMP-SBX-482913 */
    @Column(name = "cuenta_externa_id", nullable = false, length = 40)
    private String cuentaExternaId;

    @Column(nullable = false, length = 80)
    private String banco;

    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private String tipoCuenta;

    @Column(name = "numero_cuenta_ultimos4", nullable = false, length = 4)
    private String numeroCuentaUltimos4;

    @Column(nullable = false, length = 120)
    private String titular;

    @Column(name = "modo_produccion", nullable = false)
    private boolean modoProduccion = false;

    @Column(name = "vinculada_en", nullable = false)
    private OffsetDateTime vinculadaEn = OffsetDateTime.now();

    public CuentaCobroProveedor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

    public String getPasarela() { return pasarela; }
    public void setPasarela(String pasarela) { this.pasarela = pasarela; }

    public String getCuentaExternaId() { return cuentaExternaId; }
    public void setCuentaExternaId(String cuentaExternaId) { this.cuentaExternaId = cuentaExternaId; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getTipoCuenta() { return tipoCuenta; }
    public void setTipoCuenta(String tipoCuenta) { this.tipoCuenta = tipoCuenta; }

    public String getNumeroCuentaUltimos4() { return numeroCuentaUltimos4; }
    public void setNumeroCuentaUltimos4(String numeroCuentaUltimos4) { this.numeroCuentaUltimos4 = numeroCuentaUltimos4; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public boolean isModoProduccion() { return modoProduccion; }
    public void setModoProduccion(boolean modoProduccion) { this.modoProduccion = modoProduccion; }

    public OffsetDateTime getVinculadaEn() { return vinculadaEn; }
    public void setVinculadaEn(OffsetDateTime vinculadaEn) { this.vinculadaEn = vinculadaEn; }
}
