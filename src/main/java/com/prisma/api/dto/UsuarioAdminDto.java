package com.prisma.api.dto;

public class UsuarioAdminDto {

    private Long id;
    private String email;
    private String nombreCompleto;
    private String telefono;
    private String rol;
    private String estado;
    private boolean totpEnabled;
    private Long proveedorId;
    private String proveedorNombre;

    public UsuarioAdminDto() {}

    public UsuarioAdminDto(Long id, String email, String nombreCompleto, String telefono,
                           String rol, String estado, boolean totpEnabled,
                           Long proveedorId, String proveedorNombre) {
        this.id = id;
        this.email = email;
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.rol = rol;
        this.estado = estado;
        this.totpEnabled = totpEnabled;
        this.proveedorId = proveedorId;
        this.proveedorNombre = proveedorNombre;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }

    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }

    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String proveedorNombre) { this.proveedorNombre = proveedorNombre; }
}
