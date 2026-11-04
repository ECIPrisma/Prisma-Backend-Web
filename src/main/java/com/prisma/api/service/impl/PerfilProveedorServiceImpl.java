package com.prisma.api.service.impl;

import com.prisma.api.dto.PerfilProveedorRequest;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.security.AislamientoTenant;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PerfilProveedorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class PerfilProveedorServiceImpl implements PerfilProveedorService {

    private final ProveedorRepository proveedorRepository;

    public PerfilProveedorServiceImpl(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Proveedor obtenerPerfil(Long proveedorId, UserPrincipal usuario) {
        return buscarPropio(proveedorId, usuario);
    }

    @Override
    @Transactional
    public Proveedor actualizarPerfil(Long proveedorId, PerfilProveedorRequest request, UserPrincipal usuario) {
        Proveedor proveedor = buscarPropio(proveedorId, usuario);

        proveedor.setRazonSocial(request.getRazonSocial().trim());
        proveedor.setNombreComercial(request.getNombreComercial().trim());
        proveedor.setZonaCobertura(textoOpcional(request.getZonaCobertura()));
        proveedor.setContactoNombre(request.getContactoNombre().trim());
        proveedor.setContactoEmail(request.getContactoEmail().trim().toLowerCase());
        proveedor.setContactoTelefono(request.getContactoTelefono().trim());
        proveedor.setDireccionSede(textoOpcional(request.getDireccionSede()));
        proveedor.setCiudad(request.getCiudad().trim());
        proveedor.setActualizadoEn(OffsetDateTime.now());

        return proveedorRepository.save(proveedor);
    }

    /** Primero valida la pertenencia (403) y solo después consulta, para no revelar si el proveedor existe. */
    private Proveedor buscarPropio(Long proveedorId, UserPrincipal usuario) {
        if (proveedorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El usuario no está asociado a ningún proveedor");
        }
        AislamientoTenant.verificarPropietario(usuario, proveedorId, "El perfil solicitado");
        return proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
