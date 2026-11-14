package com.prisma.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TotpDisableRequest {

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @Pattern(regexp = "[0-9]{6}", message = "El código debe tener 6 dígitos")
    private String codigo;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
