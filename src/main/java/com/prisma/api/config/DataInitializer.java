package com.prisma.api.config;

import com.prisma.api.entity.*;
import com.prisma.api.enums.EstadoPedido;
import com.prisma.api.enums.MedioPago;
import com.prisma.api.enums.Rol;
import com.prisma.api.enums.TipoIncidente;
import com.prisma.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final ProveedorRepository proveedorRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final CamionRepository camionRepository;
    private final LiquidacionQuincenalRepository liquidacionRepository;
    private final ConfiguracionSistemaRepository configRepository;
    private final TiendaRepository tiendaRepository;
    private final PedidoRepository pedidoRepository;
    private final DisputaRepository disputaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final RutaRepository rutaRepository;
    private final ItemPedidoRepository itemPedidoRepository;

    public DataInitializer(
            ProveedorRepository proveedorRepository,
            CategoriaRepository categoriaRepository,
            ProductoRepository productoRepository,
            CamionRepository camionRepository,
            LiquidacionQuincenalRepository liquidacionRepository,
            ConfiguracionSistemaRepository configRepository,
            TiendaRepository tiendaRepository,
            PedidoRepository pedidoRepository,
            DisputaRepository disputaRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            RutaRepository rutaRepository,
            ItemPedidoRepository itemPedidoRepository) {
        this.proveedorRepository = proveedorRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
        this.camionRepository = camionRepository;
        this.liquidacionRepository = liquidacionRepository;
        this.configRepository = configRepository;
        this.tiendaRepository = tiendaRepository;
        this.pedidoRepository = pedidoRepository;
        this.disputaRepository = disputaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.rutaRepository = rutaRepository;
        this.itemPedidoRepository = itemPedidoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (proveedorRepository.count() > 0) {
            seedNuevosProductos();
            return;
        }

        logger.info("Cargando datos semilla iniciales para PRISMA Backend...");

        // 1. Proveedores
        Proveedor femsa = new Proveedor();
        femsa.setNit("860.000.123-1");
        femsa.setRazonSocial("Industria Nacional de Gaseosas S.A.S.");
        femsa.setNombreComercial("Coca-Cola FEMSA Colombia");
        femsa.setContactoNombre("Carlos Morales");
        femsa.setContactoEmail("logistica@femsa.com.co");
        femsa.setContactoTelefono("3109876543");
        femsa.setDireccionSede("Calle 100 # 19-61, Bogotá");
        femsa.setCiudad("Bogotá");
        femsa.setZonaCobertura("Bogotá: Kennedy, Bosa, Suba, Engativá y Fontibón");
        femsa.setComisionPorcentaje(new BigDecimal("2.50"));
        femsa.setEstado("ACTIVO");
        femsa = proveedorRepository.save(femsa);

        Proveedor postobon = new Proveedor();
        postobon.setNit("890.903.939-5");
        postobon.setRazonSocial("Postobón S.A.");
        postobon.setNombreComercial("Postobón Central");
        postobon.setContactoNombre("Laura Gómez");
        postobon.setContactoEmail("ventas@postobon.com.co");
        postobon.setContactoTelefono("3115554321");
        postobon.setDireccionSede("Av. Boyacá # 68-12, Bogotá");
        postobon.setCiudad("Bogotá");
        postobon.setZonaCobertura("Bogotá: Chapinero, Usaquén, Teusaquillo y Barrios Unidos");
        postobon.setComisionPorcentaje(new BigDecimal("2.50"));
        postobon.setEstado("ACTIVO");
        postobon = proveedorRepository.save(postobon);

        // 2. Camiones
        Camion c1 = new Camion();
        c1.setProveedor(femsa);
        c1.setPlaca("WDN-412");
        c1.setMarcaModelo("Chevrolet NPR 75 Turbo");
        c1.setCapacidadPesoKg(new BigDecimal("4500.00"));
        c1.setCapacidadCanastas(180);
        c1.setEstado("DISPONIBLE");
        camionRepository.save(c1);

        Camion c2 = new Camion();
        c2.setProveedor(femsa);
        c2.setPlaca("TKR-881");
        c2.setMarcaModelo("Hino Dutro City");
        c2.setCapacidadPesoKg(new BigDecimal("3500.00"));
        c2.setCapacidadCanastas(140);
        c2.setEstado("EN_RUTA");
        camionRepository.save(c2);

        // 3. Categorías
        Categoria catGaseosas = new Categoria();
        catGaseosas.setNombre("Gaseosas y Bebidas");
        catGaseosas.setDescripcion("Bebidas carbonatadas y refrescos");
        catGaseosas = categoriaRepository.save(catGaseosas);

        // 4. Productos con Imágenes y Promociones B2B
        Producto p1 = new Producto();
        p1.setProveedor(femsa);
        p1.setCategoria(catGaseosas);
        p1.setSku("KO-PET-400");
        p1.setNombre("Coca-Cola Original 400ml");
        p1.setDescripcion("Bebida gaseosa personal botella PET 400ml de alta rotación.");
        p1.setPresentacion("Paquete x 12 Unidades");
        p1.setPrecioUnitario(new BigDecimal("28500.00"));
        p1.setPrecioSugeridoPublico(new BigDecimal("36000.00"));
        p1.setStockDisponible(9999);
        p1.setImagenUrl("https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=500&auto=format&fit=crop&q=80");
        p1.setEstado("PROMOCION");
        p1 = productoRepository.save(p1);

        Producto p2 = new Producto();
        p2.setProveedor(femsa);
        p2.setCategoria(catGaseosas);
        p2.setSku("KO-LATA-330");
        p2.setNombre("Coca-Cola Sin Azúcar Lata 330ml");
        p2.setDescripcion("Refresco zero azúcar en lata 330ml para refrigerador.");
        p2.setPresentacion("Pack x 24 Latas");
        p2.setPrecioUnitario(new BigDecimal("54000.00"));
        p2.setPrecioSugeridoPublico(new BigDecimal("72000.00"));
        p2.setStockDisponible(9999);
        p2.setImagenUrl("https://images.unsplash.com/photo-1554866585-cd94860890b7?w=500&auto=format&fit=crop&q=80");
        p2.setEstado("ACTIVO");
        p2 = productoRepository.save(p2);

        Producto p3 = new Producto();
        p3.setProveedor(femsa);
        p3.setCategoria(catGaseosas);
        p3.setSku("KO-PET-1500");
        p3.setNombre("Coca-Cola 1.5L Retornable Vidrio/PET");
        p3.setDescripcion("Nuevo lanzamiento familiar de máxima rentabilidad para el tendero.");
        p3.setPresentacion("Caja x 8 Botellas");
        p3.setPrecioUnitario(new BigDecimal("32000.00"));
        p3.setPrecioSugeridoPublico(new BigDecimal("42000.00"));
        p3.setStockDisponible(9999);
        p3.setImagenUrl("https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?w=500&auto=format&fit=crop&q=80");
        p3.setEstado("NUEVO_LANZAMIENTO");
        p3 = productoRepository.save(p3);

        Producto p4 = new Producto();
        p4.setProveedor(femsa);
        p4.setCategoria(catGaseosas);
        p4.setSku("VALLE-FRUT-15");
        p4.setNombre("Jugo Del Valle Frut Citrus 1.5L");
        p4.setDescripcion("Bebida con jugo de fruta fortificada con vitamina C.");
        p4.setPresentacion("Pack x 6 Unidades");
        p4.setPrecioUnitario(new BigDecimal("21000.00"));
        p4.setPrecioSugeridoPublico(new BigDecimal("28000.00"));
        p4.setStockDisponible(9999);
        p4.setImagenUrl("https://images.unsplash.com/photo-1613478223719-2ab802602423?w=500&auto=format&fit=crop&q=80");
        p4.setEstado("PROMOCION");
        p4 = productoRepository.save(p4);

        Producto p5 = new Producto();
        p5.setProveedor(femsa);
        p5.setCategoria(catGaseosas);
        p5.setSku("AGUA-BRISA-600");
        p5.setNombre("Agua Manantial Sin Gas 600ml");
        p5.setDescripcion("Agua pura de manantial para consumo diario.");
        p5.setPresentacion("Paquete x 24 Botellas");
        p5.setPrecioUnitario(new BigDecimal("26000.00"));
        p5.setPrecioSugeridoPublico(new BigDecimal("36000.00"));
        p5.setStockDisponible(9999);
        p5.setImagenUrl("https://images.unsplash.com/photo-1548839140-29a749e1bc4e?w=500&auto=format&fit=crop&q=80");
        p5.setEstado("ACTIVO");
        p5 = productoRepository.save(p5);

        // 5. Usuarios Semilla
        Usuario adminUser = usuarioRepository.save(new Usuario("admin@prisma.com.co", passwordEncoder.encode("admin123"), "Administrador Prisma", "3001234567", Rol.ROLE_ADMIN, null));
        Usuario tenderoUser = usuarioRepository.save(new Usuario("dona.marta@labendicion.com", passwordEncoder.encode("admin123"), "Doña Marta Rodríguez", "3205551234", Rol.ROLE_TENDERO, null));
        Usuario repartidorUser = usuarioRepository.save(new Usuario("conductor.juan@femsa.com.co", passwordEncoder.encode("admin123"), "Juan Camilo Reyes", "3154448899", Rol.ROLE_REPARTIDOR, femsa));
        usuarioRepository.save(new Usuario("logistica@femsa.com.co", passwordEncoder.encode("femsa123"), "Carlos Morales", "3109876543", Rol.ROLE_PROVEEDOR, femsa));
        usuarioRepository.save(new Usuario("ventas@postobon.com.co", passwordEncoder.encode("postobon123"), "Laura Gómez", "3115554321", Rol.ROLE_PROVEEDOR, postobon));

        // 6. Tiendas de Barrio
        Tienda t1 = new Tienda();
        t1.setTendero(tenderoUser);
        t1.setNombreTienda("Tienda y Abarrotes La Bendición");
        t1.setNitOCedula("52.123.456");
        t1.setTelefonoTienda("3205551234");
        t1.setDireccion("Calle 45 Sur # 78H-12, Kennedy");
        t1.setBarrio("Kennedy Central");
        t1.setCiudad("Bogotá");
        t1.setLatitud(new BigDecimal("4.6184200"));
        t1.setLongitud(new BigDecimal("-74.1568200"));
        t1 = tiendaRepository.save(t1);

        Tienda t2 = new Tienda();
        t2.setTendero(tenderoUser);
        t2.setNombreTienda("Minimarket El Triunfo");
        t2.setNitOCedula("19.876.543");
        t2.setTelefonoTienda("3112223344");
        t2.setDireccion("Cra 80 # 42A-15 Sur, Kennedy");
        t2.setBarrio("Tintal");
        t2.setCiudad("Bogotá");
        t2.setLatitud(new BigDecimal("4.6210000"));
        t2.setLongitud(new BigDecimal("-74.1590000"));
        t2 = tiendaRepository.save(t2);

        Tienda t3 = new Tienda();
        t3.setTendero(tenderoUser);
        t3.setNombreTienda("Cigarrería Los Paisas");
        t3.setNitOCedula("79.432.109");
        t3.setTelefonoTienda("3156667788");
        t3.setDireccion("Av. Primero de Mayo # 72-30");
        t3.setBarrio("Timiza");
        t3.setCiudad("Bogotá");
        t3.setLatitud(new BigDecimal("4.6140000"));
        t3.setLongitud(new BigDecimal("-74.1520000"));
        t3 = tiendaRepository.save(t3);

        // 7. Ruta de Despacho y Paradas
        Ruta ruta1 = new Ruta();
        ruta1.setProveedor(femsa);
        ruta1.setCamion(c2);
        ruta1.setRepartidor(repartidorUser);
        ruta1.setCodigoRuta("RUTA-KENNEDY-01");
        ruta1.setFechaProgramada(LocalDate.now());
        ruta1.setZonaSector("Kennedy Central - Tintal - Timiza");
        ruta1.setTotalParadas(3);
        ruta1.setTotalRecaudoEsperado(new BigDecimal("520000.00"));
        ruta1.setTotalRecaudoEfectivo(new BigDecimal("165000.00"));
        ruta1.setEstado("EN_EJECUCION");
        ruta1 = rutaRepository.save(ruta1);

        // Pedido 1: Ya ENTREGADO en Parada 1
        Pedido ped1 = new Pedido();
        ped1.setNumeroPedido("PED-2026-0001");
        ped1.setTienda(t1);
        ped1.setProveedor(femsa);
        ped1.setRuta(ruta1);
        ped1.setOrdenParada(1);
        ped1.setSubtotal(new BigDecimal("165000.00"));
        ped1.setTotalAPagar(new BigDecimal("165000.00"));
        ped1.setComisionPrismaValor(new BigDecimal("4125.00"));
        ped1.setEstado(EstadoPedido.ENTREGADO);
        ped1.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        ped1.setPinEntregaHash("849201");
        ped1.setPinValidadoEn(OffsetDateTime.now().minusHours(1));
        ped1.setFechaEstimadaEntrega(LocalDate.now());
        ped1.setFechaEntregaReal(OffsetDateTime.now().minusHours(1));
        ped1 = pedidoRepository.save(ped1);

        ItemPedido it1 = new ItemPedido();
        it1.setPedido(ped1);
        it1.setProducto(p1);
        it1.setNombreProductoHistorico(p1.getNombre());
        it1.setPresentacionHistorica(p1.getPresentacion());
        it1.setCantidad(4);
        it1.setPrecioUnitarioHistorico(p1.getPrecioUnitario());
        it1.setSubtotalLinea(new BigDecimal("114000.00"));
        itemPedidoRepository.save(it1);

        ItemPedido it2 = new ItemPedido();
        it2.setPedido(ped1);
        it2.setProducto(p2);
        it2.setNombreProductoHistorico(p2.getNombre());
        it2.setPresentacionHistorica(p2.getPresentacion());
        it2.setCantidad(1);
        it2.setPrecioUnitarioHistorico(new BigDecimal("51000.00"));
        it2.setSubtotalLinea(new BigDecimal("51000.00"));
        itemPedidoRepository.save(it2);

        // Pedido 2: EN_RUTA (Parada 2 actual en atención)
        Pedido ped2 = new Pedido();
        ped2.setNumeroPedido("PED-2026-0002");
        ped2.setTienda(t2);
        ped2.setProveedor(femsa);
        ped2.setRuta(ruta1);
        ped2.setOrdenParada(2);
        ped2.setSubtotal(new BigDecimal("220000.00"));
        ped2.setTotalAPagar(new BigDecimal("220000.00"));
        ped2.setComisionPrismaValor(new BigDecimal("5500.00"));
        ped2.setEstado(EstadoPedido.ENTREGADO);
        ped2.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        ped2.setPinEntregaHash("193842");
        ped2.setFechaEstimadaEntrega(LocalDate.now());
        ped2 = pedidoRepository.save(ped2);

        ItemPedido it3 = new ItemPedido();
        it3.setPedido(ped2);
        it3.setProducto(p3);
        it3.setNombreProductoHistorico(p3.getNombre());
        it3.setPresentacionHistorica(p3.getPresentacion());
        it3.setCantidad(5);
        it3.setPrecioUnitarioHistorico(p3.getPrecioUnitario());
        it3.setSubtotalLinea(new BigDecimal("160000.00"));
        itemPedidoRepository.save(it3);

        ItemPedido it4 = new ItemPedido();
        it4.setPedido(ped2);
        it4.setProducto(p5);
        it4.setNombreProductoHistorico(p5.getNombre());
        it4.setPresentacionHistorica(p5.getPresentacion());
        it4.setCantidad(2);
        it4.setPrecioUnitarioHistorico(p5.getPrecioUnitario());
        it4.setSubtotalLinea(new BigDecimal("52000.00"));
        itemPedidoRepository.save(it4);

        // Pedido 3: ACEPTADO / EN COLA (Parada 3 siguiente)
        Pedido ped3 = new Pedido();
        ped3.setNumeroPedido("PED-2026-0003");
        ped3.setTienda(t3);
        ped3.setProveedor(femsa);
        ped3.setRuta(ruta1);
        ped3.setOrdenParada(3);
        ped3.setSubtotal(new BigDecimal("135000.00"));
        ped3.setTotalAPagar(new BigDecimal("135000.00"));
        ped3.setComisionPrismaValor(new BigDecimal("3375.00"));
        ped3.setEstado(EstadoPedido.ACEPTADO);
        ped3.setMedioPagoUtilizado(MedioPago.EFECTIVO);
        ped3.setPinEntregaHash("771209");
        ped3.setFechaEstimadaEntrega(LocalDate.now());
        ped3 = pedidoRepository.save(ped3);

        ItemPedido it5 = new ItemPedido();
        it5.setPedido(ped3);
        it5.setProducto(p1);
        it5.setNombreProductoHistorico(p1.getNombre());
        it5.setPresentacionHistorica(p1.getPresentacion());
        it5.setCantidad(3);
        it5.setPrecioUnitarioHistorico(p1.getPrecioUnitario());
        it5.setSubtotalLinea(new BigDecimal("85500.00"));
        itemPedidoRepository.save(it5);

        ItemPedido it6 = new ItemPedido();
        it6.setPedido(ped3);
        it6.setProducto(p4);
        it6.setNombreProductoHistorico(p4.getNombre());
        it6.setPresentacionHistorica(p4.getPresentacion());
        it6.setCantidad(2);
        it6.setPrecioUnitarioHistorico(p4.getPrecioUnitario());
        it6.setSubtotalLinea(new BigDecimal("42000.00"));
        itemPedidoRepository.save(it6);

        Disputa disp = new Disputa();
        disp.setTicketNumero("DISP-2026-001");
        disp.setPedido(ped2);
        disp.setReportadoPor(tenderoUser);
        disp.setTipoIncidente(TipoIncidente.PIN_NO_DISPONIBLE_CELULAR_APAGADO);
        disp.setDescripcion("El repartidor está en la tienda pero el teléfono de la tendera se apagó y no puede consultar el PIN.");
        disp.setEstado("ABIERTO");
        disputaRepository.save(disp);

        Disputa disp2 = new Disputa();
        disp2.setTicketNumero("PQRS-2026-002");
        disp2.setPedido(ped1);
        disp2.setReportadoPor(tenderoUser);
        disp2.setTipoIncidente(TipoIncidente.MERCANCIA_DANADA_O_FALTANTE);
        disp2.setDescripcion("Llegaron 2 botellas de Coca-Cola 1.5L averiadas en la canasta. Solicito reposición o nota de crédito para el próximo pedido.");
        disp2.setEstado("ABIERTO");
        disputaRepository.save(disp2);

        Disputa disp3 = new Disputa();
        disp3.setTicketNumero("PQRS-2026-003");
        disp3.setPedido(ped3);
        disp3.setReportadoPor(tenderoUser);
        disp3.setTipoIncidente(TipoIncidente.OTRO_INCIDENTE);
        disp3.setDescripcion("Aclaración de entrega: Se solicita despachar antes de las 11:00 AM los días jueves por congestión peatonal.");
        disp3.setEstado("RESUELTO");
        disp3.setResolucionJustificacion("Ruta ajustada en el sistema para entregar a primera hora (8:30 AM).");
        disp3.setFechaResolucion(OffsetDateTime.now().minusDays(1));
        disputaRepository.save(disp3);

        // 8. Liquidación Quincenal
        LiquidacionQuincenal liq = new LiquidacionQuincenal();
        liq.setNumeroLiquidacion("LIQ-2026-Q1-001");
        liq.setProveedor(femsa);
        liq.setPeriodoInicio(LocalDate.now().minusDays(15));
        liq.setPeriodoFin(LocalDate.now());
        liq.setTotalPedidosEntregados(48);
        liq.setTotalVentasBrutas(new BigDecimal("8450000.00"));
        liq.setPorcentajeComision(new BigDecimal("2.50"));
        liq.setValorComisionPrisma(new BigDecimal("211250.00"));
        liq.setTotalFacturadoPrisma(new BigDecimal("211250.00"));
        liq.setEstado("PENDIENTE");
        liquidacionRepository.save(liq);

        // 9. Configuraciones del Sistema
        configRepository.save(new ConfiguracionSistema("comision_porcentaje", "2.50", "DECIMAL", "Porcentaje retenido por PRISMA sobre ventas", "OPERACIONES"));
        configRepository.save(new ConfiguracionSistema("radio_entrega_metros", "100", "INTEGER", "Radio máximo en metros para validación de PIN por GPS", "SEGURIDAD"));
        configRepository.save(new ConfiguracionSistema("pin_validez_minutos", "15", "INTEGER", "Vigencia en minutos del PIN criptográfico de entrega", "SEGURIDAD"));

        logger.info("Datos semilla iniciales cargados exitosamente.");
        seedNuevosProductos();
    }

    private void seedNuevosProductos() {
        var proveedores = proveedorRepository.findAll();
        if (proveedores.isEmpty()) return;

        Proveedor femsa = proveedores.stream()
                .filter(p -> p.getNombreComercial().contains("FEMSA") || p.getRazonSocial().contains("Gaseosas"))
                .findFirst().orElse(proveedores.get(0));

        Proveedor postobon = proveedores.stream()
                .filter(p -> p.getNombreComercial().contains("Postobón") || p.getRazonSocial().contains("Postobón"))
                .findFirst().orElse(femsa);

        Categoria catBebidas = categoriaRepository.findAll().stream()
                .findFirst().orElseGet(() -> {
                    Categoria c = new Categoria();
                    c.setNombre("Gaseosas y Bebidas");
                    c.setDescripcion("Bebidas carbonatadas y refrescos");
                    return categoriaRepository.save(c);
                });

        // 1. Coca-Cola 3 Litros PET Familiar
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-PET-3000",
                "Coca-Cola Sabor Original 3 Litros PET",
                "Presentación familiar no retornable para fin de semana.",
                "Paca x 6 Botellas",
                new BigDecimal("54000.00"), new BigDecimal("66000.00"), 9999,
                "https://images.unsplash.com/photo-1554866585-cd94860890b7?w=500&auto=format&fit=crop&q=80", "PROMOCION");

        // 2. Coca-Cola Vidrio Retornable 350ml
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-350ML-RET",
                "Coca-Cola Vidrio Retornable 350ml",
                "Botella de vidrio tradicional en canasta plástica de máxima rotación.",
                "Canasta x 30 Botellas",
                new BigDecimal("36000.00"), new BigDecimal("54000.00"), 9999,
                "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 3. Sprite Lima-Limón 400ml PET
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-SPRITE-400",
                "Sprite Lima-Limón 400ml PET",
                "Gaseosa refrescante sabor natural lima-limón personal.",
                "Paca x 12 Unidades",
                new BigDecimal("27000.00"), new BigDecimal("34800.00"), 9999,
                "https://images.unsplash.com/photo-1625772299848-391b6a87d7b3?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 4. Quatro Toronja 400ml PET
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-QUATRO-400",
                "Quatro Toronja 400ml PET",
                "Gaseosa sabor a toronja de alta preferencia en tienda de barrio.",
                "Paca x 12 Unidades",
                new BigDecimal("28000.00"), new BigDecimal("35400.00"), 9999,
                "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 5. Monster Energy Drink 473ml
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-MONSTER-473",
                "Monster Energy Drink 473ml",
                "Bebida energizante importada lata grande de alta rotación.",
                "Bandeja x 12 Latas",
                new BigDecimal("86000.00"), new BigDecimal("108000.00"), 9999,
                "https://images.unsplash.com/photo-1622543925917-763c34d1a86e?w=500&auto=format&fit=crop&q=80", "NUEVO_LANZAMIENTO");

        // 6. Powerade Ion4 Mountain Berry 500ml
        registrarProductoSiNoExiste(femsa, catBebidas, "KO-POWER-500",
                "Powerade Ion4 Mountain Berry 500ml",
                "Bebida hidratante con 4 electrolitos para deportistas.",
                "Paca x 12 Botellas",
                new BigDecimal("32400.00"), new BigDecimal("42000.00"), 9999,
                "https://images.unsplash.com/photo-1548839140-29a749e1bc4e?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 7. Manzana Postobón 400ml PET
        registrarProductoSiNoExiste(postobon, catBebidas, "PST-MANZ-400",
                "Manzana Postobón 400ml PET",
                "Gaseosa sabor manzana favorita de Colombia.",
                "Paca x 15 Unidades",
                new BigDecimal("36000.00"), new BigDecimal("42000.00"), 9999,
                "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?w=500&auto=format&fit=crop&q=80", "PROMOCION");

        // 8. Gaseosa Colombiana La Nuestra 400ml
        registrarProductoSiNoExiste(postobon, catBebidas, "PST-COLOMB-400",
                "Gaseosa Colombiana La Nuestra 400ml",
                "Bebida tradicional colombiana sabor kola champagne.",
                "Paca x 15 Unidades",
                new BigDecimal("36000.00"), new BigDecimal("43500.00"), 9999,
                "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 9. Jugo Hit Mora 500ml PET
        registrarProductoSiNoExiste(postobon, catBebidas, "HIT-MORA-500ML",
                "Jugo Hit Mora 500ml PET",
                "Bebida de néctar de fruta mora tropical.",
                "Paca x 12 Unidades",
                new BigDecimal("26400.00"), new BigDecimal("33600.00"), 9999,
                "https://images.unsplash.com/photo-1613478223719-2ab802602423?w=500&auto=format&fit=crop&q=80", "ACTIVO");

        // 10. Bretaña Soda Mezclador 300ml Vidrio
        registrarProductoSiNoExiste(postobon, catBebidas, "PST-BRETANA-300",
                "Bretaña Soda Mezclador 300ml Vidrio",
                "Soda gasificada mezcladora tradicional en botella de vidrio.",
                "Canasta x 24 Botellas",
                new BigDecimal("38000.00"), new BigDecimal("48000.00"), 9999,
                "https://images.unsplash.com/photo-1554866585-cd94860890b7?w=500&auto=format&fit=crop&q=80", "ACTIVO");
    }

    private void registrarProductoSiNoExiste(Proveedor proveedor, Categoria categoria, String sku, String nombre,
                                            String descripcion, String presentacion, BigDecimal precioUnitario,
                                            BigDecimal precioSugerido, int stock, String imagenUrl, String estado) {
        if (productoRepository.findByProveedorIdAndSku(proveedor.getId(), sku).isEmpty()) {
            Producto p = new Producto();
            p.setProveedor(proveedor);
            p.setCategoria(categoria);
            p.setSku(sku);
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setPresentacion(presentacion);
            p.setPrecioUnitario(precioUnitario);
            p.setPrecioSugeridoPublico(precioSugerido);
            p.setStockDisponible(stock);
            p.setImagenUrl(imagenUrl);
            p.setEstado(estado);
            productoRepository.save(p);
            logger.info("Nuevo producto registrado en catálogo: {} (SKU: {})", nombre, sku);
        }
    }
}
