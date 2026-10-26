package com.prisma.api.service;

import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.security.UserPrincipal;

import java.util.List;
import java.util.Map;

public interface RepartidorService {
    List<Ruta> getRutasHoy(UserPrincipal currentUser);
    Ruta iniciarRuta(Long rutaId);
    List<Pedido> getPedidosRuta(Long rutaId);
    Map<String, Object> getManifiestoCarga(Long rutaId);
    Pedido confirmarEntrega(Long pedidoId, ConfirmarEntregaPinRequest request);
    Map<String, Object> finalizarRuta(Long rutaId);
}
