package com.prisma.api.service.impl;

import com.prisma.api.dto.ConfirmarEntregaPinRequest;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Ruta;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.exception.ResourceNotFoundException;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.RutaRepository;
import com.prisma.api.security.UserPrincipal;
import com.prisma.api.service.RepartidorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class RepartidorServiceImpl implements RepartidorService {

    private final RutaRepository rutaRepository;
    private final PedidoRepository pedidoRepository;

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
                pedidoRepository.save(p);
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
    public Pedido confirmarEntrega(Long pedidoId, ConfirmarEntregaPinRequest request) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!pedido.getPinEntregaHash().equals(request.getPin().trim())) {
            throw new BadRequestException("El PIN ingresado es incorrecto. Verifique con el tendero.");
        }

        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.setPinValidadoEn(OffsetDateTime.now());
        pedido.setFechaEntregaReal(OffsetDateTime.now());
        pedido.setMedioPagoUtilizado(request.getMedioPago());
        if (request.getNotasEntrega() != null) {
            pedido.setNotasEntrega(request.getNotasEntrega());
        }

        Pedido guardado = pedidoRepository.save(pedido);

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
}
