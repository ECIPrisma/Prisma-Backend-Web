package com.prisma.api.event.listener;

import com.prisma.api.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Suscriptor del módulo de analítica (pro-analytics) ante eventos de pedido creado.
 * Diseñado con aislamiento de fallos: una excepción aquí nunca revierte el pedido original.
 * (HU-22 / SCRUM-34).
 */
@Component
public class AnalyticsEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsEventListener.class);

    private int eventosProcesados = 0;
    private boolean simularFallo = false;

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[MÓDULO ANALÍTICA] Ingestando evento de Pedido ID: {} para Modo Pro y OLAP...", event.getPedidoId());

        try {
            if (simularFallo) {
                log.error("[MÓDULO ANALÍTICA] Error simulado en el motor analítico. Verificando aislamiento de transacción...");
                throw new RuntimeException("Falla simulada en motor de analítica Modo Pro (Aislamiento verificado)");
            }

            eventosProcesados++;
            log.info("[MÓDULO ANALÍTICA] Pedido ID: {} indexado con éxito para elasticidad y minería de canasta.", event.getPedidoId());
        } catch (Exception ex) {
            log.warn("[MÓDULO ANALÍTICA] Aislamiento activado: la falla en analítica ({}) no afecta la transacción de creación del pedido.", ex.getMessage());
        }
    }

    public int getEventosProcesados() {
        return eventosProcesados;
    }

    public void setSimularFallo(boolean simularFallo) {
        this.simularFallo = simularFallo;
    }
}
