package com.prisma.api.dto;

/**
 * HU-12 – Datos de la cuenta bancaria del proveedor para la vinculación (simulada) con la pasarela.
 */
public class VincularCuentaCobroRequest {

    private String banco;
    private String tipoCuenta;
    private String numeroCuenta;
    private String titular;

    public VincularCuentaCobroRequest() {}

    public VincularCuentaCobroRequest(String banco, String tipoCuenta, String numeroCuenta, String titular) {
        this.banco = banco;
        this.tipoCuenta = tipoCuenta;
        this.numeroCuenta = numeroCuenta;
        this.titular = titular;
    }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getTipoCuenta() { return tipoCuenta; }
    public void setTipoCuenta(String tipoCuenta) { this.tipoCuenta = tipoCuenta; }

    public String getNumeroCuenta() { return numeroCuenta; }
    public void setNumeroCuenta(String numeroCuenta) { this.numeroCuenta = numeroCuenta; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }
}
