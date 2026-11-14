package com.prisma.api.security;

import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.Rol;
import com.prisma.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceUnitTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    @DisplayName("loadUserByUsername encuentra usuario y mapea a UserPrincipal correctamente")
    void testLoadUserByUsernameExitoso() {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmail("test@prisma.com");
        usuario.setPasswordHash("$2a$10$hashedpass");
        usuario.setNombreCompleto("Juan Test");
        usuario.setRol(Rol.ROLE_PROVEEDOR);

        Proveedor prov = new Proveedor();
        prov.setId(42L);
        usuario.setProveedor(prov);

        when(usuarioRepository.findByEmail("test@prisma.com")).thenReturn(Optional.of(usuario));

        UserDetails details = userDetailsService.loadUserByUsername("test@prisma.com");

        assertThat(details).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) details;
        assertThat(principal.getId()).isEqualTo(10L);
        assertThat(principal.getUsername()).isEqualTo("test@prisma.com");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hashedpass");
        assertThat(principal.getNombreCompleto()).isEqualTo("Juan Test");
        assertThat(principal.getProveedorId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("loadUserByUsername lanza UsernameNotFoundException cuando email no existe")
    void testLoadUserByUsernameNoEncontrado() {
        when(usuarioRepository.findByEmail("noexiste@prisma.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("noexiste@prisma.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado con email: noexiste@prisma.com");
    }

    @Test
    @DisplayName("loadUserById encuentra usuario por ID")
    void testLoadUserByIdExitoso() {
        Usuario usuario = new Usuario();
        usuario.setId(99L);
        usuario.setEmail("admin@prisma.com");
        usuario.setPasswordHash("hash");
        usuario.setRol(Rol.ROLE_ADMIN);

        when(usuarioRepository.findById(99L)).thenReturn(Optional.of(usuario));

        UserDetails details = userDetailsService.loadUserById(99L);

        assertThat(details).isNotNull();
        assertThat(((UserPrincipal) details).getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("loadUserById lanza UsernameNotFoundException cuando ID no existe")
    void testLoadUserByIdNoEncontrado() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserById(999L))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado con ID: 999");
    }
}
