package com.prisma.api.dto;

import com.prisma.api.enums.MedioPago;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ConfirmarEntregaPinRequest {

    @NotBlank(message = "El PIN de entrega es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El PIN debe tener exactamente 6 dígitos")
    private String pin;

    @NotNull(message = "El medio de pago es obligatorio")
    private MedioPago medioPago;

    private String notasEntrega;

    public ConfirmarEntregaPinRequest() {}

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }

    public String getNotasEntrega() { return notasEntrega; }
    public void setNotasEntrega(String notasEntrega) { this.notasEntrega = notasEntrega; }
}
