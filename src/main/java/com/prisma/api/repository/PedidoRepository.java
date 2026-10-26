package com.prisma.api.repository;

import com.prisma.api.entity.Pedido;
import com.prisma.api.enums.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    Optional<Pedido> findByNumeroPedido(String numeroPedido);
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.tienda LEFT JOIN FETCH p.items LEFT JOIN FETCH p.proveedor WHERE p.tienda.id = :tiendaId ORDER BY p.fechaCreacion DESC")
    List<Pedido> findByTiendaIdOrderByFechaCreacionDesc(@org.springframework.data.repository.query.Param("tiendaId") Long tiendaId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.tienda LEFT JOIN FETCH p.items LEFT JOIN FETCH p.proveedor WHERE p.tienda.tendero.id = :tenderoId ORDER BY p.fechaCreacion DESC")
    List<Pedido> findByTenderoIdOrderByFechaCreacionDesc(@org.springframework.data.repository.query.Param("tenderoId") Long tenderoId);

    List<Pedido> findByProveedorIdAndEstado(Long proveedorId, EstadoPedido estado);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.tienda LEFT JOIN FETCH p.items WHERE p.ruta.id = :rutaId ORDER BY p.ordenParada ASC")
    List<Pedido> findByRutaIdOrderByOrdenParadaAsc(@org.springframework.data.repository.query.Param("rutaId") Long rutaId);
}
