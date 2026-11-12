package com.prisma.api.dto;

import com.prisma.api.enums.MedioPagoEnLinea;

/**
 * Datos que envía el checkout de la app del tendero. Solo se usan para la simulación y nunca se guardan completos:
 * el CVV no se recibe y de la tarjeta solo se conservan los últimos 4 dígitos.
 */
public class PagarPedidoRequest {

    private MedioPagoEnLinea medio;

    // Nequi
    private String celular;

    // PSE
    private String banco;
    private String tipoPersona;
    private String documento;

    // Tarjeta
    private String numeroTarjeta;
    private String titular;
    private String vencimiento;
    private Integer cuotas;

    public PagarPedidoRequest() {}

    public MedioPagoEnLinea getMedio() { return medio; }
    public void setMedio(MedioPagoEnLinea medio) { this.medio = medio; }

    public String getCelular() { return celular; }
    public void setCelular(String celular) { this.celular = celular; }

    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }

    public String getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(String tipoPersona) { this.tipoPersona = tipoPersona; }

    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }

    public String getNumeroTarjeta() { return numeroTarjeta; }
    public void setNumeroTarjeta(String numeroTarjeta) { this.numeroTarjeta = numeroTarjeta; }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }

    public String getVencimiento() { return vencimiento; }
    public void setVencimiento(String vencimiento) { this.vencimiento = vencimiento; }

    public Integer getCuotas() { return cuotas; }
    public void setCuotas(Integer cuotas) { this.cuotas = cuotas; }
}
