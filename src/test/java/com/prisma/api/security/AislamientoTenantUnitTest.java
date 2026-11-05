package com.prisma.api.security;

import com.prisma.api.entity.Proveedor;
import com.prisma.api.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AislamientoTenantUnitTest {

    @Test
    @DisplayName("esAdmin retorna true para ROLE_ADMIN o ROLE_PLATFORM_ADMIN")
    void testEsAdmin() {
        UserPrincipal admin = new UserPrincipal(
                1L, "admin@prisma.com", "hash", "Admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        UserPrincipal platformAdmin = new UserPrincipal(
                2L, "plat@prisma.com", "hash", "Plat Admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
        UserPrincipal tendero = new UserPrincipal(
                3L, "tendero@oasis.com", "hash", "Tendero", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );

        assertThat(AislamientoTenant.esAdmin(admin)).isTrue();
        assertThat(AislamientoTenant.esAdmin(platformAdmin)).isTrue();
        assertThat(AislamientoTenant.esAdmin(tendero)).isFalse();
        assertThat(AislamientoTenant.esAdmin(null)).isFalse();
    }

    @Test
    @DisplayName("verificarPropietario permite acceso al administrador sin importar el proveedor")
    void testVerificarPropietarioAdminPermitido() {
        UserPrincipal admin = new UserPrincipal(
                1L, "admin@prisma.com", "hash", "Admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThatCode(() -> AislamientoTenant.verificarPropietario(admin, 99L, "Recurso"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verificarPropietario permite acceso cuando proveedorId coincide")
    void testVerificarPropietarioMismoProveedor() {
        UserPrincipal provUser = new UserPrincipal(
                10L, "operador@femsa.com", "hash", "Operador", 5L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );

        assertThatCode(() -> AislamientoTenant.verificarPropietario(provUser, 5L, "Inventario"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verificarPropietario lanza 403 Forbidden si el proveedorId no coincide")
    void testVerificarPropietarioDiferenteProveedorLanza403() {
        UserPrincipal provUser = new UserPrincipal(
                10L, "operador@femsa.com", "hash", "Operador", 5L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );

        assertThatThrownBy(() -> AislamientoTenant.verificarPropietario(provUser, 99L, "Inventario"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getMessage()).contains("pertenece a otro proveedor");
                });
    }

    @Test
    @DisplayName("verificarPropietario con entidad Proveedor delega correctamente")
    void testVerificarPropietarioConEntidad() {
        UserPrincipal provUser = new UserPrincipal(
                10L, "operador@femsa.com", "hash", "Operador", 5L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );
        Proveedor p = new Proveedor();
        p.setId(5L);

        assertThatCode(() -> AislamientoTenant.verificarPropietario(provUser, p, "Catálogo"))
                .doesNotThrowAnyException();
    }
}
