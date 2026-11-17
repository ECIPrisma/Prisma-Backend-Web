package com.prisma.api.service;

import com.prisma.api.entity.Pedido;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de dominio para el ciclo de vida de pedidos con eventos desacoplados.
 * (HU-22 / SCRUM-34).
 */
public interface PedidoService {

    Pedido crearPedido(Long tiendaId, Long proveedorId, BigDecimal total, int itemsCount);

    Pedido finalizarPedido(Long pedidoId);

    Optional<Pedido> obtenerPorId(Long pedidoId);

    List<Pedido> listarTodos();
}
