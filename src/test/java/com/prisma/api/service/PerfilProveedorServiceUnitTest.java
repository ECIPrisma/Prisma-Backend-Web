package com.prisma.api.service;

import com.prisma.api.dto.PerfilProveedorRequest;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.exception.ApiException;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.impl.PerfilProveedorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilProveedorServiceUnitTest {

    @Mock
    private ProveedorRepository proveedorRepository;

    @InjectMocks
    private PerfilProveedorServiceImpl perfilProveedorService;

    @Test
    @DisplayName("obtenerPerfil retorna el perfil cuando el usuario pertenece al proveedor")
    void testObtenerPerfilMismoProveedor() {
        UserPrincipal usuario = new UserPrincipal(
                10L, "femsa@proveedor.com", "hash", "Operador FEMSA", 1L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );
        Proveedor proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setRazonSocial("FEMSA Colombia S.A.S");

        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));

        Proveedor resultado = perfilProveedorService.obtenerPerfil(1L, usuario);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getRazonSocial()).isEqualTo("FEMSA Colombia S.A.S");
    }

    @Test
    @DisplayName("obtenerPerfil lanza 403 Forbidden cuando el usuario intenta ver otro proveedor")
    void testObtenerPerfilOtroProveedorLanza403() {
        UserPrincipal usuario = new UserPrincipal(
                10L, "bavaria@proveedor.com", "hash", "Operador Bavaria", 2L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );

        assertThatThrownBy(() -> perfilProveedorService.obtenerPerfil(1L, usuario))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("actualizarPerfil modifica los campos del proveedor y persiste cambios")
    void testActualizarPerfilExitoso() {
        UserPrincipal usuario = new UserPrincipal(
                10L, "femsa@proveedor.com", "hash", "Operador FEMSA", 1L,
                List.of(new SimpleGrantedAuthority("ROLE_PROVEEDOR"))
        );
        Proveedor proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setRazonSocial("FEMSA Antigua");

        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(i -> i.getArgument(0));

        PerfilProveedorRequest req = new PerfilProveedorRequest();
        req.setRazonSocial("FEMSA Nueva S.A.");
        req.setNombreComercial("Coca Cola FEMSA");
        req.setContactoNombre("Carlos Gerente");
        req.setContactoEmail("GERENTE@FEMSA.COM");
        req.setContactoTelefono("3001234567");
        req.setCiudad("Bogotá");

        Proveedor actualizado = perfilProveedorService.actualizarPerfil(1L, req, usuario);

        assertThat(actualizado.getRazonSocial()).isEqualTo("FEMSA Nueva S.A.");
        assertThat(actualizado.getContactoEmail()).isEqualTo("gerente@femsa.com"); // toLowerCase
        verify(proveedorRepository).save(proveedor);
    }
}
