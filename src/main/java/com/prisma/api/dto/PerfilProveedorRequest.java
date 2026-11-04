package com.prisma.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos editables del perfil comercial del proveedor (HU-04).
 * NIT, comisión y estado no son editables por el proveedor: los gestiona el administrador de Prisma.
 */
public class PerfilProveedorRequest {

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 150, message = "La razón social admite máximo 150 caracteres")
    private String razonSocial;

    @NotBlank(message = "El nombre comercial es obligatorio")
    @Size(max = 100, message = "El nombre comercial admite máximo 100 caracteres")
    private String nombreComercial;

    @Size(max = 500, message = "La zona de cobertura admite máximo 500 caracteres")
    private String zonaCobertura;

    @NotBlank(message = "El nombre de contacto es obligatorio")
    @Size(max = 100, message = "El nombre de contacto admite máximo 100 caracteres")
    private String contactoNombre;

    @NotBlank(message = "El email de contacto es obligatorio")
    @Email(message = "El formato de email no es válido")
    @Size(max = 150, message = "El email de contacto admite máximo 150 caracteres")
    private String contactoEmail;

    @NotBlank(message = "El teléfono de contacto es obligatorio")
    @Pattern(regexp = "^[0-9+ ()-]{7,20}$", message = "El teléfono debe tener entre 7 y 20 dígitos")
    private String contactoTelefono;

    @Size(max = 200, message = "La dirección admite máximo 200 caracteres")
    private String direccionSede;

    @NotBlank(message = "La ciudad es obligatoria")
    @Size(max = 80, message = "La ciudad admite máximo 80 caracteres")
    private String ciudad;

    public PerfilProveedorRequest() {}

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }

    public String getZonaCobertura() { return zonaCobertura; }
    public void setZonaCobertura(String zonaCobertura) { this.zonaCobertura = zonaCobertura; }

    public String getContactoNombre() { return contactoNombre; }
    public void setContactoNombre(String contactoNombre) { this.contactoNombre = contactoNombre; }

    public String getContactoEmail() { return contactoEmail; }
    public void setContactoEmail(String contactoEmail) { this.contactoEmail = contactoEmail; }

    public String getContactoTelefono() { return contactoTelefono; }
    public void setContactoTelefono(String contactoTelefono) { this.contactoTelefono = contactoTelefono; }

    public String getDireccionSede() { return direccionSede; }
    public void setDireccionSede(String direccionSede) { this.direccionSede = direccionSede; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }
}
