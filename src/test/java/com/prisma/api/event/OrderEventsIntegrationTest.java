package com.prisma.api.event;

import com.prisma.api.entity.Pedido;
import com.prisma.api.event.listener.AnalyticsEventListener;
import com.prisma.api.event.listener.PaymentEventListener;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.service.PedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración para HU-22 (SCRUM-34): Publicación de eventos de dominio entre módulos.
 *
 * Criterios de Aceptación:
 * 1. Evento publicado correctamente: Al crear un pedido, se emite OrderCreatedEvent y los módulos
 *    suscritos (core-payment, pro-analytics) lo reciben sin invocación directa.
 * 2. Aislamiento ante fallo del suscriptor: Si el módulo de analítica falla al procesar el evento,
 *    la transacción del pedido en core-order NO se revierte ni se ve afectada.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SCRUM-34: Publicación de Eventos de Dominio y Aislamiento de Fallos")
public class OrderEventsIntegrationTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private AnalyticsEventListener analyticsEventListener;

    @Test
    @DisplayName("Criterio 1: OrderCreatedEvent es recibido por módulos de pagos y analítica de forma desacoplada")
    public void debePublicarEventoYSerRecibidoPorSuscriptoresDesacoplados() {
        int eventosPagosAntes = paymentEventListener.getEventosRecibidos();
        int eventosAnaliticaAntes = analyticsEventListener.getEventosProcesados();

        BigDecimal total = new BigDecimal("150000.00");
        Pedido pedido = pedidoService.crearPedido(1L, 1L, total, 4);

        assertNotNull(pedido);
        assertNotNull(pedido.getId());
        assertEquals(total, pedido.getTotalAPagar());

        // Verificar que los listeners recibieron el evento de dominio
        assertTrue(paymentEventListener.getEventosRecibidos() > eventosPagosAntes,
                "El módulo de pagos debe haber recibido el OrderCreatedEvent sin acoplamiento");
        assertTrue(analyticsEventListener.getEventosProcesados() > eventosAnaliticaAntes,
                "El módulo de analítica debe haber recibido el OrderCreatedEvent sin acoplamiento");
    }

    @Test
    @DisplayName("Criterio 2: Aislamiento ante fallo del suscriptor de analítica no afecta la persistencia del pedido")
    public void fallaEnSuscriptorNoDebeRevertirCreacionDePedido() {
        // Activar fallo simulado en el motor de analítica
        analyticsEventListener.setSimularFallo(true);

        BigDecimal total = new BigDecimal("85000.00");
        Pedido pedido = null;

        try {
            pedido = pedidoService.crearPedido(1L, 1L, total, 2);
        } finally {
            analyticsEventListener.setSimularFallo(false);
        }

        // El pedido debe existir y haberse persistido a pesar del fallo en analítica
        assertNotNull(pedido, "El pedido debe haberse creado a pesar del fallo en el suscriptor");
        assertNotNull(pedido.getId(), "El pedido debe tener un ID persistido en base de datos");
        assertTrue(pedidoRepository.existsById(pedido.getId()),
                "La transacción del pedido debe estar confirmada en base de datos sin rollback");
    }
}
