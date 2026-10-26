package com.prisma.auth.dto;

import com.prisma.auth.enums.Rol;

public class ValidateTokenResponse {

    private boolean valido;
    private Long userId;
    private String email;
    private Rol rol;
    private Long proveedorId;
    private String mensaje;

    public ValidateTokenResponse() {}

    public static ValidateTokenResponse valido(Long userId, String email, Rol rol, Long proveedorId) {
        ValidateTokenResponse res = new ValidateTokenResponse();
        res.valido = true;
        res.userId = userId;
        res.email = email;
        res.rol = rol;
        res.proveedorId = proveedorId;
        res.mensaje = "Token válido y activo";
        return res;
    }

    public static ValidateTokenResponse invalido(String mensaje) {
        ValidateTokenResponse res = new ValidateTokenResponse();
        res.valido = false;
        res.mensaje = mensaje;
        return res;
    }

    public boolean isValido() { return valido; }
    public void setValido(boolean valido) { this.valido = valido; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
