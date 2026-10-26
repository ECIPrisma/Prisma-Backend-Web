package com.prisma.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.api.entity.Pedido;
import com.prisma.api.entity.Producto;
import com.prisma.api.entity.Proveedor;
import com.prisma.api.entity.Ruta;
import com.prisma.api.entity.Usuario;
import com.prisma.api.enums.Rol;
import com.prisma.api.repository.AuditLogRepository;
import com.prisma.api.repository.PedidoRepository;
import com.prisma.api.repository.ProductoRepository;
import com.prisma.api.repository.ProveedorRepository;
import com.prisma.api.repository.RutaRepository;
import com.prisma.api.repository.TiendaRepository;
import com.prisma.api.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de integración: arranca la aplicación completa (seguridad JWT, validación,
 * AOP de auditoría, JPA) sobre H2 en memoria con los datos semilla de {@code DataInitializer}.
 * Cada prueba corre en una transacción que se revierte al terminar, por lo que son independientes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public abstract class IntegrationTestBase {

    // Usuarios sembrados por DataInitializer
    protected static final String ADMIN = "admin@prisma.com.co";
    protected static final String ADMIN_PASS = "admin123";
    protected static final String MARTA = "dona.marta@labendicion.com";
    protected static final String MARTA_PASS = "admin123";
    protected static final String JUAN = "conductor.juan@femsa.com.co";
    protected static final String JUAN_PASS = "admin123";
    protected static final String FEMSA_USER = "logistica@femsa.com.co";
    protected static final String FEMSA_PASS = "femsa123";
    /** Contraseña de los usuarios que crean las pruebas. */
    protected static final String CLAVE_TEST = "clave1234";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected ProveedorRepository proveedorRepository;
    @Autowired protected ProductoRepository productoRepository;
    @Autowired protected PedidoRepository pedidoRepository;
    @Autowired protected RutaRepository rutaRepository;
    @Autowired protected TiendaRepository tiendaRepository;
    @Autowired protected AuditLogRepository auditLogRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------- HTTP
    protected String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return leer(r).at("/datos/token").asText();
    }

    protected ResultActions getReq(String url, String token) throws Exception {
        return mvc.perform(conToken(get(url), token));
    }

    protected ResultActions postReq(String url, String token, Object body) throws Exception {
        return mvc.perform(conToken(post(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body)));
    }

    protected ResultActions putReq(String url, String token, Object body) throws Exception {
        return mvc.perform(conToken(put(url), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected ResultActions deleteReq(String url, String token) throws Exception {
        return mvc.perform(conToken(delete(url), token));
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    protected JsonNode leer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    protected JsonNode leer(ResultActions a) throws Exception {
        return leer(a.andReturn());
    }

    // ---------------------------------------------------------------- datos de apoyo
    protected Proveedor proveedorPorNombre(String nombreComercial) {
        return proveedorRepository.findAll().stream()
                .filter(p -> nombreComercial.equals(p.getNombreComercial()))
                .findFirst().orElseThrow();
    }

    protected Proveedor femsa() { return proveedorPorNombre("Coca-Cola FEMSA Colombia"); }

    protected Proveedor postobon() { return proveedorPorNombre("Postobón Central"); }

    protected Pedido pedido(String numero) {
        return pedidoRepository.findByNumeroPedido(numero).orElseThrow();
    }

    protected Ruta rutaKennedy() {
        return rutaRepository.findAll().stream()
                .filter(r -> "RUTA-KENNEDY-01".equals(r.getCodigoRuta()))
                .findFirst().orElseThrow();
    }

    protected Usuario crearUsuario(String email, Rol rol, Proveedor proveedor) {
        return usuarioRepository.save(new Usuario(email, passwordEncoder.encode(CLAVE_TEST),
                "Usuario de prueba", "3000000000", rol, proveedor));
    }

    protected Producto crearProducto(Proveedor proveedor, String sku, String estado, String precio) {
        Producto modelo = productoRepository.findByProveedorId(femsa().getId()).get(0);
        Producto p = new Producto();
        p.setProveedor(proveedor);
        p.setCategoria(modelo.getCategoria());
        p.setSku(sku);
        p.setNombre("Producto " + sku);
        p.setDescripcion("Producto creado por una prueba");
        p.setPresentacion("Unidad");
        p.setPrecioUnitario(new BigDecimal(precio));
        p.setPrecioSugeridoPublico(new BigDecimal(precio).multiply(new BigDecimal("1.2")));
        p.setStockDisponible(100);
        p.setImagenUrl("https://example.com/" + sku + ".png");
        p.setEstado(estado);
        return productoRepository.save(p);
    }

    /** Registra un tendero nuevo por la API y devuelve su token. */
    protected String registrarTendero(String email, String nit) throws Exception {
        MvcResult r = postReq("/api/v1/auth/register", null, cuerpoRegistro(email, nit))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).at("/datos/token").asText();
    }

    protected Map<String, Object> cuerpoRegistro(String email, String nit) {
        return new java.util.HashMap<>(Map.of(
                "nombreCompleto", "Pedro Pérez",
                "email", email,
                "password", CLAVE_TEST,
                "telefono", "3101234567",
                "nombreTienda", "Tienda de Pedro",
                "nitOCedula", nit,
                "direccion", "Calle 1 # 2-3",
                "barrio", "Kennedy"));
    }

    protected static List<Map<String, Object>> items(Object... productoYCantidad) {
        java.util.ArrayList<Map<String, Object>> lista = new java.util.ArrayList<>();
        for (int i = 0; i < productoYCantidad.length; i += 2) {
            lista.add(Map.of("productoId", productoYCantidad[i], "cantidad", productoYCantidad[i + 1]));
        }
        return lista;
    }
}
