package com.prisma.api.service;

import com.prisma.api.dto.TotpSetupResponse;
import com.prisma.api.dto.TotpStatusResponse;
import com.prisma.api.entity.Usuario;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.RefreshTokenRepository;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalLong;

@Service
public class TotpAuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    public TotpAuthService(UsuarioRepository usuarioRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           TotpService totpService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
    }

    @Transactional(readOnly = true)
    public TotpStatusResponse status(Long userId) {
        return new TotpStatusResponse(findUser(userId).isTotpEnabled());
    }

    @Transactional
    public TotpSetupResponse beginSetup(Long userId) {
        Usuario usuario = findUserForUpdate(userId);
        if (usuario.isTotpEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "La autenticación en dos pasos ya está activada.");
        }

        String secret = totpService.generateSecret();
        usuario.setTotpSecretEncrypted(totpService.encryptSecret(secret));
        usuario.setTotpLastUsedStep(null);
        usuarioRepository.save(usuario);
        return new TotpSetupResponse(secret, totpService.buildOtpAuthUri(secret, usuario.getEmail()));
    }

    @Transactional
    public TotpStatusResponse confirmSetup(Long userId, String code) {
        Usuario usuario = findUserForUpdate(userId);
        if (usuario.isTotpEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "La autenticación en dos pasos ya está activada.");
        }
        if (usuario.getTotpSecretEncrypted() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Primero debes iniciar la configuración del autenticador.");
        }

        long acceptedStep = requireValidCode(totpService.decryptSecret(usuario.getTotpSecretEncrypted()), code);
        usuario.setTotpEnabled(true);
        usuario.setTotpLastUsedStep(acceptedStep);
        usuarioRepository.save(usuario);
        refreshTokenRepository.revokeAllForUsuario(usuario.getId());
        return new TotpStatusResponse(true);
    }

    @Transactional
    public TotpStatusResponse disable(Long userId, String password, String code) {
        Usuario usuario = findUserForUpdate(userId);
        if (!usuario.isTotpEnabled() || usuario.getTotpSecretEncrypted() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "La autenticación en dos pasos no está activada.");
        }
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }

        requireNewValidCode(usuario, code);
        usuario.setTotpEnabled(false);
        usuario.setTotpSecretEncrypted(null);
        usuario.setTotpLastUsedStep(null);
        usuarioRepository.save(usuario);
        return new TotpStatusResponse(false);
    }

    @Transactional
    public void validateLoginCode(Usuario usuario, String code) {
        if (!usuario.isTotpEnabled()) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Se requiere un código TOTP de 6 dígitos.");
        }
        if (usuario.getTotpSecretEncrypted() == null) {
            throw new IllegalStateException("El usuario tiene 2FA activado pero no tiene secreto TOTP.");
        }
        requireNewValidCode(usuario, code);
    }

    private void requireNewValidCode(Usuario usuario, String code) {
        long acceptedStep = requireValidCode(totpService.decryptSecret(usuario.getTotpSecretEncrypted()), code);
        Long lastUsedStep = usuario.getTotpLastUsedStep();
        if (lastUsedStep != null && acceptedStep <= lastUsedStep) {
            throw new BadCredentialsException("Código de autenticación inválido.");
        }
        usuario.setTotpLastUsedStep(acceptedStep);
        usuarioRepository.save(usuario);
    }

    private long requireValidCode(String secret, String code) {
        OptionalLong matchedStep = totpService.matchingStep(secret, code);
        if (matchedStep.isEmpty()) {
            throw new BadCredentialsException("Código de autenticación inválido.");
        }
        return matchedStep.getAsLong();
    }

    private Usuario findUserForUpdate(Long userId) {
        return usuarioRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    private Usuario findUser(Long userId) {
        return usuarioRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }
}
