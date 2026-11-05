package com.prisma.api.service;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.entity.RefreshToken;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.Rol;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.RefreshTokenRepository;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.JwtTokenProvider;
import com.prisma.api.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                usuarioRepository,
                tokenProvider,
                2592000000L,
                true
        );
    }

    @Test
    @DisplayName("emitir crea y guarda un refresh token persistiendo su hash")
    void testEmitir() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);

        when(usuarioRepository.getReferenceById(1L)).thenReturn(usuario);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String token = refreshTokenService.emitir(1L);

        assertThat(token).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("renovar con token vacío lanza ApiException 401 Unauthorized")
    void testRenovarVacioLanza401() {
        assertThatThrownBy(() -> refreshTokenService.renovar(""))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("renovar con token inexistente o expirado lanza ApiException 401")
    void testRenovarInvalidoLanza401() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.renovar("token-falso"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("crearCookie genera cookie HttpOnly y Secure")
    void testCrearCookie() {
        ResponseCookie cookie = refreshTokenService.crearCookie("sample-token");

        assertThat(cookie.getName()).isEqualTo("prisma_refresh");
        assertThat(cookie.getValue()).isEqualTo("sample-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    @DisplayName("borrarCookie genera cookie vacía con maxAge cero")
    void testBorrarCookie() {
        ResponseCookie cookie = refreshTokenService.borrarCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0);
    }
}
