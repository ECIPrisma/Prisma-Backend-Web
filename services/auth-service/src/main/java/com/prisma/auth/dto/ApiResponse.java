package com.prisma.auth.dto;

import java.time.OffsetDateTime;

public class ApiResponse<T> {

    private boolean exito;
    private String mensaje;
    private T datos;
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public ApiResponse() {}

    public ApiResponse(boolean exito, String mensaje, T datos) {
        this.exito = exito;
        this.mensaje = mensaje;
        this.datos = datos;
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> ApiResponse<T> ok(String mensaje, T datos) {
        return new ApiResponse<>(true, mensaje, datos);
    }

    public static <T> ApiResponse<T> error(String mensaje) {
        return new ApiResponse<>(false, mensaje, null);
    }

    public boolean isExito() { return exito; }
    public void setExito(boolean exito) { this.exito = exito; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public T getDatos() { return datos; }
    public void setDatos(T datos) { this.datos = datos; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }
}
