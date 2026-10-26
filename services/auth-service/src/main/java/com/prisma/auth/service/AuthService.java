package com.prisma.auth.service;

import com.prisma.auth.dto.*;
import com.prisma.auth.entity.Usuario;
import com.prisma.auth.enums.Rol;
import com.prisma.auth.repository.UsuarioRepository;
import com.prisma.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas: email o contraseña incorrectos"));

        if (!"ACTIVO".equalsIgnoreCase(usuario.getEstado())) {
            throw new BadCredentialsException("Cuenta inactiva o bloqueada. Contacte al administrador.");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            usuario.setIntentosFallidosLogin(usuario.getIntentosFallidosLogin() + 1);
            if (usuario.getIntentosFallidosLogin() >= 5) {
                usuario.setEstado("BLOQUEADO");
                logger.warn("Usuario {} bloqueado por exceder 5 intentos fallidos", usuario.getEmail());
            }
            usuarioRepository.save(usuario);
            throw new BadCredentialsException("Credenciales inválidas: email o contraseña incorrectos");
        }

        // Login exitoso: reset intentos y actualizar fecha
        usuario.setIntentosFallidosLogin(0);
        usuario.setUltimoLogin(OffsetDateTime.now());
        usuarioRepository.save(usuario);

        String token = jwtService.generateToken(usuario);
        long expSec = jwtService.getExpirationInSeconds();

        AuthResponse.UserData userData = new AuthResponse.UserData(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getTelefono(),
                usuario.getRol(),
                usuario.getProveedorId()
        );

        logger.info("Login exitoso para usuario: {} [Rol: {}]", usuario.getEmail(), usuario.getRol());
        return new AuthResponse(token, expSec, userData);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con el email: " + email);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Usuario nuevoUsuario = new Usuario(
                email,
                encodedPassword,
                request.getNombreCompleto().trim(),
                request.getTelefono().trim(),
                request.getRol(),
                request.getProveedorId()
        );

        Usuario guardado = usuarioRepository.save(nuevoUsuario);
        String token = jwtService.generateToken(guardado);
        long expSec = jwtService.getExpirationInSeconds();

        AuthResponse.UserData userData = new AuthResponse.UserData(
                guardado.getId(),
                guardado.getEmail(),
                guardado.getNombreCompleto(),
                guardado.getTelefono(),
                guardado.getRol(),
                guardado.getProveedorId()
        );

        logger.info("Usuario registrado exitosamente: {} [Rol: {}]", guardado.getEmail(), guardado.getRol());
        return new AuthResponse(token, expSec, userData);
    }

    @Transactional(readOnly = true)
    public ValidateTokenResponse validateToken(String rawToken) {
        String token = cleanToken(rawToken);
        if (token == null || !jwtService.validateToken(token)) {
            return ValidateTokenResponse.invalido("Token inválido, expirado o malformado");
        }

        try {
            Claims claims = jwtService.getClaimsFromToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            String rolStr = claims.get("rol", String.class);
            Long proveedorId = claims.get("proveedorId", Long.class);

            Rol rol = Rol.valueOf(rolStr);

            // Verificar que el usuario continúe existiendo y activo
            Usuario usuario = usuarioRepository.findById(userId).orElse(null);
            if (usuario == null || !"ACTIVO".equalsIgnoreCase(usuario.getEstado())) {
                return ValidateTokenResponse.invalido("El usuario asociado al token ya no se encuentra activo");
            }

            return ValidateTokenResponse.valido(userId, email, rol, proveedorId);
        } catch (Exception ex) {
            return ValidateTokenResponse.invalido("Error decodificando claims del token: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserData getProfileFromToken(String rawToken) {
        String token = cleanToken(rawToken);
        if (token == null || !jwtService.validateToken(token)) {
            throw new BadCredentialsException("Token inválido o expirado");
        }

        Long userId = jwtService.getUserIdFromToken(token);
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        return new AuthResponse.UserData(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getTelefono(),
                usuario.getRol(),
                usuario.getProveedorId()
        );
    }

    private String cleanToken(String rawToken) {
        if (rawToken == null) return null;
        if (rawToken.startsWith("Bearer ")) {
            return rawToken.substring(7).trim();
        }
        return rawToken.trim();
    }

    @PostConstruct
    @Transactional
    public void seedInitialUsersIfEmpty() {
        if (usuarioRepository.count() == 0) {
            logger.info("Inicializando cuentas semilla en la base de datos de Auth Service...");
            
            // 1. Admin PRISMA
            usuarioRepository.save(new Usuario(
                    "admin@prisma.com.co",
                    passwordEncoder.encode("admin123"),
                    "Administrador Prisma",
                    "3001234567",
                    Rol.ROLE_ADMIN,
                    null
            ));

            // 2. Proveedor Coca-Cola
            usuarioRepository.save(new Usuario(
                    "logistica@femsa.com.co",
                    passwordEncoder.encode("femsa123"),
                    "Carlos Morales - Despachos FEMSA",
                    "3109876543",
                    Rol.ROLE_PROVEEDOR,
                    1L
            ));

            // 3. Tendero La Bendición
            usuarioRepository.save(new Usuario(
                    "dona.marta@labendicion.com",
                    passwordEncoder.encode("marta123"),
                    "Doña Marta Rodríguez",
                    "3205551234",
                    Rol.ROLE_TENDERO,
                    null
            ));

            // 4. Repartidor
            usuarioRepository.save(new Usuario(
                    "conductor.juan@femsa.com.co",
                    passwordEncoder.encode("juan123"),
                    "Juan Camilo Reyes",
                    "3154448899",
                    Rol.ROLE_REPARTIDOR,
                    1L
            ));

            logger.info("4 Cuentas semilla creadas con contraseñas encriptadas BCrypt.");
        }
    }
}
