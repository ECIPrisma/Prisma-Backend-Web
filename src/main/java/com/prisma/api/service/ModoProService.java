package com.prisma.api.service;

import com.prisma.api.entity.ItemPedido;
import com.prisma.api.entity.Pedido;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.exception.BadRequestException;
import com.prisma.api.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modo Pro – HU-15: ranking de productos del proveedor por zona (barrio de la tienda),
 * ordenado por unidades entregadas en la ventana indicada (30 días por defecto).
 *
 * Solo cuentan los pedidos ENTREGADOS cuya fecha de entrega cae dentro de la ventana.
 * Se calcula en memoria con la misma base transaccional (sin motor externo, costo $0).
 */
@Service
public class ModoProService {

    public static final int DIAS_POR_DEFECTO = 30;
    public static final int DIAS_MAXIMO = 365;
    public static final String MENSAJE_SIN_DATOS = "Aún no hay suficientes datos para generar este reporte";
    static final String ZONA_SIN_DEFINIR = "Sin zona";

    public record RankingProducto(int posicion, Long productoId, String nombre, long unidades) {}

    public record RankingZona(String zona, long unidadesTotales, List<RankingProducto> productos) {}

    public record RankingResponse(int dias, boolean hayDatos, String mensaje, List<RankingZona> zonas) {}

    private final PedidoRepository pedidoRepository;

    public ModoProService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public RankingResponse rankingPorZona(Long proveedorId, Integer diasSolicitados) {
        int dias = diasSolicitados == null ? DIAS_POR_DEFECTO : diasSolicitados;
        if (dias < 1 || dias > DIAS_MAXIMO) {
            throw new BadRequestException("El periodo debe estar entre 1 y " + DIAS_MAXIMO + " días");
        }
        OffsetDateTime desde = OffsetDateTime.now().minusDays(dias);

        // zona -> (clave de producto -> acumulado)
        Map<String, Map<String, Acumulado>> porZona = new HashMap<>();
        for (Pedido pedido : pedidoRepository.findByProveedorIdAndEstado(proveedorId, EstadoPedido.ENTREGADO)) {
            OffsetDateTime entregado = pedido.getFechaEntregaReal();
            if (entregado == null || entregado.isBefore(desde)) {
                continue;
            }
            String zona = pedido.getTienda() != null && pedido.getTienda().getBarrio() != null
                    && !pedido.getTienda().getBarrio().isBlank()
                    ? pedido.getTienda().getBarrio().trim() : ZONA_SIN_DEFINIR;
            for (ItemPedido item : pedido.getItems()) {
                if (item.getCantidad() <= 0) {
                    continue;
                }
                Long productoId = item.getProducto() != null ? item.getProducto().getId() : null;
                String nombre = item.getNombreProductoHistorico();
                String clave = productoId != null ? "id:" + productoId : "nombre:" + nombre;
                porZona.computeIfAbsent(zona, z -> new HashMap<>())
                        .computeIfAbsent(clave, k -> new Acumulado(productoId, nombre))
                        .unidades += item.getCantidad();
            }
        }

        if (porZona.isEmpty()) {
            return new RankingResponse(dias, false, MENSAJE_SIN_DATOS, List.of());
        }

        List<RankingZona> zonas = new ArrayList<>();
        for (Map.Entry<String, Map<String, Acumulado>> e : porZona.entrySet()) {
            List<Acumulado> ordenados = new ArrayList<>(e.getValue().values());
            ordenados.sort(Comparator.comparingLong((Acumulado a) -> a.unidades).reversed()
                    .thenComparing(a -> a.nombre == null ? "" : a.nombre));
            List<RankingProducto> productos = new ArrayList<>();
            long total = 0;
            int posicion = 1;
            for (Acumulado a : ordenados) {
                productos.add(new RankingProducto(posicion++, a.productoId, a.nombre, a.unidades));
                total += a.unidades;
            }
            zonas.add(new RankingZona(e.getKey(), total, productos));
        }
        zonas.sort(Comparator.comparingLong(RankingZona::unidadesTotales).reversed()
                .thenComparing(RankingZona::zona));
        return new RankingResponse(dias, true, null, zonas);
    }

    private static final class Acumulado {
        final Long productoId;
        final String nombre;
        long unidades;

        Acumulado(Long productoId, String nombre) {
            this.productoId = productoId;
            this.nombre = nombre;
        }
    }
}
