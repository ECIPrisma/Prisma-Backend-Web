package com.prisma.api.event.listener;

import com.prisma.api.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Suscriptor del módulo de pagos (core-payment) ante eventos de pedido creado.
 * (HU-22 / SCRUM-34).
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final BigDecimal TARIFA_COMISION = new BigDecimal("0.035"); // 3.50%

    private int eventosRecibidos = 0;

    @EventListener
    @Async
    public void onOrderCreated(OrderCreatedEvent event) {
        eventosRecibidos++;
        BigDecimal total = event.getTotal();
        BigDecimal comision = total.multiply(TARIFA_COMISION).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netoProveedor = total.subtract(comision);

        log.info("[MÓDULO PAGOS] Evento recibido para Pedido ID: {}. Split Payment -> Total: {}, Comisión PRISMA (3.5%): {}, Neto Proveedor: {}",
                event.getPedidoId(), total, comision, netoProveedor);
    }

    public int getEventosRecibidos() {
        return eventosRecibidos;
    }
}
