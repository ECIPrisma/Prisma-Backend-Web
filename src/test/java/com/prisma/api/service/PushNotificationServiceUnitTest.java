package com.prisma.api.service;

import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.Tienda;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceUnitTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PushNotificationService pushNotificationService;

    @Test
    @DisplayName("N-07 (AAA): registrarFcmToken actualiza y persiste el token en el usuario")
    void testRegistrarFcmToken() {
        // Arrange
        Usuario u = new Usuario();
        u.setId(10L);
        u.setEmail("tendero@oasis.com");
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));

        // Act
        pushNotificationService.registrarFcmToken(10L, "  fcm_token_xyz  ");

        // Assert
        assertThat(u.getFcmToken()).isEqualTo("fcm_token_xyz");
        verify(usuarioRepository).save(u);
    }

    @Test
    @DisplayName("N-07 Edge Case (AAA): registrarFcmToken ignora valores nulos o vacíos sin consultar BD")
    void testRegistrarFcmTokenEdgeCasesVacios() {
        // Act
        pushNotificationService.registrarFcmToken(null, "token");
        pushNotificationService.registrarFcmToken(10L, null);
        pushNotificationService.registrarFcmToken(10L, "   ");

        // Assert
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("N-07 (AAA): notificarCambioEstadoTendero y notificarNuevoPedidoProveedor encolan notificaciones")
    void testNotificarNuevoPedidoYCambioEstado() {
        // Arrange
        Proveedor prov = new Proveedor();
        prov.setId(1L);

        Usuario adminProv = new Usuario();
        adminProv.setId(20L);
        adminProv.setEmail("ventas@femsa.com");
        adminProv.setProveedor(prov);
        adminProv.setFcmToken("fcm_prov_20");

        Usuario tendero = new Usuario();
        tendero.setId(30L);
        tendero.setEmail("tendero@oasis.com");
        tendero.setFcmToken("fcm_tendero_30");

        Tienda tienda = new Tienda();
        tienda.setId(5L);
        tienda.setNombreTienda("Tienda El Oasis");
        tienda.setTendero(tendero);

        Pedido pedido = new Pedido();
        pedido.setId(101L);
        pedido.setNumeroPedido("PED-2026-0101");
        pedido.setTotalAPagar(new BigDecimal("95000.00"));
        pedido.setEstado(EstadoPedido.EN_RUTA);
        pedido.setProveedor(prov);
        pedido.setTienda(tienda);

        when(usuarioRepository.findAll()).thenReturn(List.of(adminProv));

        // Act
        pushNotificationService.notificarNuevoPedidoProveedor(pedido);
        pushNotificationService.notificarCambioEstadoTendero(pedido);

        // Assert
        List<Map<String, Object>> notifProv = pushNotificationService.obtenerNotificacionesUsuario(20L);
        List<Map<String, Object>> notifTendero = pushNotificationService.obtenerNotificacionesUsuario(30L);

        assertThat(notifProv).hasSize(1);
        assertThat(notifProv.get(0).get("tipo")).isEqualTo("NUEVO_PEDIDO");
        assertThat(notifTendero).hasSize(1);
        assertThat(notifTendero.get(0).get("tipo")).isEqualTo("CAMBIO_ESTADO_PEDIDO");
        assertThat(notifTendero.get(0).get("estadoPedido")).isEqualTo("EN_RUTA");
    }
}
