package com.prisma.api.service;

import com.prisma.api.dto.RegisterRequest;
import com.prisma.api.entity.Tienda;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.Rol;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.TiendaRepository;
import com.prisma.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RegistroService {

    // Centro de Bogotá, usado solo si el cliente no envía coordenadas.
    private static final BigDecimal LAT_DEFECTO = new BigDecimal("4.7110000");
    private static final BigDecimal LNG_DEFECTO = new BigDecimal("-74.0721000");

    private final UsuarioRepository usuarioRepository;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistroService(UsuarioRepository usuarioRepository,
                           TiendaRepository tiendaRepository,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tiendaRepository = tiendaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario registrarTendero(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        String nit = req.getNitOCedula().trim();

        if (usuarioRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
        }
        if (tiendaRepository.findByNitOCedula(nit).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una tienda registrada con ese NIT o cédula");
        }

        Usuario usuario = usuarioRepository.save(new Usuario(
                email,
                passwordEncoder.encode(req.getPassword()),
                req.getNombreCompleto().trim(),
                req.getTelefono().trim(),
                Rol.ROLE_TENDERO,
                null));

        Tienda tienda = new Tienda();
        tienda.setTendero(usuario);
        tienda.setNombreTienda(req.getNombreTienda().trim());
        tienda.setNitOCedula(nit);
        tienda.setTelefonoTienda(req.getTelefono().trim());
        tienda.setDireccion(req.getDireccion().trim());
        tienda.setBarrio(req.getBarrio().trim());
        tienda.setCiudad(req.getCiudad() != null && !req.getCiudad().isBlank() ? req.getCiudad().trim() : "Bogotá");
        tienda.setLatitud(req.getLatitud() != null ? req.getLatitud() : LAT_DEFECTO);
        tienda.setLongitud(req.getLongitud() != null ? req.getLongitud() : LNG_DEFECTO);
        tiendaRepository.save(tienda);

        return usuario;
    }
}
