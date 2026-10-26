package com.prisma.auth.dto;

import com.prisma.auth.enums.Rol;

public class AuthResponse {

    private String token;
    private String tipoToken = "Bearer";
    private long expiracionEnSegundos;
    private UserData usuario;

    public AuthResponse() {}

    public AuthResponse(String token, long expiracionEnSegundos, UserData usuario) {
        this.token = token;
        this.tipoToken = "Bearer";
        this.expiracionEnSegundos = expiracionEnSegundos;
        this.usuario = usuario;
    }

    public static class UserData {
        private Long id;
        private String email;
        private String nombreCompleto;
        private String telefono;
        private Rol rol;
        private Long proveedorId;

        public UserData() {}

        public UserData(Long id, String email, String nombreCompleto, String telefono, Rol rol, Long proveedorId) {
            this.id = id;
            this.email = email;
            this.nombreCompleto = nombreCompleto;
            this.telefono = telefono;
            this.rol = rol;
            this.proveedorId = proveedorId;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getNombreCompleto() { return nombreCompleto; }
        public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public Rol getRol() { return rol; }
        public void setRol(Rol rol) { this.rol = rol; }
        public Long getProveedorId() { return proveedorId; }
        public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTipoToken() { return tipoToken; }
    public void setTipoToken(String tipoToken) { this.tipoToken = tipoToken; }
    public long getExpiracionEnSegundos() { return expiracionEnSegundos; }
    public void setExpiracionEnSegundos(long expiracionEnSegundos) { this.expiracionEnSegundos = expiracionEnSegundos; }
    public UserData getUsuario() { return usuario; }
    public void setUsuario(UserData usuario) { this.usuario = usuario; }
}
