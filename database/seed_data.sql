-- ==============================================================================
-- PRISMA - Datos Semilla / Pruebas de Desarrollo (seed_data.sql)
-- ==============================================================================

-- 1. Configuraciones Globales del Sistema en Caliente
INSERT INTO configuracion_sistema (clave, valor, tipo_dato, descripcion, categoria, es_sensible) VALUES
('comision_base_porcentaje', '2.50', 'NUMERIC', 'Porcentaje estándar de intermediación cobrado quincenalmente a proveedores', 'FINANZAS', false),
('radio_alerta_gps_metros', '500', 'INTEGER', 'Radio en metros para notificar a la tienda que el camión está próximo', 'LOGISTICA', false),
('frecuencia_ping_gps_segundos', '35', 'INTEGER', 'Frecuencia de envío de telemetría GPS desde la app del repartidor', 'LOGISTICA', false),
('tiempo_expiracion_pedido_horas', '24', 'INTEGER', 'Tiempo máximo para que un proveedor acepte un pedido antes de anulación', 'OPERACIONES', false),
('banner_global_activo', 'false', 'BOOLEAN', 'Activar banner de anuncio de mantenimiento en todas las apps', 'UI', false),
('banner_global_mensaje', 'Prisma operando con normalidad en toda la ciudad.', 'STRING', 'Texto del banner superior en web y móvil', 'UI', false);

-- 2. Proveedores CPG (Marcas Mayoristas)
INSERT INTO proveedores (nit, razon_social, nombre_comercial, contacto_nombre, contacto_email, contacto_telefono, direccion_sede, ciudad, color_primario_hex, color_secundario_hex, comision_porcentaje, estado) VALUES
('860005224-1', 'Industria Nacional de Gaseosas S.A.S.', 'Coca-Cola FEMSA', 'Carlos Mendoza', 'distribucion.femsa@cocacola.com.co', '+573102223344', 'Av. Boyacá # 34-80', 'Bogotá', '#E11D48', '#1E6F68', 2.50, 'ACTIVO'),
('890903939-5', 'Postobón S.A.', 'Postobón Central', 'Mariana Gómez', 'operaciones.bogota@postobon.com.co', '+573155556677', 'Zona Industrial Montevideo Cl 19 # 68-50', 'Bogotá', '#F59E0B', '#1E6F68', 2.50, 'ACTIVO');

-- 3. Usuarios de Sistema (Password genérico bcrypt para dev: 'Prisma2026*')
INSERT INTO usuarios (email, password_hash, nombre_completo, telefono, rol, proveedor_id, estado) VALUES
-- Admin Prisma (Nosotros)
('admin@prisma.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Juan Admin Prisma', '+573001112233', 'ROLE_PLATFORM_ADMIN', NULL, 'ACTIVO'),
('soporte@prisma.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Laura Soporte Helpdesk', '+573004445566', 'ROLE_SOPORTE', NULL, 'ACTIVO'),
-- Admins de Proveedores
('femsa.admin@cocacola.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Gerente Logística FEMSA', '+573102223344', 'ROLE_PROVEEDOR_ADMIN', 1, 'ACTIVO'),
('postobon.admin@postobon.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Coordinador Postobón', '+573155556677', 'ROLE_PROVEEDOR_ADMIN', 2, 'ACTIVO'),
-- Repartidores / Conductores
('repartidor.femsa@cocacola.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Nelson Conductor FEMSA', '+573117778899', 'ROLE_REPARTIDOR', 1, 'ACTIVO'),
('repartidor.postobon@postobon.com.co', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Jairo Conductor Postobón', '+573129990011', 'ROLE_REPARTIDOR', 2, 'ACTIVO'),
-- Tenderos
('donpedro@tiendaeloasis.com', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'Pedro Pablo Pérez', '+573148889900', 'ROLE_TENDERO', NULL, 'ACTIVO'),
('donamaria@laesperanza.com', '$2a$12$e8h12f71891238912389123891238912389123891238912389123', 'María Rodríguez', '+573167776655', 'ROLE_TENDERO', NULL, 'ACTIVO');

-- 4. Tiendas de Barrio
INSERT INTO tiendas (tendero_id, nombre_tienda, nit_o_cedula, direccion, barrio, ciudad, telefono_tienda, latitud, longitud, dia_preferido_entrega) VALUES
(7, 'Tienda y Abarrotes El Oasis', '79845123', 'Cra 50 # 128B-15', 'Prado Veraniego', 'Bogotá', '+573148889900', 4.7158900, -74.0532100, 'LUNES'),
(8, 'Minimercado La Esperanza', '52189445', 'Cl 134 # 45A-20', 'Alcalá', 'Bogotá', '+573167776655', 4.7214500, -74.0489000, 'MARTES');

-- 5. Vinculación Tiendas - Proveedores (Onboarding)
INSERT INTO proveedor_tiendas (proveedor_id, tienda_id, codigo_cliente_erp, cupo_credito_asignado, dias_plazo_pago, estado) VALUES
(1, 1, 'CC-BOG-49012', 0.00, 0, 'HABILITADA'),
(1, 2, 'CC-BOG-49013', 500000.00, 7, 'HABILITADA'),
(2, 1, 'PST-109283', 0.00, 0, 'HABILITADA'),
(2, 2, 'PST-109284', 0.00, 0, 'HABILITADA');

-- 6. Categorías
INSERT INTO categorias (proveedor_id, nombre, descripcion, orden_visual, activo) VALUES
(1, 'Gaseosas y Colas', 'Línea de bebidas carbonatadas Coca-Cola', 1, true),
(1, 'Aguas y Jugos', 'Agua Brisa, Valle Frut y Del Valle Fresh', 2, true),
(2, 'Gaseosas Saborizadas', 'Postobón Manzana, Colombiana, Naranjada y Bretaña', 1, true),
(2, 'Jugos y Té', 'Hit Néctar y Mr Tea', 2, true);

-- 7. Catálogo de Productos
INSERT INTO productos (proveedor_id, categoria_id, sku, codigo_barras_ean, nombre, descripcion, presentacion, precio_unitario, precio_sugerido_publico, stock_disponible, cantidad_minima_pedido, es_retornable, estado) VALUES
(1, 1, 'KO-400ML-PAC', '7702535001010', 'Coca-Cola Original 400ml Pet', 'Botella pet no retornable para venta individual', 'Paca x 12 unidades', 24000.00, 2500.00, 150, 1, false, 'ACTIVO'),
(1, 1, 'KO-1.5L-PAC', '7702535001027', 'Coca-Cola Sabor Original 1.5L', 'Presentación familiar no retornable', 'Paca x 6 unidades', 28500.00, 5500.00, 80, 1, false, 'ACTIVO'),
(1, 1, 'KO-350ML-RET', '7702535001034', 'Coca-Cola Vidrio Retornable 350ml', 'Botella de vidrio tradicional en canasta plástica', 'Canasta x 30 botellas', 36000.00, 1800.00, 60, 1, true, 'ACTIVO'),
(2, 3, 'PST-MANZ-1.5L', '7702007002011', 'Postobón Manzana 1.5L Pet', 'Gaseosa sabor manzana favorita de Colombia', 'Paca x 6 unidades', 25200.00, 4800.00, 200, 1, false, 'ACTIVO'),
(2, 4, 'HIT-MORA-500ML', '7702007003025', 'Jugo Hit Mora 500ml Pet', 'Bebida con fruta tropical con néctar de mora', 'Paca x 12 unidades', 26400.00, 2600.00, 120, 1, false, 'ACTIVO');

-- 8. Flota de Camiones
INSERT INTO camiones (proveedor_id, placa, marca_modelo, capacidad_peso_kg, capacidad_canastas, estado) VALUES
(1, 'WLM-420', 'Chevrolet FTR Furgón', 7500.00, 350, 'EN_RUTA'),
(2, 'TLK-890', 'Hino 500 Plataforma', 8000.00, 400, 'DISPONIBLE');

-- 9. Ruta Activa
INSERT INTO rutas (proveedor_id, camion_id, repartidor_id, codigo_ruta, fecha_programada, zona_sector, total_paradas, total_recaudo_esperado, estado) VALUES
(1, 1, 5, 'RUTA-BOG-SUBANORTE-01', CURRENT_DATE, 'Suba - Prado Veraniego - Alcalá', 2, 88500.00, 'EN_TRANSITO');

-- 10. Pedido en Ruta con PIN
INSERT INTO pedidos (numero_pedido, tienda_id, proveedor_id, ruta_id, orden_parada, subtotal, descuento, total_a_pagar, comision_prisma_valor, estado, pin_entrega_hash, medio_pago_utilizado, fecha_estimada_entrega) VALUES
('PED-202610-0010492', 1, 1, 1, 1, 88500.00, 0.00, 88500.00, 2212.50, 'EN_RUTA', '7492', 'EFECTIVO', CURRENT_DATE);

-- 11. Líneas del Pedido
INSERT INTO items_pedido (pedido_id, producto_id, nombre_producto_historico, presentacion_historica, cantidad, precio_unitario_historico, subtotal_linea) VALUES
(1, 1, 'Coca-Cola Original 400ml Pet', 'Paca x 12 unidades', 2, 24000.00, 48000.00),
(1, 2, 'Coca-Cola Sabor Original 1.5L', 'Paca x 6 unidades', 1, 28500.00, 28500.00),
(1, 3, 'Coca-Cola Vidrio Retornable 350ml (Recarga)', 'Canasta x 30 botellas', 1, 12000.00, 12000.00);

-- 12. Log de Auditoría Inicial
INSERT INTO audit_logs (agente_id, agente_nombre, agente_email, agente_rol, proveedor_id, tipo_evento, nivel_severidad, recurso_afectado_tipo, recurso_afectado_id, justificacion, ip_origen, user_agent, resultado) VALUES
(1, 'Juan Admin Prisma', 'admin@prisma.com.co', 'ROLE_PLATFORM_ADMIN', NULL, 'INICIALIZACION_SISTEMA', 'INFO', 'SISTEMA', 'ROOT', 'Instalación y aprovisionamiento inicial de la base de datos de Prisma', '127.0.0.1', 'MigrationScript/1.0', 'EXITOSO');
