package com.prisma.api.event;

import org.springframework.context.ApplicationEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento de dominio desacoplado que se emite cuando un pedido es creado exitosamente.
 * (HU-22 / SCRUM-34).
 */
public class OrderCreatedEvent extends ApplicationEvent {

    private final Long pedidoId;
    private final Long tiendaId;
    private final Long proveedorId;
    private final BigDecimal total;
    private final int itemsCount;
    private final LocalDateTime eventDateTime;

    public OrderCreatedEvent(Object source, Long pedidoId, Long tiendaId, Long proveedorId, BigDecimal total, int itemsCount) {
        super(source);
        this.pedidoId = pedidoId;
        this.tiendaId = tiendaId;
        this.proveedorId = proveedorId;
        this.total = total;
        this.itemsCount = itemsCount;
        this.eventDateTime = LocalDateTime.now();
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Long getTiendaId() {
        return tiendaId;
    }

    public Long getProveedorId() {
        return proveedorId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public int getItemsCount() {
        return itemsCount;
    }

    public LocalDateTime getEventDateTime() {
        return eventDateTime;
    }

    @Override
    public String toString() {
        return "OrderCreatedEvent{" +
                "pedidoId=" + pedidoId +
                ", tiendaId=" + tiendaId +
                ", proveedorId=" + proveedorId +
                ", total=" + total +
                ", itemsCount=" + itemsCount +
                ", eventDateTime=" + eventDateTime +
                '}';
    }
}
