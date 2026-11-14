package com.prisma.api.service.impl;

import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.RastreoCamion;
import com.prisma.api.entity.Ruta;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.RastreoCamionRepository;
import com.prisma.api.repository.RutaRepository;
import com.prisma.api.repository.UsuarioRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.PushNotificationService;
import com.prisma.api.service.RepartidorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class RepartidorServiceImpl implements RepartidorService {

    private static final int MAX_INTENTOS_PIN = 3;

    private final RutaRepository rutaRepository;
    private final PedidoRepository pedidoRepository;
    private final Map<Long, Integer> intentosPorPedido = new ConcurrentHashMap<>();
    private final Map<Long, OffsetDateTime> bloqueoPorPedido = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private PushNotificationService pushNotificationService;

    @Autowired(required = false)
    private RastreoCamionRepository rastreoCamionRepository;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    public RepartidorServiceImpl(RutaRepository rutaRepository, PedidoRepository pedidoRepository) {
        this.rutaRepository = rutaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ruta> getRutasHoy(UserPrincipal currentUser) {
        List<Ruta> rutas = rutaRepository.findByRepartidorIdAndFechaProgramada(currentUser.getId(), LocalDate.now());
        if (rutas.isEmpty()) {
            rutas = rutaRepository.findByRepartidorIdOrderByFechaProgramadaDesc(currentUser.getId());
        }
        return rutas;
    }

    @Override
    public Ruta iniciarRuta(Long rutaId) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada con ID: " + rutaId));

        ruta.setEstado("EN_EJECUCION");
        ruta.setHoraInicioReal(OffsetDateTime.now());
        Ruta guardada = rutaRepository.save(ruta);

        List<Pedido> pedidos = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
        for (Pedido p : pedidos) {
            if (p.getEstado() == EstadoPedido.CREADO || p.getEstado() == EstadoPedido.CARGADO_EN_BODEGA || p.getEstado() == EstadoPedido.ACEPTADO) {
                p.setEstado(EstadoPedido.EN_RUTA);
                Pedido pGuardado = pedidoRepository.save(p);
                if (pushNotificationService != null) {
                    pushNotificationService.notificarCambioEstadoTendero(pGuardado);
                }
            }
        }

        return guardada;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> getPedidosRuta(Long rutaId) {
        return pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getManifiestoCarga(Long rutaId) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada con ID: " + rutaId));

        List<Pedido> pedidos = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(rutaId);
        Map<String, Integer> consolidadoProductos = new HashMap<>();
        BigDecimal totalDineroEsperado = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            totalDineroEsperado = totalDineroEsperado.add(p.getTotalAPagar() != null ? p.getTotalAPagar() : BigDecimal.ZERO);
            if (p.getItems() != null) {
                for (var item : p.getItems()) {
                    String nombre = item.getNombreProductoHistorico();
                    consolidadoProductos.put(nombre, consolidadoProductos.getOrDefault(nombre, 0) + item.getCantidad());
                }
            }
        }

        Map<String, Object> manifiesto = new HashMap<>();
        manifiesto.put("codigoRuta", ruta.getCodigoRuta());
        manifiesto.put("totalParadas", pedidos.size());
        manifiesto.put("totalRecaudoEsperado", totalDineroEsperado);
        manifiesto.put("itemsCargados", consolidadoProductos);

        return manifiesto;
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public Pedido confirmarEntrega(Long pedidoId, ConfirmarEntregaPinRequest request) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        OffsetDateTime bloqueadoHasta = pedido.getPinBloqueadoHasta() != null
                ? pedido.getPinBloqueadoHasta()
                : bloqueoPorPedido.get(pedidoId);

        if (bloqueadoHasta != null && bloqueadoHasta.isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Confirmación bloqueada temporalmente por superar el límite de intentos de OTP.");
        }

        if (!pedido.getPinEntregaHash().equals(request.getPin().trim())) {
            int nuevosIntentos = Math.max(pedido.getIntentosFallidosPin(), intentosPorPedido.getOrDefault(pedidoId, 0)) + 1;
            pedido.setIntentosFallidosPin(nuevosIntentos);
            intentosPorPedido.put(pedidoId, nuevosIntentos);

            if (nuevosIntentos >= MAX_INTENTOS_PIN) {
                OffsetDateTime bloqueo = OffsetDateTime.now().plusMinutes(15);
                pedido.setPinBloqueadoHasta(bloqueo);
                bloqueoPorPedido.put(pedidoId, bloqueo);
                throw new BadRequestException("El PIN ingresado es incorrecto. Límite de reintentos alcanzado: confirmación bloqueada temporalmente.");
            }
            throw new BadRequestException("El PIN ingresado es incorrecto. Verifique con el tendero. Intentos restantes: " + (MAX_INTENTOS_PIN - nuevosIntentos));
        }

        pedido.setIntentosFallidosPin(0);
        pedido.setPinBloqueadoHasta(null);
        intentosPorPedido.remove(pedidoId);
        bloqueoPorPedido.remove(pedidoId);

        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setPinValidadoEn(OffsetDateTime.now());
        pedido.setFechaEntregaReal(OffsetDateTime.now());
        pedido.setMedioPagoUtilizado(request.getMedioPago());
        if (request.getNotasEntrega() != null) {
            pedido.setNotasEntrega(request.getNotasEntrega());
        }

        Pedido guardado = pedidoRepository.save(pedido);
        if (pushNotificationService != null) {
            pushNotificationService.notificarCambioEstadoTendero(guardado);
        }

        if (pedido.getRuta() != null) {
            Ruta r = pedido.getRuta();
            BigDecimal actual = r.getTotalRecaudoEfectivo() != null ? r.getTotalRecaudoEfectivo() : BigDecimal.ZERO;
            r.setTotalRecaudoEfectivo(actual.add(guardado.getTotalAPagar()));
            rutaRepository.save(r);
        }

        return guardado;
    }

    @Override
    public Map<String, Object> finalizarRuta(Long rutaId) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada con ID: " + rutaId));

        ruta.setEstado("FINALIZADA");
        ruta.setHoraFinReal(OffsetDateTime.now());
        rutaRepository.save(ruta);

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("codigoRuta", ruta.getCodigoRuta());
        resumen.put("estado", "FINALIZADA");
        resumen.put("horaFin", ruta.getHoraFinReal());
        resumen.put("totalRecaudadoEfectivo", ruta.getTotalRecaudoEfectivo());
        resumen.put("mensaje", "Jornada completada exitosamente. Fondos listos para liquidación.");

        return resumen;
    }

    @Override
    public RastreoCamion registrarPosicion(Long rutaId, BigDecimal latitud, BigDecimal longitud, BigDecimal velocidadKmh, BigDecimal rumboGrados, UserPrincipal currentUser) {
        Ruta ruta = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada con ID: " + rutaId));

        RastreoCamion r = new RastreoCamion();
        r.setRuta(ruta);
        r.setCamion(ruta.getCamion());

        Usuario repartidor = ruta.getRepartidor();
        if (repartidor == null && currentUser != null && usuarioRepository != null) {
            repartidor = usuarioRepository.findById(currentUser.getId()).orElse(null);
        }
        r.setRepartidor(repartidor);
        r.setLatitud(latitud != null ? latitud : new BigDecimal("4.6097100"));
        r.setLongitud(longitud != null ? longitud : new BigDecimal("-74.0817500"));
        r.setVelocidadKmh(velocidadKmh != null ? velocidadKmh : BigDecimal.ZERO);
        r.setRumboGrados(rumboGrados != null ? rumboGrados : BigDecimal.ZERO);
        r.setTimestamp(OffsetDateTime.now());

        if (rastreoCamionRepository != null) {
            return rastreoCamionRepository.save(r);
        }
        return r;
    }
}
