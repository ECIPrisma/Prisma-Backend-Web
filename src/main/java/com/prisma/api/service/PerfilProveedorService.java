package com.prisma.api.service;

import com.prisma.api.dto.PerfilProveedorRequest;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.security.UserPrincipal;

public interface PerfilProveedorService {
    Proveedor obtenerPerfil(Long proveedorId, UserPrincipal usuario);
    Proveedor actualizarPerfil(Long proveedorId, PerfilProveedorRequest request, UserPrincipal usuario);
}
