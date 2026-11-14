package com.prisma.api.service;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.entity.Usuario;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.JwtTokenProvider;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.TotpAuthService;
import com.prisma.api.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TotpAuthService totpAuthService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Autenticación exitosa genera AuthResponse con token y datos de usuario")
    void testAuthenticateExitoso() {
        LoginRequest req = new LoginRequest("admin@prisma.com.co", "admin123");
        UserPrincipal principal = new UserPrincipal(
                1L, "admin@prisma.com.co", "hash", "Admin",
                null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEstado("ACTIVO");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(usuarioRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(usuario));
        when(tokenProvider.generateToken(auth)).thenReturn("mocked.jwt.token");

        AuthResponse resp = authService.authenticate(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getToken()).isEqualTo("mocked.jwt.token");
        assertThat(resp.getEmail()).isEqualTo("admin@prisma.com.co");
        assertThat(resp.getRol()).isEqualTo("ROLE_ADMIN");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(totpAuthService).validateLoginCode(usuario, null);
        verify(tokenProvider).generateToken(auth);
    }

    @Test
    @DisplayName("Credenciales inválidas propagan BadCredentialsException")
    void testAuthenticateCredencialesInvalidas() {
        LoginRequest req = new LoginRequest("wrong@prisma.com.co", "badpass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Credenciales incorrectas"));

        assertThatThrownBy(() -> authService.authenticate(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Credenciales incorrectas");
    }
}
