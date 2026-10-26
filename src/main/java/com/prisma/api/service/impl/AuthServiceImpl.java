package com.prisma.api.service.impl;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.security.JwtTokenProvider;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public AuthResponse authenticate(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String rol = principal.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_TENDERO");

        return new AuthResponse(
                jwt,
                principal.getId(),
                principal.getUsername(),
                principal.getNombreCompleto(),
                rol,
                principal.getProveedorId()
        );
    }
}
