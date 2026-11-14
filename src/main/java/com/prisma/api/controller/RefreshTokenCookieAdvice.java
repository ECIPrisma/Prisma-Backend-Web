package com.prisma.api.controller;

import com.prisma.api.dto.ApiResponse;
import com.prisma.api.dto.AuthResponse;
import com.prisma.api.service.RefreshTokenService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * HU-03: toda respuesta exitosa de {@link AuthController} que abre una sesión (login, registro)
 * sale con la cookie HttpOnly del refresh token, sin modificar los endpoints de autenticación.
 */
@ControllerAdvice(assignableTypes = AuthController.class)
public class RefreshTokenCookieAdvice implements ResponseBodyAdvice<Object> {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCookieAdvice(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse
                && apiResponse.isExitoso()
                && apiResponse.getDatos() instanceof AuthResponse auth
                && auth.getId() != null) {
            String refreshToken = refreshTokenService.emitir(auth.getId());
            response.getHeaders().add(HttpHeaders.SET_COOKIE, refreshTokenService.crearCookie(refreshToken).toString());
        }
        return body;
    }
}
