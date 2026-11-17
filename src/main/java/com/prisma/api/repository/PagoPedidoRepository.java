package com.prisma.api.repository;

import com.prisma.api.entity.PagoPedido;
import com.prisma.api.enums.EstadoPagoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoPedidoRepository extends JpaRepository<PagoPedido, Long> {
    Optional<PagoPedido> findFirstByPedidoIdOrderByCreadoEnDescIdDesc(Long pedidoId);
    Optional<PagoPedido> findByReferencia(String referencia);
    Optional<PagoPedido> findFirstByPedidoIdAndEstadoIn(Long pedidoId, Collection<EstadoPagoPedido> estados);
    List<PagoPedido> findByPedidoIdInOrderByCreadoEnAscIdAsc(Collection<Long> pedidoIds);
    List<PagoPedido> findByProveedorIdAndEstadoInOrderByCreadoEnDesc(Long proveedorId, Collection<EstadoPagoPedido> estados);
}
