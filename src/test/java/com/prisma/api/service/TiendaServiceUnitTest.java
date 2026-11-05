package com.prisma.api.service;

import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Tienda;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.impl.TiendaServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendaServiceUnitTest {

    @Mock
    private TiendaRepository tiendaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ItemPedidoRepository itemPedidoRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private DisputaRepository disputaRepository;

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private TiendaServiceImpl tiendaService;

    @Test
    @DisplayName("getMiTienda retorna la tienda asociada al tendero autenticado")
    void testGetMiTienda() {
        UserPrincipal tendero = new UserPrincipal(
                3L, "tendero@oasis.com", "hash", "Pedro Tienda", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        Tienda tienda = new Tienda();
        tienda.setId(1L);
        tienda.setNombreTienda("Tienda El Oasis");

        when(tiendaRepository.findFirstByTenderoId(3L)).thenReturn(Optional.of(tienda));

        Tienda resultado = tiendaService.getMiTienda(tendero);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getNombreTienda()).isEqualTo("Tienda El Oasis");
        verify(tiendaRepository).findFirstByTenderoId(3L);
    }

    @Test
    @DisplayName("getCatalogo retorna productos activos del proveedor")
    void testGetCatalogo() {
        Producto p1 = new Producto();
        p1.setId(100L);
        p1.setNombre("Coca-Cola 1.5L");
        p1.setEstado("ACTIVO");

        when(productoRepository.findByProveedorIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(p1));

        List<Producto> catalogo = tiendaService.getCatalogo(1L);

        assertThat(catalogo).hasSize(1);
        assertThat(catalogo.get(0).getNombre()).isEqualTo("Coca-Cola 1.5L");
    }

    @Test
    @DisplayName("repetirUltimoPedido lanza BadRequestException si no hay pedidos previos")
    void testRepetirUltimoPedidoSinHistorial() {
        UserPrincipal tendero = new UserPrincipal(
                3L, "tendero@oasis.com", "hash", "Pedro Tienda", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        Tienda tienda = new Tienda();
        tienda.setId(1L);

        when(tiendaRepository.findFirstByTenderoId(3L)).thenReturn(Optional.of(tienda));
        when(pedidoRepository.findByTenderoIdOrderByFechaCreacionDesc(3L)).thenReturn(Collections.emptyList());
        when(pedidoRepository.findByTiendaIdOrderByFechaCreacionDesc(1L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> tiendaService.repetirUltimoPedido(tendero))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No hay pedidos previos");
    }

    @Test
    @DisplayName("HU-09 (AAA): crearPedido con Idempotency-Key repetida retorna el pedido existente sin duplicar")
    void testCrearPedidoIdempotencyKeyExistente() {
        // Arrange
        UserPrincipal tendero = new UserPrincipal(
                3L, "tendero@oasis.com", "hash", "Pedro Tienda", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        Pedido existente = new Pedido();
        existente.setId(77L);
        existente.setNumeroPedido("PED-2026-0077");
        existente.setIdempotencyKey("uuid-idem-77");

        when(pedidoRepository.findByIdempotencyKey("uuid-idem-77")).thenReturn(Optional.of(existente));

        // Act
        Pedido resultado = tiendaService.crearPedido(java.util.Map.of("idempotencyKey", "uuid-idem-77"), tendero);

        // Assert
        assertThat(resultado.getId()).isEqualTo(77L);
        assertThat(resultado.getNumeroPedido()).isEqualTo("PED-2026-0077");
        verify(pedidoRepository).findByIdempotencyKey("uuid-idem-77");
    }

    @Test
    @DisplayName("HU-08 Edge Case (AAA): crearPedido con producto agotado (stock = 0) lanza BadRequestException")
    void testCrearPedidoProductoAgotado() {
        // Arrange
        UserPrincipal tendero = new UserPrincipal(
                3L, "tendero@oasis.com", "hash", "Pedro Tienda", null,
                List.of(new SimpleGrantedAuthority("ROLE_TENDERO"))
        );
        Tienda tienda = new Tienda();
        tienda.setId(1L);
        com.prisma.api.entity.Proveedor prov = new com.prisma.api.entity.Proveedor();
        prov.setId(1L);

        Producto prodAgotado = new Producto();
        prodAgotado.setId(50L);
        prodAgotado.setNombre("Agua Brisa 600ml");
        prodAgotado.setStockDisponible(0);
        prodAgotado.setCantidadMinimaPedido(1);

        when(tiendaRepository.findFirstByTenderoId(3L)).thenReturn(Optional.of(tienda));
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(prov));
        when(productoRepository.findById(50L)).thenReturn(Optional.of(prodAgotado));

        java.util.Map<String, Object> payload = java.util.Map.of(
                "proveedorId", 1L,
                "items", List.of(java.util.Map.of("productoId", 50L, "cantidad", 2))
        );

        // Act & Assert
        assertThatThrownBy(() -> tiendaService.crearPedido(payload, tendero))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Agotado");
    }
}
