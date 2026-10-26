package com.prisma.api.controller;

import com.prisma.api.aspect.AuditableAction;
import com.prisma.api.dto.ApiResponse;
import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.prisma.api.enums.SeveridadAudit;
import com.prisma.api.enums.TipoIncidente;
import com.prisma.api.enums.Rol;
import com.prisma.api.repository.*;
import com.prisma.api.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.prisma.api.enums.Rol;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api/v1/proveedores")
@Tag(name = "Proveedores (Portal Web React)", description = "Gestión de catálogo, despacho de rutas, flota y facturación quincenal B2B")
public class ProveedorController {

    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final ProveedorRepository proveedorRepository;
    private final CategoriaRepository categoriaRepository;
    private final CamionRepository camionRepository;
    private final LiquidacionQuincenalRepository liquidacionRepository;
    private final DisputaRepository disputaRepository;
    private final RutaRepository rutaRepository;
    private final TiendaRepository tiendaRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ProveedorController(
            ProductoRepository productoRepository,
            PedidoRepository pedidoRepository,
            ProveedorRepository proveedorRepository,
            CategoriaRepository categoriaRepository,
            CamionRepository camionRepository,
            LiquidacionQuincenalRepository liquidacionRepository,
            DisputaRepository disputaRepository,
            RutaRepository rutaRepository,
            TiendaRepository tiendaRepository,
            ItemPedidoRepository itemPedidoRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.proveedorRepository = proveedorRepository;
        this.categoriaRepository = categoriaRepository;
        this.camionRepository = camionRepository;
        this.liquidacionRepository = liquidacionRepository;
        this.disputaRepository = disputaRepository;
        this.rutaRepository = rutaRepository;
        this.tiendaRepository = tiendaRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static boolean esAdmin(UserPrincipal u) {
        return u.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
    }

    /** Aislamiento multi-tenant: un proveedor solo modifica productos de su propio catálogo. */
    private Producto productoPropio(Long id, UserPrincipal currentUser) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado con ID: " + id));
        if (!esAdmin(currentUser) && !p.getProveedor().getId().equals(currentUser.getProveedorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El producto pertenece a otro proveedor");
        }
        return p;
    }

    private Long resolveProveedorId(UserPrincipal currentUser) {
        if (currentUser != null && currentUser.getProveedorId() != null) {
            return currentUser.getProveedorId();
        }
        // Fallback por defecto para pruebas/admin
        return 1L;
    }

    @GetMapping("/mis-productos")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Listar catálogo de productos de la empresa distribuidora")
    public ResponseEntity<ApiResponse<List<Producto>>> getMisProductos(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<Producto> productos = productoRepository.findByProveedorIdAndEstadoNot(provId, "INACTIVO");
        return ResponseEntity.ok(ApiResponse.exito("Productos del proveedor", productos));
    }

    @PostMapping("/productos")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Crear nuevo producto en el catálogo de la distribuidora")
    public ResponseEntity<ApiResponse<Producto>> crearProducto(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, Object> body) {
        
        Long provId = resolveProveedorId(currentUser);
        Proveedor prov = proveedorRepository.findById(provId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado: " + provId));

        Producto p = new Producto();
        p.setProveedor(prov);
        p.setSku((String) body.getOrDefault("sku", "SKU-" + System.currentTimeMillis()));
        p.setNombre((String) body.getOrDefault("nombre", "Nuevo Producto"));
        p.setDescripcion((String) body.getOrDefault("descripcion", ""));
        p.setPresentacion((String) body.getOrDefault("presentacion", "Unidad"));
        
        Object precioObj = body.get("precioUnitario");
        BigDecimal precio = precioObj != null ? new BigDecimal(precioObj.toString()) : BigDecimal.valueOf(1000);
        p.setPrecioUnitario(precio);

        Object precioSugeridoObj = body.get("precioSugeridoPublico");
        p.setPrecioSugeridoPublico(precioSugeridoObj != null ? new BigDecimal(precioSugeridoObj.toString()) : precio.multiply(BigDecimal.valueOf(1.2)));

        Object stockObj = body.get("stockDisponible");
        p.setStockDisponible(stockObj != null ? Integer.parseInt(stockObj.toString()) : 50);

        Object cantMinObj = body.get("cantidadMinimaPedido");
        p.setCantidadMinimaPedido(cantMinObj != null ? Integer.parseInt(cantMinObj.toString()) : 1);

        p.setEsRetornable(Boolean.parseBoolean(String.valueOf(body.getOrDefault("esRetornable", false))));
        p.setImagenUrl((String) body.getOrDefault("imagenUrl", "/logo_icono_tienda.png"));
        p.setEstado((String) body.getOrDefault("estado", "ACTIVO"));

        Long catId = body.get("categoriaId") != null ? Long.parseLong(body.get("categoriaId").toString()) : 1L;
        Categoria categoria = categoriaRepository.findById(catId).orElse(null);
        p.setCategoria(categoria);

        Producto guardado = productoRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Producto creado con éxito", guardado));
    }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Actualizar precio, stock o estado de un producto")
public ResponseEntity<ApiResponse<Producto>> actualizarProducto(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Producto p = productoPropio(id, currentUser);

        if (body.containsKey("nombre")) p.setNombre(body.get("nombre").toString());
        if (body.containsKey("presentacion")) p.setPresentacion(body.get("presentacion").toString());
        if (body.containsKey("precioUnitario")) p.setPrecioUnitario(new BigDecimal(body.get("precioUnitario").toString()));
        if (body.containsKey("precioSugeridoPublico")) p.setPrecioSugeridoPublico(new BigDecimal(body.get("precioSugeridoPublico").toString()));
        if (body.containsKey("stockDisponible")) p.setStockDisponible(Integer.parseInt(body.get("stockDisponible").toString()));
        if (body.containsKey("imagenUrl")) p.setImagenUrl(body.get("imagenUrl").toString());
        if (body.containsKey("estado")) p.setEstado(body.get("estado").toString());

        Producto actualizado = productoRepository.save(p);
        return ResponseEntity.ok(ApiResponse.exito("Producto actualizado", actualizado));
    }

    @DeleteMapping("/productos/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Eliminar o archivar un producto del catálogo")
public ResponseEntity<ApiResponse<String>> eliminarProducto(@PathVariable Long id,
                                                                @AuthenticationPrincipal UserPrincipal currentUser) {
        Producto p = productoPropio(id, currentUser);
        p.setEstado("INACTIVO");
        productoRepository.save(p);
        return ResponseEntity.ok(ApiResponse.exito("Producto retirado del catálogo exitosamente", "OK"));
    }

    @GetMapping("/rutas/progreso")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Obtener progreso en tiempo real de las rutas de entrega del proveedor")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProgresoRutas(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<Ruta> rutas = rutaRepository.findByProveedorId(provId);

        List<Map<String, Object>> resultado = rutas.stream().map(r -> {
            List<Pedido> pedidosRuta = pedidoRepository.findByRutaIdOrderByOrdenParadaAsc(r.getId());
            long entregados = pedidosRuta.stream().filter(ped -> ped.getEstado() == EstadoPedido.ENTREGADO).count();
            long cancelados = pedidosRuta.stream().filter(ped -> ped.getEstado() == EstadoPedido.CANCELADO || ped.getEstado() == EstadoPedido.RECHAZADO).count();
            long pendientes = pedidosRuta.size() - entregados - cancelados;

            BigDecimal recaudoCompletado = pedidosRuta.stream()
                    .filter(ped -> ped.getEstado() == EstadoPedido.ENTREGADO)
                    .map(Pedido::getTotalAPagar)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalEsperado = pedidosRuta.stream()
                    .map(Pedido::getTotalAPagar)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int porcentaje = pedidosRuta.isEmpty() ? 0 : (int) Math.round(((double) entregados / pedidosRuta.size()) * 100);

            Map<String, Object> dto = new HashMap<>();
            dto.put("rutaId", r.getId());
            dto.put("codigoRuta", r.getCodigoRuta());
            dto.put("zonaSector", r.getZonaSector());
            dto.put("estado", r.getEstado());
            dto.put("fechaProgramada", r.getFechaProgramada());
            dto.put("placaCamion", r.getCamion() != null ? r.getCamion().getPlaca() : "N/A");
            dto.put("modeloCamion", r.getCamion() != null ? r.getCamion().getMarcaModelo() : "");
            dto.put("nombreRepartidor", r.getRepartidor() != null ? r.getRepartidor().getNombreCompleto() : "Conductor Asignado");
            dto.put("totalParadas", pedidosRuta.size());
            dto.put("paradasCompletadas", entregados);
            dto.put("paradasCanceladas", cancelados);
            dto.put("paradasPendientes", pendientes);
            dto.put("porcentajeProgreso", porcentaje);
            dto.put("recaudoEfectivoCobrado", recaudoCompletado);
            dto.put("recaudoTotalEsperado", totalEsperado);

            List<Map<String, Object>> paradas = pedidosRuta.stream().map(ped -> {
                Map<String, Object> parada = new HashMap<>();
                parada.put("pedidoId", ped.getId());
                parada.put("numeroPedido", ped.getNumeroPedido());
                parada.put("ordenParada", ped.getOrdenParada());
                parada.put("nombreTienda", ped.getTienda() != null ? ped.getTienda().getNombreTienda() : "Tienda");
                parada.put("direccion", ped.getTienda() != null ? ped.getTienda().getDireccion() : "");
                parada.put("estado", ped.getEstado().name());
                parada.put("totalAPagar", ped.getTotalAPagar());
                parada.put("notas", ped.getNotasEntrega());
                return parada;
            }).toList();
            dto.put("paradas", paradas);

            return dto;
        }).toList();

        return ResponseEntity.ok(ApiResponse.exito("Progreso de rutas", resultado));
    }

    @GetMapping("/pedidos/nuevos")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Consultar todos los pedidos de la empresa proveedora")
    public ResponseEntity<ApiResponse<List<Pedido>>> getPedidosNuevos(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(provId))
                .toList();
        return ResponseEntity.ok(ApiResponse.exito("Pedidos de la empresa", pedidos));
    }

    @PostMapping("/pedidos/{pedidoId}/cambiar-estado")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Avanzar el estado del pedido (Fulfillment / Despacho)")
    public ResponseEntity<ApiResponse<Pedido>> cambiarEstadoPedido(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> body) {
        
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        String nuevoEstado = body.get("estado");
        if (nuevoEstado != null) {
            pedido.setEstado(EstadoPedido.valueOf(nuevoEstado));
            pedido.setActualizadoEn(OffsetDateTime.now());
        }

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Estado de pedido actualizado a " + nuevoEstado, guardado));
    }

    @GetMapping("/repartidores")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Listar conductores y repartidores asignados al distribuidor")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRepartidores(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<Usuario> repartidores = usuarioRepository.findByProveedorIdAndRol(provId, Rol.ROLE_REPARTIDOR);
        List<Map<String, Object>> res = repartidores.stream().map(u -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", u.getId());
            dto.put("nombreCompleto", u.getNombreCompleto());
            dto.put("email", u.getEmail());
            dto.put("telefono", u.getTelefono());
            dto.put("estado", u.getEstado());
            return dto;
        }).toList();
        return ResponseEntity.ok(ApiResponse.exito("Conductores y repartidores", res));
    }

    @PostMapping("/repartidores")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Crear nuevo repartidor para acceso a la aplicación móvil")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearRepartidor(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, String> body) {
        Long provId = resolveProveedorId(currentUser);
        Proveedor prov = proveedorRepository.findById(provId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado: " + provId));

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("El correo electrónico del conductor es obligatorio.");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new RuntimeException("Ya existe un usuario registrado con el correo: " + email);
        }

        String password = body.getOrDefault("password", "repartidor123");
        String nombre = body.getOrDefault("nombreCompleto", "Conductor / Repartidor");
        String telefono = body.getOrDefault("telefono", "");

        Usuario nuevo = new Usuario();
        nuevo.setEmail(email);
        nuevo.setPasswordHash(passwordEncoder.encode(password));
        nuevo.setNombreCompleto(nombre);
        nuevo.setTelefono(telefono);
        nuevo.setRol(Rol.ROLE_REPARTIDOR);
        nuevo.setEstado("ACTIVO");
        nuevo.setProveedor(prov);

        Usuario guardado = usuarioRepository.save(nuevo);

        Map<String, Object> dto = new HashMap<>();
        dto.put("id", guardado.getId());
        dto.put("nombreCompleto", guardado.getNombreCompleto());
        dto.put("email", guardado.getEmail());
        dto.put("telefono", guardado.getTelefono());
        dto.put("rol", "ROLE_REPARTIDOR");
        dto.put("mensajeAcceso", "Credenciales listas para entrar a la aplicación móvil del repartidor.");

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Conductor / Repartidor registrado exitosamente", dto));
    }

    @GetMapping("/camiones")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Listar camiones de la flota de la distribuidora con cuenta móvil única")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCamiones(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        Proveedor prov = proveedorRepository.findById(provId).orElse(null);
        List<Camion> camiones = camionRepository.findByProveedorId(provId);
        List<Map<String, Object>> res = new ArrayList<>();

        for (Camion c : camiones) {
            // Auto-asegurar que todo camión cuente con su terminal móvil única
            if (c.getRepartidor() == null && prov != null) {
                String cleanPlaca = c.getPlaca().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                String emailDefault = "camion." + cleanPlaca + "@distribucion.prisma.com";
                Usuario rep = usuarioRepository.findByEmail(emailDefault).orElseGet(() -> {
                    Usuario nuevo = new Usuario();
                    nuevo.setEmail(emailDefault);
                    nuevo.setPasswordHash(passwordEncoder.encode("123456"));
                    nuevo.setNombreCompleto("Camión " + c.getPlaca());
                    nuevo.setTelefono(prov.getContactoTelefono() != null ? prov.getContactoTelefono() : "3000000000");
                    nuevo.setRol(Rol.ROLE_REPARTIDOR);
                    nuevo.setProveedor(prov);
                    nuevo.setEstado("ACTIVO");
                    return usuarioRepository.save(nuevo);
                });
                c.setRepartidor(rep);
                camionRepository.save(c);
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", c.getId());
            dto.put("placa", c.getPlaca());
            dto.put("marcaModelo", c.getMarcaModelo());
            dto.put("capacidadPesoKg", c.getCapacidadPesoKg());
            dto.put("capacidadCanastas", c.getCapacidadCanastas());
            dto.put("estado", c.getEstado());
            if (c.getRepartidor() != null) {
                dto.put("repartidorId", c.getRepartidor().getId());
                dto.put("repartidorNombre", c.getRepartidor().getNombreCompleto());
                dto.put("repartidorTelefono", c.getRepartidor().getTelefono());
                dto.put("repartidorEmail", c.getRepartidor().getEmail());
                dto.put("usuarioApp", c.getRepartidor().getEmail());
            } else {
                String cleanPlaca = c.getPlaca().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                dto.put("repartidorId", null);
                dto.put("repartidorNombre", "Camión " + c.getPlaca());
                dto.put("repartidorTelefono", null);
                dto.put("repartidorEmail", "camion." + cleanPlaca + "@distribucion.prisma.com");
                dto.put("usuarioApp", "camion." + cleanPlaca + "@distribucion.prisma.com");
            }
            res.add(dto);
        }
        return ResponseEntity.ok(ApiResponse.exito("Flota de camiones", res));
    }

    @PostMapping("/camiones")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Registrar nuevo camión a la flota con cuenta y terminal móvil única")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearCamion(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody Map<String, Object> body) {
        Long provId = resolveProveedorId(currentUser);
        Proveedor prov = proveedorRepository.findById(provId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + provId));

        if (body.get("placa") == null || body.get("placa").toString().isBlank()) {
            throw new RuntimeException("La placa del vehículo es obligatoria.");
        }
        String placa = body.get("placa").toString().trim().toUpperCase();

        boolean existe = camionRepository.findByProveedorId(provId).stream()
                .anyMatch(c -> c.getPlaca().equalsIgnoreCase(placa));
        if (existe) {
            throw new RuntimeException("Ya existe un camión registrado con la placa: " + placa);
        }

        String pin = "123456";
        if (body.containsKey("pin") && !body.get("pin").toString().isBlank()) {
            pin = body.get("pin").toString().trim();
        } else if (body.containsKey("password") && !body.get("password").toString().isBlank()) {
            pin = body.get("password").toString().trim();
        }

        String cleanPlaca = placa.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        String emailCamion = "camion." + cleanPlaca + "@distribucion.prisma.com";

        Usuario repartidor = usuarioRepository.findByEmail(emailCamion).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setEmail(emailCamion);
            u.setRol(Rol.ROLE_REPARTIDOR);
            u.setTelefono(prov.getContactoTelefono() != null ? prov.getContactoTelefono() : "3000000000");
            u.setProveedor(prov);
            u.setEstado("ACTIVO");
            return u;
        });

        repartidor.setNombreCompleto("Camión " + placa);
        repartidor.setPasswordHash(passwordEncoder.encode(pin));
        if (body.containsKey("telefono") && body.get("telefono") != null && !body.get("telefono").toString().isBlank()) {
            repartidor.setTelefono(body.get("telefono").toString());
        } else if (repartidor.getTelefono() == null || repartidor.getTelefono().isBlank()) {
            repartidor.setTelefono(prov.getContactoTelefono() != null ? prov.getContactoTelefono() : "3000000000");
        }
        repartidor.setProveedor(prov);
        repartidor.setEstado("ACTIVO");
        Usuario repartidorGuardado = usuarioRepository.save(repartidor);

        Camion c = new Camion();
        c.setProveedor(prov);
        c.setPlaca(placa);
        c.setMarcaModelo(body.getOrDefault("marcaModelo", "Camión de Carga").toString());
        c.setCapacidadPesoKg(new BigDecimal(body.getOrDefault("capacidadPesoKg", "3500.00").toString()));
        c.setCapacidadCanastas(Integer.parseInt(body.getOrDefault("capacidadCanastas", "150").toString()));
        c.setEstado(body.getOrDefault("estado", "DISPONIBLE").toString());
        c.setRepartidor(repartidorGuardado);

        Camion guardado = camionRepository.save(c);

        Map<String, Object> dto = new HashMap<>();
        dto.put("id", guardado.getId());
        dto.put("placa", guardado.getPlaca());
        dto.put("marcaModelo", guardado.getMarcaModelo());
        dto.put("capacidadPesoKg", guardado.getCapacidadPesoKg());
        dto.put("capacidadCanastas", guardado.getCapacidadCanastas());
        dto.put("estado", guardado.getEstado());
        dto.put("repartidorId", repartidorGuardado.getId());
        dto.put("repartidorNombre", repartidorGuardado.getNombreCompleto());
        dto.put("repartidorEmail", repartidorGuardado.getEmail());
        dto.put("usuarioApp", repartidorGuardado.getEmail());
        dto.put("pinAcceso", pin);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Vehículo registrado exitosamente con terminal móvil", dto));
    }

    @PutMapping("/camiones/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Actualizar datos, PIN de acceso móvil o estado operativo del camión")
    public ResponseEntity<ApiResponse<Map<String, Object>>> actualizarCamion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Camion c = camionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Camión no encontrado con ID: " + id));

        if (body.containsKey("placa")) c.setPlaca(body.get("placa").toString().toUpperCase());
        if (body.containsKey("marcaModelo")) c.setMarcaModelo(body.get("marcaModelo").toString());
        if (body.containsKey("capacidadPesoKg")) c.setCapacidadPesoKg(new BigDecimal(body.get("capacidadPesoKg").toString()));
        if (body.containsKey("capacidadCanastas")) c.setCapacidadCanastas(Integer.parseInt(body.get("capacidadCanastas").toString()));
        if (body.containsKey("estado")) c.setEstado(body.get("estado").toString());

        // Si actualizaron PIN o clave de acceso móvil
        String nuevoPin = null;
        if (body.containsKey("pin") && !body.get("pin").toString().isBlank()) {
            nuevoPin = body.get("pin").toString().trim();
        } else if (body.containsKey("password") && !body.get("password").toString().isBlank()) {
            nuevoPin = body.get("password").toString().trim();
        }

        if (c.getRepartidor() != null) {
            Usuario rep = c.getRepartidor();
            if (nuevoPin != null) {
                rep.setPasswordHash(passwordEncoder.encode(nuevoPin));
            }
            if (body.containsKey("telefono") && body.get("telefono") != null) {
                rep.setTelefono(body.get("telefono").toString());
            }
            usuarioRepository.save(rep);
        } else if (nuevoPin != null) {
            String cleanPlaca = c.getPlaca().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
            String emailDefault = "camion." + cleanPlaca + "@distribucion.prisma.com";
            Usuario rep = usuarioRepository.findByEmail(emailDefault).orElseGet(() -> {
                Usuario nuevo = new Usuario();
                nuevo.setEmail(emailDefault);
                nuevo.setRol(Rol.ROLE_REPARTIDOR);
                nuevo.setTelefono(c.getProveedor() != null && c.getProveedor().getContactoTelefono() != null ? c.getProveedor().getContactoTelefono() : "3000000000");
                nuevo.setProveedor(c.getProveedor());
                nuevo.setEstado("ACTIVO");
                return nuevo;
            });
            rep.setPasswordHash(passwordEncoder.encode(nuevoPin));
            rep.setNombreCompleto("Camión " + c.getPlaca());
            Usuario repGuardado = usuarioRepository.save(rep);
            c.setRepartidor(repGuardado);
        }

        Camion guardado = camionRepository.save(c);

        Map<String, Object> dto = new HashMap<>();
        dto.put("id", guardado.getId());
        dto.put("placa", guardado.getPlaca());
        dto.put("marcaModelo", guardado.getMarcaModelo());
        dto.put("capacidadPesoKg", guardado.getCapacidadPesoKg());
        dto.put("capacidadCanastas", guardado.getCapacidadCanastas());
        dto.put("estado", guardado.getEstado());
        if (guardado.getRepartidor() != null) {
            dto.put("repartidorId", guardado.getRepartidor().getId());
            dto.put("repartidorNombre", guardado.getRepartidor().getNombreCompleto());
            dto.put("repartidorEmail", guardado.getRepartidor().getEmail());
            dto.put("usuarioApp", guardado.getRepartidor().getEmail());
        }

        return ResponseEntity.ok(ApiResponse.exito("Camión actualizado exitosamente", dto));
    }

    @DeleteMapping("/camiones/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Dar de baja un camión de la flota")
    public ResponseEntity<ApiResponse<String>> eliminarCamion(@PathVariable Long id) {
        Camion c = camionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Camión no encontrado con ID: " + id));
        c.setEstado("MANTENIMIENTO");
        camionRepository.save(c);
        return ResponseEntity.ok(ApiResponse.exito("Camión retirado del servicio activo", "OK"));
    }

    @GetMapping("/liquidaciones")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Consultar liquidaciones quincenales y comisiones del 2.5%")
    public ResponseEntity<ApiResponse<List<LiquidacionQuincenal>>> getLiquidaciones(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<LiquidacionQuincenal> liquidaciones = liquidacionRepository.findByProveedorIdOrderByGeneradoEnDesc(provId);
        return ResponseEntity.ok(ApiResponse.exito("Liquidaciones quincenales", liquidaciones));
    }

    @PostMapping("/pedidos/{pedidoId}/reportar-problema")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Reportar imposibilidad de entrega o cumplimiento (Tienda cerrada, dinero incompleto, sin stock)")
    public ResponseEntity<ApiResponse<Pedido>> reportarProblemaPedido(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> body) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        String motivo = body.getOrDefault("motivo", "PROBLEMA_ENTREGA");
        String detalle = body.getOrDefault("detalle", "No fue posible realizar la entrega en la tienda.");
        String accion = body.getOrDefault("accion", "CANCELAR"); // CANCELAR, REPROGRAMAR, RECHAZAR

        if ("REPROGRAMAR".equalsIgnoreCase(accion)) {
            pedido.setEstado(EstadoPedido.ACEPTADO);
            pedido.setFechaEstimadaEntrega(java.time.LocalDate.now().plusDays(1));
            pedido.setNotasEntrega("REPROGRAMADO POR PROVEEDOR: " + motivo + " - " + detalle);
        } else if ("RECHAZAR".equalsIgnoreCase(accion)) {
            pedido.setEstado(EstadoPedido.RECHAZADO);
            pedido.setNotasEntrega("RECHAZADO POR PROVEEDOR: " + motivo + " - " + detalle);
        } else {
            pedido.setEstado(EstadoPedido.CANCELADO);
            pedido.setNotasEntrega("CANCELADO POR PROVEEDOR: " + motivo + " - " + detalle);
        }
        pedido.setActualizadoEn(OffsetDateTime.now());

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Reporte procesado exitosamente: pedido " + pedido.getEstado(), guardado));
    }

    @PostMapping("/pedidos/{pedidoId}/reprogramar")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Reprogramar fecha de entrega por parte del proveedor")
    public ResponseEntity<ApiResponse<Pedido>> reprogramarPedido(
            @PathVariable Long pedidoId,
            @RequestBody Map<String, String> body) {

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + pedidoId));

        String nuevaFechaStr = body.get("nuevaFecha");
        if (nuevaFechaStr != null) {
            pedido.setFechaEstimadaEntrega(java.time.LocalDate.parse(nuevaFechaStr));
        } else {
            pedido.setFechaEstimadaEntrega(java.time.LocalDate.now().plusDays(1));
        }

        String motivo = body.getOrDefault("motivo", "Reprogramado para la siguiente ruta disponible");
        pedido.setNotasEntrega("REPROGRAMADO: " + motivo);
        pedido.setEstado(EstadoPedido.ACEPTADO);
        pedido.setActualizadoEn(OffsetDateTime.now());

        Pedido guardado = pedidoRepository.save(pedido);
        return ResponseEntity.ok(ApiResponse.exito("Pedido reprogramado exitosamente", guardado));
    }

    @GetMapping("/tiendas")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Listar tiendas atendidas por la distribuidora con resumen de compras")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTiendasClientes(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        List<Tienda> todasTiendas = tiendaRepository.findAll();
        List<Pedido> pedidosProveedor = pedidoRepository.findAll().stream()
                .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(provId))
                .toList();

        List<Map<String, Object>> resultado = todasTiendas.stream().map(t -> {
            List<Pedido> pedidosTienda = pedidosProveedor.stream()
                    .filter(p -> p.getTienda() != null && p.getTienda().getId().equals(t.getId()))
                    .toList();

            BigDecimal totalComprado = pedidosTienda.stream()
                    .filter(p -> p.getEstado() != EstadoPedido.CANCELADO && p.getEstado() != EstadoPedido.RECHAZADO)
                    .map(Pedido::getTotalAPagar)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Pedido ultimoPedido = pedidosTienda.stream()
                    .max(Comparator.comparing(Pedido::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", t.getId());
            dto.put("nombreTienda", t.getNombreTienda());
            dto.put("nitOCedula", t.getNitOCedula());
            dto.put("direccion", t.getDireccion());
            dto.put("barrio", t.getBarrio());
            dto.put("ciudad", t.getCiudad());
            dto.put("telefono", t.getTelefonoTienda());
            dto.put("nombreTendero", t.getTendero() != null ? t.getTendero().getNombreCompleto() : "Tendero Asignado");
            dto.put("diaPreferidoEntrega", t.getDiaPreferidoEntrega() != null ? t.getDiaPreferidoEntrega() : "Lunes y Jueves");
            dto.put("estado", t.getEstado());
            dto.put("totalComprado", totalComprado);
            dto.put("totalPedidos", pedidosTienda.size());
            dto.put("ultimoPedidoFecha", ultimoPedido != null ? (ultimoPedido.getFechaCreacion() != null ? ultimoPedido.getFechaCreacion().toString() : ultimoPedido.getFechaEstimadaEntrega().toString()) : null);
            dto.put("ultimoPedidoEstado", ultimoPedido != null ? ultimoPedido.getEstado().name() : null);

            return dto;
        }).sorted((a, b) -> ((BigDecimal) b.get("totalComprado")).compareTo((BigDecimal) a.get("totalComprado"))).toList();

        return ResponseEntity.ok(ApiResponse.exito("Tiendas clientes", resultado));
    }

    @PostMapping("/tiendas")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Registrar nueva tienda cliente atendida por el distribuidor")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crearTiendaCliente(
            @RequestBody Map<String, String> body) {

        String nit = body.getOrDefault("nitOCedula", "CC-" + System.currentTimeMillis());
        if (tiendaRepository.findByNitOCedula(nit).isPresent()) {
            throw new RuntimeException("Ya existe una tienda con el NIT o Cédula: " + nit);
        }

        String nombreTienda = body.getOrDefault("nombreTienda", "Tienda Sin Nombre");
        String nombreTendero = body.getOrDefault("nombreTendero", "Tendero " + nombreTienda);
        String telefono = body.getOrDefault("telefono", "3000000000");
        String direccion = body.getOrDefault("direccion", "Dirección no especificada");
        String barrio = body.getOrDefault("barrio", "Centro");
        String ciudad = body.getOrDefault("ciudad", "Bogotá");
        String diaPreferido = body.getOrDefault("diaPreferidoEntrega", "Lunes y Jueves");

        String emailTendero = "tendero." + nit.replaceAll("[^a-zA-Z0-9]", "") + "@prisma.com";
        Usuario tendero = usuarioRepository.findByEmail(emailTendero).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setEmail(emailTendero);
            u.setPasswordHash(passwordEncoder.encode("tendero123"));
            u.setNombreCompleto(nombreTendero);
            u.setTelefono(telefono);
            u.setRol(Rol.ROLE_TENDERO);
            u.setEstado("ACTIVO");
            return usuarioRepository.save(u);
        });

        Tienda nueva = new Tienda();
        nueva.setNombreTienda(nombreTienda);
        nueva.setNitOCedula(nit);
        nueva.setDireccion(direccion);
        nueva.setBarrio(barrio);
        nueva.setCiudad(ciudad);
        nueva.setTelefonoTienda(telefono);
        nueva.setTendero(tendero);
        nueva.setDiaPreferidoEntrega(diaPreferido);
        nueva.setLatitud(new BigDecimal("4.6097100"));
        nueva.setLongitud(new BigDecimal("-74.0817500"));
        nueva.setEstado("ACTIVA");

        Tienda guardada = tiendaRepository.save(nueva);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", guardada.getId());
        resp.put("nombreTienda", guardada.getNombreTienda());
        resp.put("direccion", guardada.getDireccion());
        resp.put("barrio", guardada.getBarrio());
        resp.put("ciudad", guardada.getCiudad());
        resp.put("telefono", guardada.getTelefonoTienda());
        resp.put("diaPreferidoEntrega", guardada.getDiaPreferidoEntrega());
        resp.put("estado", guardada.getEstado());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.exito("Tienda cliente registrada exitosamente", resp));
    }

    @PutMapping("/tiendas/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Actualizar datos comerciales o de contacto de una tienda cliente")
    public ResponseEntity<ApiResponse<Map<String, Object>>> actualizarTiendaCliente(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Tienda t = tiendaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada con ID: " + id));

        if (body.containsKey("nombreTienda")) t.setNombreTienda(body.get("nombreTienda"));
        if (body.containsKey("direccion")) t.setDireccion(body.get("direccion"));
        if (body.containsKey("barrio")) t.setBarrio(body.get("barrio"));
        if (body.containsKey("ciudad")) t.setCiudad(body.get("ciudad"));
        if (body.containsKey("telefono")) t.setTelefonoTienda(body.get("telefono"));
        if (body.containsKey("diaPreferidoEntrega")) t.setDiaPreferidoEntrega(body.get("diaPreferidoEntrega"));
        if (body.containsKey("diaEntrega")) t.setDiaPreferidoEntrega(body.get("diaEntrega"));
        if (body.containsKey("nitOCedula")) t.setNitOCedula(body.get("nitOCedula"));
        if (body.containsKey("estado")) t.setEstado(body.get("estado"));

        if (body.containsKey("nombreTendero") && t.getTendero() != null) {
            Usuario tendero = t.getTendero();
            tendero.setNombreCompleto(body.get("nombreTendero"));
            if (body.containsKey("telefono")) tendero.setTelefono(body.get("telefono"));
            usuarioRepository.save(tendero);
        }

        Tienda guardada = tiendaRepository.save(t);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", guardada.getId());
        resp.put("nombreTienda", guardada.getNombreTienda());
        resp.put("direccion", guardada.getDireccion());
        resp.put("barrio", guardada.getBarrio());
        resp.put("ciudad", guardada.getCiudad());
        resp.put("telefono", guardada.getTelefonoTienda());
        resp.put("diaPreferidoEntrega", guardada.getDiaPreferidoEntrega());
        resp.put("estado", guardada.getEstado());
        return ResponseEntity.ok(ApiResponse.exito("Tienda actualizada exitosamente", resp));
    }

    @DeleteMapping("/tiendas/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Dar de baja o inactivar una tienda cliente")
    public ResponseEntity<ApiResponse<String>> eliminarTiendaCliente(@PathVariable Long id) {
        Tienda t = tiendaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada con ID: " + id));
        t.setEstado("INACTIVA");
        tiendaRepository.save(t);
        return ResponseEntity.ok(ApiResponse.exito("Tienda dada de baja del portafolio", "OK"));
    }

    @GetMapping("/tiendas/{tiendaId}/analisis-ventas")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Consultar qué vende y compra una tienda (ranking de productos e historial)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalisisVentasTienda(
            @PathVariable Long tiendaId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Long provId = resolveProveedorId(currentUser);
        Tienda tienda = tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada: " + tiendaId));

        List<Pedido> pedidosTienda = pedidoRepository.findAll().stream()
                .filter(p -> p.getTienda() != null && p.getTienda().getId().equals(tiendaId))
                .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(provId))
                .toList();

        List<ItemPedido> items = new ArrayList<>();
        for (Pedido ped : pedidosTienda) {
            items.addAll(itemPedidoRepository.findByPedidoId(ped.getId()));
        }

        Map<String, Map<String, Object>> consolidadoProductos = new HashMap<>();
        int totalUnidadesGlobal = 0;
        BigDecimal totalDineroGlobal = BigDecimal.ZERO;

        for (ItemPedido it : items) {
            String key = it.getNombreProductoHistorico();
            consolidadoProductos.putIfAbsent(key, new HashMap<>());
            Map<String, Object> data = consolidadoProductos.get(key);

            int prevCant = (int) data.getOrDefault("unidades", 0);
            BigDecimal prevSub = (BigDecimal) data.getOrDefault("totalDinero", BigDecimal.ZERO);

            int nuevaCant = prevCant + it.getCantidad();
            BigDecimal nuevoSub = prevSub.add(it.getSubtotalLinea() != null ? it.getSubtotalLinea() : BigDecimal.ZERO);

            data.put("nombreProducto", key);
            data.put("presentacion", it.getPresentacionHistorica());
            data.put("unidades", nuevaCant);
            data.put("totalDinero", nuevoSub);
            data.put("precioUnitario", it.getPrecioUnitarioHistorico());
            if (it.getProducto() != null) {
                data.put("imagenUrl", it.getProducto().getImagenUrl());
                data.put("sku", it.getProducto().getSku());
            }

            totalUnidadesGlobal += it.getCantidad();
            totalDineroGlobal = totalDineroGlobal.add(it.getSubtotalLinea() != null ? it.getSubtotalLinea() : BigDecimal.ZERO);
        }

        final int finalUnidades = totalUnidadesGlobal;
        List<Map<String, Object>> rankingProductos = consolidadoProductos.values().stream()
                .peek(d -> {
                    int cant = (int) d.get("unidades");
                    int pct = finalUnidades > 0 ? (int) Math.round(((double) cant / finalUnidades) * 100) : 0;
                    d.put("porcentajeVentas", pct);
                })
                .sorted((a, b) -> Integer.compare((int) b.get("unidades"), (int) a.get("unidades")))
                .toList();

        List<Map<String, Object>> historialPedidos = pedidosTienda.stream().map(p -> {
            Map<String, Object> pedDto = new HashMap<>();
            pedDto.put("id", p.getId());
            pedDto.put("numeroPedido", p.getNumeroPedido());
            pedDto.put("fecha", p.getFechaCreacion() != null ? p.getFechaCreacion().toString() : p.getFechaEstimadaEntrega().toString());
            pedDto.put("total", p.getTotalAPagar());
            pedDto.put("estado", p.getEstado().name());
            pedDto.put("notas", p.getNotasEntrega());
            return pedDto;
        }).toList();

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tiendaId", tienda.getId());
        respuesta.put("nombreTienda", tienda.getNombreTienda());
        respuesta.put("nitOCedula", tienda.getNitOCedula());
        respuesta.put("direccion", tienda.getDireccion());
        respuesta.put("barrio", tienda.getBarrio());
        respuesta.put("telefono", tienda.getTelefonoTienda());
        respuesta.put("nombreTendero", tienda.getTendero() != null ? tienda.getTendero().getNombreCompleto() : "Tendero");
        respuesta.put("diaPreferidoEntrega", tienda.getDiaPreferidoEntrega());
        respuesta.put("totalGastadoAcumulado", totalDineroGlobal);
        respuesta.put("gananciaTotalTienda", totalDineroGlobal);
        respuesta.put("totalUnidadesCompradas", totalUnidadesGlobal);
        respuesta.put("totalPedidosRealizados", pedidosTienda.size());
        respuesta.put("rankingProductos", rankingProductos);
        respuesta.put("productosSuscritos", rankingProductos);
        respuesta.put("historialPedidos", historialPedidos);

        return ResponseEntity.ok(ApiResponse.exito("Análisis de ventas por tienda", respuesta));
    }

    @GetMapping("/estadisticas-economicas")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Consultar estadísticas económicas y ganancias netas del productor")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEstadisticasEconomicas(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);

        List<Pedido> pedidosProveedor = pedidoRepository.findAll().stream()
                .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(provId))
                .toList();

        List<Pedido> pedidosEntregados = pedidosProveedor.stream()
                .filter(p -> p.getEstado() == EstadoPedido.ENTREGADO)
                .toList();

        BigDecimal ventasTotales = pedidosEntregados.stream()
                .map(Pedido::getTotalAPagar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Si aún no hay pedidos entregados en demo, sumamos los pedidos en curso o confirmados para proyección
        BigDecimal ventasProyectadas = pedidosProveedor.stream()
                .map(Pedido::getTotalAPagar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ingresosProductor = ventasTotales.compareTo(BigDecimal.ZERO) > 0 ? ventasTotales : ventasProyectadas;

        // Tiendas más rentables
        Map<String, BigDecimal> ventasPorTienda = new HashMap<>();
        Map<String, String> nombresTienda = new HashMap<>();
        for (Pedido p : pedidosProveedor) {
            if (p.getTienda() != null) {
                String tId = p.getTienda().getId().toString();
                nombresTienda.put(tId, p.getTienda().getNombreTienda());
                BigDecimal sub = ventasPorTienda.getOrDefault(tId, BigDecimal.ZERO);
                ventasPorTienda.put(tId, sub.add(p.getTotalAPagar() != null ? p.getTotalAPagar() : BigDecimal.ZERO));
            }
        }

        List<Map<String, Object>> topTiendas = ventasPorTienda.entrySet().stream()
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("tiendaId", Long.parseLong(e.getKey()));
                    map.put("nombreTienda", nombresTienda.get(e.getKey()));
                    map.put("totalVendido", e.getValue());
                    return map;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalVendido")).compareTo((BigDecimal) a.get("totalVendido")))
                .limit(5)
                .toList();

        // Promedio por pedido
        BigDecimal ticketPromedio = pedidosProveedor.isEmpty() ? BigDecimal.ZERO 
                : ingresosProductor.divide(BigDecimal.valueOf(pedidosProveedor.size()), 2, java.math.RoundingMode.HALF_UP);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("ventasTotales", ingresosProductor);
        resultado.put("gananciaNetaProductor", ingresosProductor);
        resultado.put("totalPedidos", pedidosProveedor.size());
        resultado.put("pedidosEntregados", pedidosEntregados.size());
        resultado.put("ticketPromedio", ticketPromedio);
        resultado.put("tiendasAtendidas", ventasPorTienda.size());
        resultado.put("topTiendas", topTiendas);

        return ResponseEntity.ok(ApiResponse.exito("Estadísticas económicas del productor", resultado));
    }

    @PostMapping("/rutas/{rutaId}/reportar-varado")
    @PreAuthorize("hasAnyAuthority('ROLE_REPARTIDOR', 'ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Reportar percance o varamiento de un camión para auxilio inmediato")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportarVarado(
            @PathVariable Long rutaId,
            @RequestBody(required = false) Map<String, String> body) {
        Ruta r = rutaRepository.findById(rutaId)
                .orElseThrow(() -> new RuntimeException("Ruta no encontrada con ID: " + rutaId));

        String accion = body != null && body.containsKey("accion") ? body.get("accion") : "VARADO";
        String motivo = body != null && body.containsKey("motivo") ? body.get("motivo") : "Camión varado por falla mecánica o percance en la vía";

        if ("RESOLVER".equalsIgnoreCase(accion) || "EN_RUTA".equalsIgnoreCase(accion) || "REANUDAR".equalsIgnoreCase(accion)) {
            r.setEstado("EN_EJECUCION");
            if (r.getCamion() != null) {
                r.getCamion().setEstado("ACTIVO");
                camionRepository.save(r.getCamion());
            }
        } else {
            r.setEstado("VARADO_EN_PERCANCE");
            if (r.getCamion() != null) {
                r.getCamion().setEstado("MANTENIMIENTO");
                camionRepository.save(r.getCamion());
            }
        }

        Ruta guardada = rutaRepository.save(r);

        Map<String, Object> resp = new HashMap<>();
        resp.put("rutaId", guardada.getId());
        resp.put("codigoRuta", guardada.getCodigoRuta());
        resp.put("nuevoEstado", guardada.getEstado());
        resp.put("motivo", motivo);
        resp.put("camionPlaca", guardada.getCamion() != null ? guardada.getCamion().getPlaca() : "N/A");

        return ResponseEntity.ok(ApiResponse.exito("Estado de auxilio en ruta actualizado", resp));
    }

    @GetMapping("/pqrs")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Operation(summary = "Consultar PQRS, quejas y reclamos de tiendas clientes hacia el productor")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPqrsProveedor(@AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);

        List<Disputa> disputas = disputaRepository.findAll().stream()
                .filter(d -> (d.getPedido() != null && d.getPedido().getProveedor() != null && d.getPedido().getProveedor().getId().equals(provId))
                          || (d.getProveedor() != null && d.getProveedor().getId().equals(provId)))
                .sorted((a, b) -> b.getFechaReporte().compareTo(a.getFechaReporte()))
                .toList();

        List<Map<String, Object>> res = disputas.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("ticketNumero", d.getTicketNumero());
            map.put("tipoIncidente", d.getTipoIncidente() != null ? d.getTipoIncidente().name() : "OTRO_INCIDENTE");
            map.put("descripcion", d.getDescripcion());
            map.put("estado", d.getEstado());
            map.put("resolucionJustificacion", d.getResolucionJustificacion());
            map.put("fechaReporte", d.getFechaReporte() != null ? d.getFechaReporte().toString() : null);
            map.put("fechaResolucion", d.getFechaResolucion() != null ? d.getFechaResolucion().toString() : null);

            boolean esHaciaPrisma = d.getProveedor() != null && d.getReportadoPor() != null 
                    && (d.getReportadoPor().getRol() == Rol.ROLE_PROVEEDOR || d.getReportadoPor().getRol() == Rol.ROLE_PROVEEDOR_ADMIN);
            map.put("origen", esHaciaPrisma ? "PROVEEDOR_A_PRISMA" : "TENDERO_A_PROVEEDOR");

            Pedido p = d.getPedido();
            if (p != null) {
                map.put("pedidoId", p.getId());
                map.put("numeroPedido", p.getNumeroPedido());
                map.put("totalPedido", p.getTotalAPagar());
                map.put("estadoPedido", p.getEstado() != null ? p.getEstado().name() : "EN_RUTA");
                if (p.getTienda() != null) {
                    map.put("tiendaId", p.getTienda().getId());
                    map.put("nombreTienda", p.getTienda().getNombreTienda());
                    map.put("direccionTienda", p.getTienda().getDireccion());
                    map.put("barrioTienda", p.getTienda().getBarrio());
                    map.put("telefonoTienda", p.getTienda().getTelefonoTienda());
                    map.put("nombreTendero", p.getTienda().getTendero() != null ? p.getTienda().getTendero().getNombreCompleto() : "Tendero");
                }
            }

            if (d.getReportadoPor() != null) {
                map.put("reportadoPorNombre", d.getReportadoPor().getNombreCompleto());
                map.put("reportadoPorTelefono", d.getReportadoPor().getTelefono());
                map.put("reportadoPorRol", d.getReportadoPor().getRol() != null ? d.getReportadoPor().getRol().name() : "");
            }

            return map;
        }).toList();

        return ResponseEntity.ok(ApiResponse.exito("PQRS y reclamos del productor", res));
    }

    @PostMapping("/pqrs/radicar-a-prisma")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Radicar un reclamo o disputa del productor directamente al Administrador de Prisma")
    public ResponseEntity<ApiResponse<Map<String, Object>>> radicarDisputaPrisma(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long provId = resolveProveedorId(currentUser);
        Proveedor prov = proveedorRepository.findById(provId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
        Usuario user = usuarioRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String tipoStr = body.getOrDefault("tipoIncidente", "DISPUTA_COMISION_PRISMA").toString();
        TipoIncidente tipo;
        try {
            tipo = TipoIncidente.valueOf(tipoStr);
        } catch (Exception e) {
            tipo = TipoIncidente.OTRO_INCIDENTE;
        }

        String descripcion = body.getOrDefault("descripcion", "Reclamo administrativo del proveedor").toString();
        
        Pedido pedidoRelacionado = null;
        if (body.containsKey("pedidoId") && body.get("pedidoId") != null && !body.get("pedidoId").toString().isEmpty()) {
            try {
                Long pedId = Long.parseLong(body.get("pedidoId").toString());
                pedidoRelacionado = pedidoRepository.findById(pedId).orElse(null);
            } catch (Exception ignored) {}
        }

        Disputa d = new Disputa();
        d.setTicketNumero("PQRS-PRISMA-" + (System.currentTimeMillis() % 100000));
        d.setProveedor(prov);
        d.setReportadoPor(user);
        d.setPedido(pedidoRelacionado);
        d.setTipoIncidente(tipo);
        d.setDescripcion(descripcion);
        d.setEstado("ABIERTO");
        d.setFechaReporte(OffsetDateTime.now());

        Disputa guardada = disputaRepository.save(d);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", guardada.getId());
        resp.put("ticketNumero", guardada.getTicketNumero());
        resp.put("estado", guardada.getEstado());
        resp.put("tipoIncidente", guardada.getTipoIncidente().name());
        resp.put("descripcion", guardada.getDescripcion());

        return ResponseEntity.ok(ApiResponse.exito("Reclamo radicado exitosamente ante el Administrador Prisma", resp));
    }

    @PostMapping("/pqrs/{id}/responder")
    @PreAuthorize("hasAnyAuthority('ROLE_PROVEEDOR_ADMIN', 'ROLE_PROVEEDOR', 'ROLE_ADMIN', 'ROLE_PLATFORM_ADMIN')")
    @Transactional
    @Operation(summary = "Responder o dar solución a una PQRS / reclamo de tendero")
    public ResponseEntity<ApiResponse<Map<String, Object>>> responderPqrs(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Disputa d = disputaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket de PQRS no encontrado con ID: " + id));

        String resolucion = body != null && body.containsKey("resolucion") ? body.get("resolucion") : "Reclamo gestionado por el distribuidor";
        String nuevoEstado = body != null && body.containsKey("nuevoEstado") ? body.get("nuevoEstado") : "RESUELTO";

        d.setResolucionJustificacion(resolucion);
        d.setEstado(nuevoEstado);
        if ("RESUELTO".equalsIgnoreCase(nuevoEstado)) {
            d.setFechaResolucion(java.time.OffsetDateTime.now());
        }

        Disputa guardada = disputaRepository.save(d);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", guardada.getId());
        resp.put("ticketNumero", guardada.getTicketNumero());
        resp.put("estado", guardada.getEstado());
        resp.put("resolucionJustificacion", guardada.getResolucionJustificacion());

        return ResponseEntity.ok(ApiResponse.exito("Respuesta a PQRS registrada exitosamente", resp));
    }
}
