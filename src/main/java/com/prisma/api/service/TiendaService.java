package com.prisma.api.service;

import com.prisma.api.entity.Disputa;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Tienda;
import com.prisma.api.security.UserPrincipal;

import java.util.List;
import java.util.Map;

public interface TiendaService {
    Tienda getMiTienda(UserPrincipal currentUser);
    List<Producto> getCatalogo(Long proveedorId);
    List<Pedido> getMisPedidos(UserPrincipal currentUser);
    Pedido crearPedido(Map<String, Object> payload, UserPrincipal currentUser);
    Pedido repetirUltimoPedido(UserPrincipal currentUser);
    Disputa reportarAveria(Long pedidoId, Map<String, String> payload, UserPrincipal currentUser);
}
