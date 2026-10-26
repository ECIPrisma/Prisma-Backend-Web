package com.prisma.api.dto;

import java.time.OffsetDateTime;

public class ApiResponse<T> {

    private boolean exitoso;
    private String mensaje;
    private T datos;
    private OffsetDateTime timestamp = OffsetDateTime.now();

    public ApiResponse(boolean exitoso, String mensaje, T datos) {
        this.exitoso = exitoso;
        this.mensaje = mensaje;
        this.datos = datos;
    }

    public static <T> ApiResponse<T> exito(String mensaje, T datos) {
        return new ApiResponse<>(true, mensaje, datos);
    }

    public static <T> ApiResponse<T> error(String mensaje) {
        return new ApiResponse<>(false, mensaje, null);
    }

    public boolean isExitoso() { return exitoso; }
    public String getMensaje() { return mensaje; }
    public T getDatos() { return datos; }
    public OffsetDateTime getTimestamp() { return timestamp; }
}
