package com.prisma.api.service;

import com.prisma.api.dto.PagarPedidoRequest;
import com.prisma.api.dto.PagoPedidoResponse;
import com.prisma.api.dto.RecaudosProveedorResponse;
import com.prisma.api.entity.Pedido;
import com.prisma.api.security.UserPrincipal;

import java.util.Collection;

/**
 * N-08 – Pago en línea de pedidos con la pasarela SIMULADA y retención en garantía hasta la entrega.
 */
public interface PagoPedidoService {

    /** El tendero paga su pedido. Un rechazo de la pasarela no es un error: se devuelve con estado RECHAZADO. */
    PagoPedidoResponse pagar(Long pedidoId, PagarPedidoRequest datos, UserPrincipal tendero);

    /** Último intento de pago del pedido del tendero. */
    PagoPedidoResponse obtenerPago(Long pedidoId, UserPrincipal tendero);

    /** Completa los campos de pago (transitorios) de los pedidos para que la app los muestre. */
    void anotarEstadoPago(Collection<Pedido> pedidos);

    /** ¿El pedido está pagado en línea (en garantía o ya liberado)? */
    boolean estaPagadoEnLinea(Long pedidoId);

    /** Al confirmar la entrega con PIN: libera el dinero en garantía al proveedor. Devuelve true si había pago en línea. */
    boolean liberarGarantia(Long pedidoId);

    /** Pagos en línea de los pedidos del proveedor autenticado. */
    RecaudosProveedorResponse recaudosProveedor(UserPrincipal proveedor);
}
