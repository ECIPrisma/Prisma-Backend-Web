package com.prisma.api.service;

import com.prisma.api.dto.AuthResponse;
import org.springframework.http.ResponseCookie;

public interface RefreshTokenService {
    String COOKIE_NAME = "prisma_refresh";

    String emitir(Long usuarioId);
    AuthResponse renovar(String refreshToken);
    void revocar(String refreshToken);
    ResponseCookie crearCookie(String refreshToken);
    ResponseCookie borrarCookie();
}
