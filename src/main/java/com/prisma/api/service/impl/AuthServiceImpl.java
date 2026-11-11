package com.prisma.api.service.impl;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;
import com.prisma.api.entity.Usuario;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.JwtTokenProvider;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.AuthService;
import com.prisma.api.service.TotpAuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UsuarioRepository usuarioRepository;
    private final TotpAuthService totpAuthService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider,
                           UsuarioRepository usuarioRepository,
                           TotpAuthService totpAuthService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.usuarioRepository = usuarioRepository;
        this.totpAuthService = totpAuthService;
    }

    @Override
    @Transactional
    public AuthResponse authenticate(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Usuario usuario = usuarioRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
        if (!"ACTIVO".equals(usuario.getEstado())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La cuenta del usuario no está activa.");
        }
        totpAuthService.validateLoginCode(usuario, loginRequest.getCodigoTotp());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

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
