package com.prisma.api.security;

import com.prisma.api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Matriz de permisos: cada rol solo entra a lo suyo (200) y es rechazado en lo demás (403 / 401 sin sesión). */
class AutorizacionPorRolTest extends IntegrationTestBase {

    private String tokenDe(String rol) throws Exception {
        return switch (rol) {
            case "TENDERO" -> login(MARTA, MARTA_PASS);
            case "REPARTIDOR" -> login(JUAN, JUAN_PASS);
            case "PROVEEDOR" -> login(FEMSA_USER, FEMSA_PASS);
            case "ADMIN" -> login(ADMIN, ADMIN_PASS);
            default -> null;
        };
    }

    @ParameterizedTest(name = "{0} -> GET {1} = {2}")
    @DisplayName("Permisos por rol sobre los endpoints principales")
    @CsvSource({
            // rol,        endpoint,                              estado esperado
            "TENDERO,      /api/v1/tiendas/mis-pedidos,           200",
            "TENDERO,      /api/v1/admin/auditoria,               403",
            "TENDERO,      /api/v1/proveedores/mis-productos,     403",
            "TENDERO,      /api/v1/repartidores/mis-rutas/hoy,    403",
            "REPARTIDOR,   /api/v1/repartidores/mis-rutas/hoy,    200",
            "REPARTIDOR,   /api/v1/tiendas/mis-pedidos,           403",
            "REPARTIDOR,   /api/v1/admin/auditoria,               403",
            "PROVEEDOR,    /api/v1/proveedores/mis-productos,     200",
            "PROVEEDOR,    /api/v1/tiendas/mis-pedidos,           403",
            "PROVEEDOR,    /api/v1/admin/auditoria,               403",
            "ADMIN,        /api/v1/admin/auditoria,               200",
            "ADMIN,        /api/v1/admin/configuraciones,         200",
            "SIN_SESION,   /api/v1/tiendas/mis-pedidos,           401",
            "SIN_SESION,   /api/v1/proveedores/mis-productos,     401",
            "SIN_SESION,   /api/v1/repartidores/mis-rutas/hoy,    401",
            "SIN_SESION,   /api/v1/admin/auditoria,               401",
            "SIN_SESION,   /api/v1/auth/me,                       401"
    })
    void matrizDePermisos(String rol, String endpoint, int esperado) throws Exception {
        getReq(endpoint, tokenDe(rol)).andExpect(status().is(esperado));
    }

    @ParameterizedTest(name = "{0} no puede modificar el catálogo ni forzar entregas")
    @DisplayName("Operaciones de escritura fuera del rol -> 403")
    @CsvSource({"TENDERO", "REPARTIDOR"})
    void escriturasFueraDelRol(String rol) throws Exception {
        String token = tokenDe(rol);
        postReq("/api/v1/proveedores/productos", token, java.util.Map.of("nombre", "X")).andExpect(status().isForbidden());
        postReq("/api/v1/admin/pedidos/1/override-pin", token, java.util.Map.of("justificacion", "intento no autorizado"))
                .andExpect(status().isForbidden());
    }
}
