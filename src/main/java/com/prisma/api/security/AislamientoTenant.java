package com.prisma.api.security;

import com.prisma.api.entity.Proveedor;
import com.prisma.api.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * HU-04 – Aislamiento multi-tenant: un proveedor solo puede ver o modificar recursos de su propia empresa.
 * Los administradores de la plataforma (ROLE_ADMIN / ROLE_PLATFORM_ADMIN) quedan exentos.
 */
public final class AislamientoTenant {

    private AislamientoTenant() {}

    public static boolean esAdmin(UserPrincipal usuario) {
        return usuario != null && usuario.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    /** Lanza 403 si el recurso no pertenece al proveedor del usuario autenticado. */
    public static void verificarPropietario(UserPrincipal usuario, Long proveedorIdRecurso, String recurso) {
        if (esAdmin(usuario)) {
            return;
        }
        Long proveedorIdUsuario = usuario != null ? usuario.getProveedorId() : null;
        if (proveedorIdUsuario == null || !proveedorIdUsuario.equals(proveedorIdRecurso)) {
            throw new ApiException(HttpStatus.FORBIDDEN, recurso + " pertenece a otro proveedor");
        }
    }

    public static void verificarPropietario(UserPrincipal usuario, Proveedor proveedorRecurso, String recurso) {
        verificarPropietario(usuario, proveedorRecurso != null ? proveedorRecurso.getId() : null, recurso);
    }
}
