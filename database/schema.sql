-- ==============================================================================
-- PRISMA - RED DE TIENDAS INTELIGENTE
-- Script DDL de Base de Datos Relacional (PostgreSQL 14+)
-- Arquitectura Multitenant, Integridad Referencial y Optimización de Índices
-- ==============================================================================

-- Habilitar extensión para generación de identificadores UUID si no existe
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- 1. TABLA: proveedores (Tenant Empresarial de Consumo Masivo)
-- ==============================================================================
CREATE TABLE proveedores (
    id BIGSERIAL PRIMARY KEY,
    nit VARCHAR(20) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    nombre_comercial VARCHAR(100) NOT NULL,
    contacto_nombre VARCHAR(100) NOT NULL,
    contacto_email VARCHAR(150) NOT NULL,
    contacto_telefono VARCHAR(20) NOT NULL,
    direccion_sede VARCHAR(200),
    ciudad VARCHAR(80) NOT NULL DEFAULT 'Bogotá',
    logo_url VARCHAR(500),
    color_primario_hex VARCHAR(7) DEFAULT '#F59E0B',
    color_secundario_hex VARCHAR(7) DEFAULT '#1E6F68',
    comision_porcentaje NUMERIC(5, 2) NOT NULL DEFAULT 2.50 CHECK (comision_porcentaje >= 0.00 AND comision_porcentaje <= 100.00),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'INACTIVO')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE proveedores IS 'Empresas mayoristas CPG (Coca-Cola, Postobón, Bavaria) que operan como tenants.';
COMMENT ON COLUMN proveedores.comision_porcentaje IS 'Porcentaje de intermediación pactado con Prisma (por defecto 2.50%).';

CREATE INDEX idx_proveedores_estado ON proveedores (estado);
CREATE INDEX idx_proveedores_nit ON proveedores (nit);

-- ==============================================================================
-- 2. TABLA: usuarios (Autenticación Centralizada y RBAC)
-- ==============================================================================
CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombre_completo VARCHAR(120) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    rol VARCHAR(40) NOT NULL CHECK (rol IN (
        'ROLE_PLATFORM_ADMIN',  -- Superadministrador Prisma (nosotros)
        'ROLE_SOPORTE',         -- Agente de operaciones / mesa de ayuda
        'ROLE_CONTABILIDAD',    -- Agente financiero para cobro de comisiones
        'ROLE_PROVEEDOR_ADMIN', -- Administrador de la distribuidora (Coca-Cola, etc.)
        'ROLE_REPARTIDOR',      -- Conductor / Repartidor de camión
        'ROLE_TENDERO'          -- Tendero / Dueño del punto de venta
    )),
    proveedor_id BIGINT REFERENCES proveedores(id) ON DELETE SET NULL, -- NULL para roles Prisma y Tenderos
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO')),
    ultimo_login TIMESTAMPTZ,
    intentos_fallidos_login INT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE usuarios IS 'Cuentas de usuario de los 4 roles principales y subroles de soporte.';

CREATE INDEX idx_usuarios_email ON usuarios (email);
CREATE INDEX idx_usuarios_rol ON usuarios (rol);
CREATE INDEX idx_usuarios_proveedor_id ON usuarios (proveedor_id);

-- ==============================================================================
-- 3. TABLA: tiendas (Puntos de Venta de Barrio)
-- ==============================================================================
CREATE TABLE tiendas (
    id BIGSERIAL PRIMARY KEY,
    tendero_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    nombre_tienda VARCHAR(120) NOT NULL,
    nit_o_cedula VARCHAR(25) NOT NULL UNIQUE,
    direccion VARCHAR(200) NOT NULL,
    barrio VARCHAR(100) NOT NULL,
    ciudad VARCHAR(80) NOT NULL DEFAULT 'Bogotá',
    telefono_tienda VARCHAR(20) NOT NULL,
    latitud NUMERIC(10, 7) NOT NULL CHECK (latitud BETWEEN -90.0000000 AND 90.0000000),
    longitud NUMERIC(10, 7) NOT NULL CHECK (longitud BETWEEN -180.0000000 AND 180.0000000),
    foto_fachada_url VARCHAR(500),
    dia_preferido_entrega VARCHAR(15) CHECK (dia_preferido_entrega IN ('LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO')),
    horario_atencion VARCHAR(80) DEFAULT '07:00 - 21:00',
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'INACTIVA', 'SUSPENDIDA')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE tiendas IS 'Tiendas de barrio registradas en la red Prisma.';

CREATE INDEX idx_tiendas_tendero_id ON tiendas (tendero_id);
CREATE INDEX idx_tiendas_barrio_ciudad ON tiendas (ciudad, barrio);
CREATE INDEX idx_tiendas_coordenadas ON tiendas (latitud, longitud);

-- ==============================================================================
-- 4. TABLA: proveedor_tiendas (Onboarding y Relación Comercial B2B)
-- ==============================================================================
CREATE TABLE proveedor_tiendas (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE CASCADE,
    tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE CASCADE,
    codigo_cliente_erp VARCHAR(50), -- Código con el que Coca-Cola o Postobón identifica esta tienda en su ERP/SAP
    cupo_credito_asignado NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (cupo_credito_asignado >= 0),
    dias_plazo_pago INT NOT NULL DEFAULT 0 CHECK (dias_plazo_pago >= 0), -- 0 = Estricto Contra Entrega
    estado VARCHAR(20) NOT NULL DEFAULT 'HABILITADA' CHECK (estado IN ('HABILITADA', 'BLOQUEADA_CARTERA', 'INACTIVA')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_proveedor_tienda UNIQUE (proveedor_id, tienda_id)
);

CREATE INDEX idx_proveedor_tiendas_lookup ON proveedor_tiendas (proveedor_id, tienda_id);
CREATE INDEX idx_proveedor_tiendas_erp ON proveedor_tiendas (codigo_cliente_erp);

-- ==============================================================================
-- 5. TABLA: categorias (Clasificación de Catálogo)
-- ==============================================================================
CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT REFERENCES proveedores(id) ON DELETE CASCADE, -- NULL si es global o asignado al proveedor
    nombre VARCHAR(80) NOT NULL,
    descripcion TEXT,
    icono_url VARCHAR(500),
    orden_visual INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_categorias_proveedor ON categorias (proveedor_id, activo);

-- ==============================================================================
-- 6. TABLA: productos (Catálogo de Artículos por Proveedor)
-- ==============================================================================
CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE CASCADE,
    categoria_id BIGINT NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
    sku VARCHAR(50) NOT NULL,
    codigo_barras_ean VARCHAR(30),
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    presentacion VARCHAR(100) NOT NULL, -- Ej: 'Paca x 24 un 400ml', 'Canasta x 30 botellas retornable'
    precio_unitario NUMERIC(12, 2) NOT NULL CHECK (precio_unitario >= 0),
    precio_sugerido_publico NUMERIC(12, 2) CHECK (precio_sugerido_publico >= precio_unitario),
    stock_disponible INT NOT NULL DEFAULT 0 CHECK (stock_disponible >= 0),
    cantidad_minima_pedido INT NOT NULL DEFAULT 1 CHECK (cantidad_minima_pedido >= 1),
    es_retornable BOOLEAN NOT NULL DEFAULT FALSE,
    imagen_url VARCHAR(500),
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'AGOTADO', 'DESCONTINUADO')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_proveedor_sku UNIQUE (proveedor_id, sku)
);

COMMENT ON TABLE productos IS 'Artículos mayoristas provistos por cada marca distribuidora.';

CREATE INDEX idx_productos_proveedor_estado ON productos (proveedor_id, estado);
CREATE INDEX idx_productos_categoria ON productos (categoria_id);
CREATE INDEX idx_productos_ean ON productos (codigo_barras_ean);

-- ==============================================================================
-- 7. TABLA: promociones (Ofertas y Banners)
-- ==============================================================================
CREATE TABLE promociones (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE CASCADE,
    titulo VARCHAR(120) NOT NULL,
    descripcion TEXT,
    porcentaje_descuento NUMERIC(5, 2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_descuento >= 0.00 AND porcentaje_descuento <= 100.00),
    compra_minima_valor NUMERIC(12, 2) DEFAULT 0.00,
    banner_imagen_url VARCHAR(500),
    fecha_inicio TIMESTAMPTZ NOT NULL,
    fecha_fin TIMESTAMPTZ NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_fechas_promo CHECK (fecha_fin > fecha_inicio)
);

CREATE INDEX idx_promociones_activas ON promociones (proveedor_id, activo, fecha_inicio, fecha_fin);

-- ==============================================================================
-- 8. TABLA: camiones (Flota Logística de Reparto)
-- ==============================================================================
CREATE TABLE camiones (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE CASCADE,
    placa VARCHAR(10) NOT NULL UNIQUE,
    marca_modelo VARCHAR(80) NOT NULL,
    capacidad_peso_kg NUMERIC(8, 2) NOT NULL CHECK (capacidad_peso_kg > 0),
    capacidad_canastas INT NOT NULL CHECK (capacidad_canastas > 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE' CHECK (estado IN ('DISPONIBLE', 'EN_RUTA', 'EN_MANTENIMIENTO', 'INACTIVO')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_camiones_proveedor ON camiones (proveedor_id, estado);

-- ==============================================================================
-- 9. TABLA: rutas (Hojas de Ruta y Asignación de Despacho)
-- ==============================================================================
CREATE TABLE rutas (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE CASCADE,
    camion_id BIGINT NOT NULL REFERENCES camiones(id) ON DELETE RESTRICT,
    repartidor_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    codigo_ruta VARCHAR(30) NOT NULL UNIQUE, -- Ej: 'RUTA-BOG-NORTE-20261004-01'
    fecha_programada DATE NOT NULL,
    zona_sector VARCHAR(100) NOT NULL,
    total_paradas INT NOT NULL DEFAULT 0,
    total_recaudo_esperado NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_recaudo_efectivo NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(25) NOT NULL DEFAULT 'PLANIFICADA' CHECK (estado IN ('PLANIFICADA', 'CARGADA_BODEGA', 'EN_TRANSITO', 'FINALIZADA', 'CANCELADA')),
    hora_inicio_real TIMESTAMPTZ,
    hora_fin_real TIMESTAMPTZ,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE rutas IS 'Hoja de ruta que agrupa pedidos para un camión y conductor en una fecha.';

CREATE INDEX idx_rutas_proveedor_fecha ON rutas (proveedor_id, fecha_programada);
CREATE INDEX idx_rutas_repartidor_estado ON rutas (repartidor_id, estado);
CREATE INDEX idx_rutas_camion ON rutas (camion_id);

-- ==============================================================================
-- 10. TABLA: pedidos (Órdenes B2B y Máquina de Estados)
-- ==============================================================================
CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    numero_pedido VARCHAR(30) NOT NULL UNIQUE, -- Ej: 'PED-202610-0010492'
    tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE RESTRICT,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE RESTRICT,
    ruta_id BIGINT REFERENCES rutas(id) ON DELETE SET NULL,
    orden_parada INT DEFAULT 0, -- Secuencia de entrega en la ruta (1, 2, 3...)
    
    -- Valores Monetarios
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0),
    descuento NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (descuento >= 0),
    total_a_pagar NUMERIC(12, 2) NOT NULL CHECK (total_a_pagar >= 0),
    comision_prisma_valor NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (comision_prisma_valor >= 0), -- 2.5% de total_a_pagar
    
    -- Estados y Seguridad
    estado VARCHAR(25) NOT NULL DEFAULT 'CREADO' CHECK (estado IN (
        'CREADO',             -- Enviado por el tendero
        'ACEPTADO',            -- Confirmado por el proveedor
        'CARGADO_EN_BODEGA',   -- Cargado en el camión
        'EN_RUTA',             -- Chofer en camino
        'ENTREGADO',           -- Mercancía entregada y validada con PIN
        'RECHAZADO',           -- Proveedor no pudo surtir
        'CANCELADO'            -- Cancelado por tienda o incidente
    )),
    
    -- Verificación de Entrega
    pin_entrega_hash VARCHAR(10) NOT NULL, -- PIN de 4 dígitos (ej: '7492')
    pin_validado_en TIMESTAMPTZ,
    medio_pago_utilizado VARCHAR(25) NOT NULL DEFAULT 'EFECTIVO' CHECK (medio_pago_utilizado IN ('EFECTIVO', 'NEQUI_DAVIPLATA', 'DATAFONO', 'CREDITO_PROVEEDOR')),
    notas_entrega TEXT,
    
    -- Auditoría Temporal
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_estimada_entrega DATE NOT NULL,
    fecha_entrega_real TIMESTAMPTZ,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE pedidos IS 'Transacciones comerciales B2B entre tiendas de barrio y proveedores CPG.';
COMMENT ON COLUMN pedidos.pin_entrega_hash IS 'PIN de 4 dígitos generado en el celular del tendero que el repartidor ingresa.';

CREATE INDEX idx_pedidos_tienda_estado ON pedidos (tienda_id, estado);
CREATE INDEX idx_pedidos_proveedor_estado ON pedidos (proveedor_id, estado);
CREATE INDEX idx_pedidos_ruta_parada ON pedidos (ruta_id, orden_parada);
CREATE INDEX idx_pedidos_fecha_creacion ON pedidos (fecha_creacion DESC);
CREATE INDEX idx_pedidos_numero ON pedidos (numero_pedido);

-- ==============================================================================
-- 11. TABLA: items_pedido (Líneas de Detalle de Productos en Pedido)
-- ==============================================================================
CREATE TABLE items_pedido (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE RESTRICT,
    nombre_producto_historico VARCHAR(150) NOT NULL, -- Preserva el nombre al momento de la compra
    presentacion_historica VARCHAR(100) NOT NULL,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    precio_unitario_historico NUMERIC(12, 2) NOT NULL CHECK (precio_unitario_historico >= 0),
    subtotal_linea NUMERIC(12, 2) NOT NULL CHECK (subtotal_linea >= 0)
);

CREATE INDEX idx_items_pedido_pedido_id ON items_pedido (pedido_id);
CREATE INDEX idx_items_pedido_producto_id ON items_pedido (producto_id);

-- ==============================================================================
-- 12. TABLA: rastreo_camion (Pings GPS de Ubicación en Tiempo Real - Low Cost)
-- ==============================================================================
CREATE TABLE rastreo_camion (
    id BIGSERIAL PRIMARY KEY,
    camion_id BIGINT NOT NULL REFERENCES camiones(id) ON DELETE CASCADE,
    repartidor_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    ruta_id BIGINT NOT NULL REFERENCES rutas(id) ON DELETE CASCADE,
    latitud NUMERIC(10, 7) NOT NULL CHECK (latitud BETWEEN -90.0000000 AND 90.0000000),
    longitud NUMERIC(10, 7) NOT NULL CHECK (longitud BETWEEN -180.0000000 AND 180.0000000),
    velocidad_kmh NUMERIC(5, 2) DEFAULT 0.00,
    rumbo_grados NUMERIC(5, 2) DEFAULT 0.00,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE rastreo_camion IS 'Registro periódico (cada 30-45s) de telemetría GPS del repartidor a costo $0 USD.';

CREATE INDEX idx_rastreo_camion_ruta_time ON rastreo_camion (ruta_id, timestamp DESC);
CREATE INDEX idx_rastreo_camion_camion_time ON rastreo_camion (camion_id, timestamp DESC);

-- ==============================================================================
-- 13. TABLA: liquidaciones_quincenales (Cobro de Comisión 2.5% Prisma a Proveedores)
-- ==============================================================================
CREATE TABLE liquidaciones_quincenales (
    id BIGSERIAL PRIMARY KEY,
    numero_liquidacion VARCHAR(30) NOT NULL UNIQUE, -- Ej: 'LIQ-202610-01-POSTOBON'
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id) ON DELETE RESTRICT,
    periodo_inicio DATE NOT NULL,
    periodo_fin DATE NOT NULL,
    total_pedidos_entregados INT NOT NULL DEFAULT 0,
    total_ventas_brutas NUMERIC(14, 2) NOT NULL CHECK (total_ventas_brutas >= 0),
    porcentaje_comision NUMERIC(5, 2) NOT NULL DEFAULT 2.50,
    valor_comision_prisma NUMERIC(14, 2) NOT NULL CHECK (valor_comision_prisma >= 0),
    valor_iva_comision NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_facturado_prisma NUMERIC(14, 2) NOT NULL CHECK (total_facturado_prisma >= 0),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADA', 'VENCIDA', 'EN_CONCILIACION')),
    cufe_dian VARCHAR(100),
    fecha_pago TIMESTAMPTZ,
    comprobante_bancario_ref VARCHAR(100),
    generado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_periodo_liq CHECK (periodo_fin >= periodo_inicio)
);

COMMENT ON TABLE liquidaciones_quincenales IS 'Facturas de cobro emitidas por Prisma a cada proveedor por el 2.5% de comisión.';

CREATE INDEX idx_liquidaciones_proveedor ON liquidaciones_quincenales (proveedor_id, estado);
CREATE INDEX idx_liquidaciones_periodo ON liquidaciones_quincenales (periodo_inicio, periodo_fin);

-- ==============================================================================
-- 14. TABLA: disputas (Mesa de Incidentes y Resolución de Operaciones)
-- ==============================================================================
CREATE TABLE disputas (
    id BIGSERIAL PRIMARY KEY,
    ticket_numero VARCHAR(30) NOT NULL UNIQUE, -- Ej: 'INC-20261004-0042'
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE RESTRICT,
    reportado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    agente_resolutor_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    tipo_incidente VARCHAR(50) NOT NULL CHECK (tipo_incidente IN (
        'PIN_NO_DISPONIBLE_CELULAR_APAGADO',
        'TIENDA_CERRADA',
        'DINERO_INCOMPLETO',
        'MERCANCIA_DANADA_O_FALTANTE',
        'CAMION_VARADO_ACCIDENTE',
        'OTRO_INCIDENTE'
    )),
    descripcion TEXT NOT NULL,
    estado VARCHAR(25) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'EN_REVISION', 'RESUELTO_OVERRIDE', 'RESUELTO_ANULADO', 'DESCARTADO')),
    resolucion_justificacion TEXT,
    fecha_reporte TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    fecha_resolucion TIMESTAMPTZ
);

COMMENT ON TABLE disputas IS 'Casos de soporte operativo atendidos sin tocar código.';

CREATE INDEX idx_disputas_pedido ON disputas (pedido_id);
CREATE INDEX idx_disputas_estado ON disputas (estado);
CREATE INDEX idx_disputas_agente ON disputas (agente_resolutor_id);

-- ==============================================================================
-- 15. TABLA: configuracion_sistema (Feature Flags y Parámetros en Caliente)
-- ==============================================================================
CREATE TABLE configuracion_sistema (
    clave VARCHAR(80) PRIMARY KEY,
    valor TEXT NOT NULL,
    tipo_dato VARCHAR(20) NOT NULL DEFAULT 'STRING' CHECK (tipo_dato IN ('STRING', 'INTEGER', 'NUMERIC', 'BOOLEAN', 'JSON')),
    descripcion TEXT NOT NULL,
    categoria VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    es_sensible BOOLEAN NOT NULL DEFAULT FALSE,
    actualizado_por_usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE configuracion_sistema IS 'Variables operativas globales modificables en caliente sin reiniciar la app.';

-- ==============================================================================
-- 16. TABLA: audit_logs (Trazabilidad Forense Inmutable por Agente)
-- ==============================================================================
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    agente_id BIGINT NOT NULL,
    agente_nombre VARCHAR(150) NOT NULL,
    agente_email VARCHAR(150) NOT NULL,
    agente_rol VARCHAR(50) NOT NULL,
    proveedor_id BIGINT,
    tipo_evento VARCHAR(100) NOT NULL,
    nivel_severidad VARCHAR(20) NOT NULL DEFAULT 'INFO' CHECK (nivel_severidad IN ('INFO', 'WARNING', 'CRITICAL', 'SECURITY')),
    recurso_afectado_tipo VARCHAR(50) NOT NULL,
    recurso_afectado_id VARCHAR(100) NOT NULL,
    justificacion TEXT NOT NULL,
    valores_anteriores JSONB,
    valores_nuevos JSONB,
    ip_origen VARCHAR(45) NOT NULL,
    user_agent TEXT NOT NULL,
    resultado VARCHAR(20) NOT NULL DEFAULT 'EXITOSO' CHECK (resultado IN ('EXITOSO', 'DENEGADO', 'ERROR'))
);

COMMENT ON TABLE audit_logs IS 'Rastro forense append-only inmutable de todas las acciones operativas.';

CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_agente_id ON audit_logs (agente_id);
CREATE INDEX idx_audit_logs_tipo_evento ON audit_logs (tipo_evento);
CREATE INDEX idx_audit_logs_recurso ON audit_logs (recurso_afectado_tipo, recurso_afectado_id);

-- Reglas de inmutabilidad: Impedir UPDATE o DELETE sobre audit_logs
CREATE OR REPLACE RULE audit_logs_no_update AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
CREATE OR REPLACE RULE audit_logs_no_delete AS ON DELETE TO audit_logs DO INSTEAD NOTHING;

-- ==============================================================================
-- FIN DEL ESQUEMA DDL PRISMA
-- ==============================================================================
