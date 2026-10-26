package com.prisma.api.security;

import com.prisma.api.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String nombreCompleto;
    private final Long proveedorId;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String email, String password, String nombreCompleto, Long proveedorId, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nombreCompleto = nombreCompleto;
        this.proveedorId = proveedorId;
        this.authorities = authorities;
    }

    public static UserPrincipal create(Usuario usuario) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(usuario.getRol().name()));
        Long provId = usuario.getProveedor() != null ? usuario.getProveedor().getId() : null;

        return new UserPrincipal(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getPasswordHash(),
                usuario.getNombreCompleto(),
                provId,
                authorities
        );
    }

    public static UserPrincipal createFromClaims(Long id, String email, String nombreCompleto, String rol, Long proveedorId) {
        String roleWithPrefix = (rol != null && rol.startsWith("ROLE_")) ? rol : "ROLE_" + (rol != null ? rol : "TENDERO");
        String roleBare = roleWithPrefix.substring(5);

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(roleWithPrefix),
                new SimpleGrantedAuthority(roleBare)
        );

        return new UserPrincipal(
                id,
                email,
                "",
                nombreCompleto != null ? nombreCompleto : email,
                proveedorId,
                authorities
        );
    }

    public Long getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public Long getProveedorId() { return proveedorId; }

    @Override
    public String getUsername() { return email; }

    @Override
    public String getPassword() { return password; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
