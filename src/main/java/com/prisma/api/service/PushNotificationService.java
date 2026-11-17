package com.prisma.api.service;

import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Usuario;
import com.prisma.api.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final UsuarioRepository usuarioRepository;
    private final Map<Long, List<Map<String, Object>>> buzonNotificaciones = new ConcurrentHashMap<>();

    public PushNotificationService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public void registrarFcmToken(Long usuarioId, String fcmToken) {
        if (usuarioId == null || fcmToken == null || fcmToken.isBlank()) {
            return;
        }
        usuarioRepository.findById(usuarioId).ifPresent(u -> {
            u.setFcmToken(fcmToken.trim());
            usuarioRepository.save(u);
            log.info("FCM Token registrado para usuario ID={} ({})", u.getId(), u.getEmail());
        });
    }

    public void notificarNuevoPedidoProveedor(Pedido pedido) {
        if (pedido == null || pedido.getProveedor() == null) {
            return;
        }
        String titulo = "Nuevo pedido recibido: #" + pedido.getNumeroPedido();
        String tiendaNombre = pedido.getTienda() != null ? pedido.getTienda().getNombreTienda() : "Tienda";
        String mensaje = "La tienda " + tiendaNombre + " ha enviado un pedido por $" + pedido.getTotalAPagar() + " COP.";

        List<Usuario> usuariosProveedor = usuarioRepository.findAll().stream()
                .filter(u -> u.getProveedor() != null && u.getProveedor().getId().equals(pedido.getProveedor().getId()))
                .toList();

        for (Usuario u : usuariosProveedor) {
            enviarPush(u, titulo, mensaje, "NUEVO_PEDIDO", pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado().name());
        }
    }

    /** HU-14: el pago en línea del pedido quedó confirmado (en garantía); el proveedor puede despachar. */
    public void notificarPagoConfirmadoProveedor(Pedido pedido, java.math.BigDecimal monto) {
        if (pedido == null || pedido.getProveedor() == null) {
            return;
        }
        String titulo = "Pago confirmado: pedido #" + pedido.getNumeroPedido();
        String tiendaNombre = pedido.getTienda() != null ? pedido.getTienda().getNombreTienda() : "La tienda";
        String mensaje = tiendaNombre + " pagó en línea $" + monto + " COP. El pago está en garantía: ya puedes iniciar el despacho.";

        List<Usuario> usuariosProveedor = usuarioRepository.findAll().stream()
                .filter(u -> u.getProveedor() != null && u.getProveedor().getId().equals(pedido.getProveedor().getId()))
                .toList();

        for (Usuario u : usuariosProveedor) {
            enviarPush(u, titulo, mensaje, "PAGO_CONFIRMADO", pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado().name());
        }
    }

    /** El pedido pagado en línea se canceló o rechazó: se avisa al tendero que su dinero fue reembolsado. */
    public void notificarReembolsoTendero(Pedido pedido, java.math.BigDecimal monto) {
        if (pedido == null || pedido.getTienda() == null || pedido.getTienda().getTendero() == null) {
            return;
        }
        String titulo = "Reembolso del pedido #" + pedido.getNumeroPedido();
        String mensaje = "Tu pedido fue " + pedido.getEstado().name().toLowerCase()
                + ". Te devolvimos $" + monto + " COP que estaban en garantía.";
        enviarPush(pedido.getTienda().getTendero(), titulo, mensaje, "PAGO_REEMBOLSADO",
                pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado().name());
    }

    public void notificarCambioEstadoTendero(Pedido pedido) {
        if (pedido == null || pedido.getTienda() == null || pedido.getTienda().getTendero() == null) {
            return;
        }
        Usuario tendero = pedido.getTienda().getTendero();
        String titulo = "Actualización de pedido #" + pedido.getNumeroPedido();
        String mensaje = "Tu pedido ahora se encuentra en estado: " + pedido.getEstado().name();
        enviarPush(tendero, titulo, mensaje, "CAMBIO_ESTADO_PEDIDO", pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado().name());
    }

    private void enviarPush(Usuario destino, String titulo, String cuerpo, String tipo, Long pedidoId, String numeroPedido, String estadoPedido) {
        if (destino == null || destino.getId() == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", System.currentTimeMillis());
        payload.put("usuarioId", destino.getId());
        payload.put("titulo", titulo);
        payload.put("cuerpo", cuerpo);
        payload.put("tipo", tipo);
        payload.put("pedidoId", pedidoId);
        payload.put("numeroPedido", numeroPedido);
        payload.put("estadoPedido", estadoPedido);
        payload.put("fcmTokenDestino", destino.getFcmToken() != null ? destino.getFcmToken() : "FCM_LOCAL_CHANNEL");
        payload.put("enviadoEn", OffsetDateTime.now().toString());

        buzonNotificaciones.computeIfAbsent(destino.getId(), k -> Collections.synchronizedList(new ArrayList<>())).add(0, payload);
        log.info("FCM PUSH ENVIADO -> usuarioId={}, token={}, titulo='{}', cuerpo='{}'",
                destino.getId(), payload.get("fcmTokenDestino"), titulo, cuerpo);
    }

    public List<Map<String, Object>> obtenerNotificacionesUsuario(Long usuarioId) {
        if (usuarioId == null) {
            return List.of();
        }
        return buzonNotificaciones.getOrDefault(usuarioId, List.of());
    }
}
