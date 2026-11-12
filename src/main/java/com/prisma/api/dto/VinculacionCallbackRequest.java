package com.prisma.api.dto;

/**
 * Parámetros que Mercado Pago devuelve en el redirect y que el frontend reenvía al backend.
 * Si el proveedor cancela o rechaza, llega sin "code" (y posiblemente con "error").
 */
public class VinculacionCallbackRequest {

    private String code;
    private String state;
    private String error;

    public VinculacionCallbackRequest() {}

    public VinculacionCallbackRequest(String code, String state, String error) {
        this.code = code;
        this.state = state;
        this.error = error;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
