package com.prisma.api.dto;

import jakarta.validation.constraints.Pattern;

public class TotpCodeRequest {

    @Pattern(regexp = "[0-9]{6}", message = "El código debe tener 6 dígitos")
    private String codigo;

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
