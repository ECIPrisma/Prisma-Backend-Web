"""
PRISMA Analytics Engine - Microservicio de IA Prescriptiva y Modo Pro.
FastAPI + DuckDB + Scikit-Learn.
(HU-27 / SCRUM-39, HU-29 / SCRUM-41, HU-30 / SCRUM-43, HU-31 / SCRUM-44).
"""

from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import os
import psutil
import time
from typing import Optional, List

from database import db
from models.elasticity import (
    calcular_elasticidad_y_prescribir_descuento,
    seleccionar_serie_por_zona,
    productos_afines,
)
from models.basket_analysis import calcular_reglas_asociacion_y_empaques

app = FastAPI(
    title="PRISMA Analytics Engine (Modo Pro)",
    description="Motor co-alojado de analítica columnar OLAP e inteligencia prescriptiva con DuckDB y Scikit-Learn",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health_check():
    """
    Health check con métricas de aislamiento de recursos (<300 MB RAM). (SCRUM-39)
    """
    process = psutil.Process(os.getpid())
    mem_info = process.memory_info()
    mem_mb = round(mem_info.rss / (1024 * 1024), 2)
    cpu_percent = process.cpu_percent(interval=0.1)

    return {
        "status": "UP",
        "service": "prisma-analytics-engine",
        "engine": "FastAPI + DuckDB + Scikit-Learn",
        "memory_usage_mb": mem_mb,
        "memory_limit_mb": 300.0,
        "within_memory_budget": mem_mb < 300.0,
        "cpu_usage_percent": cpu_percent,
        "co_hosted": True,
        "operational_cost_usd": 0.0
    }

@app.get("/api/v1/analytics/status")
def analytics_status():
    """Retorna el estado de la base de datos analítica y tablas OLAP cargadas."""
    df = db.query_df("SELECT COUNT(*) AS total FROM ventas_olap")
    count = int(df["total"].iloc[0])
    return {
        "engine_connected": True,
        "is_duckdb": db.is_duckdb,
        "total_registros_olap": count,
        "parquet_export_available": os.path.exists("data/parquet/ventas_consolidadas.parquet")
    }

@app.post("/api/v1/analytics/sync-olap")
def sync_olap_parquet():
    """
    Sincroniza la tabla analítica hacia particiones Parquet para lecturas masivas sub-segundo. (SCRUM-41)
    """
    start_time = time.time()
    parquet_path = db.exportar_a_parquet()
    registros = db.consultar_parquet(parquet_path)
    duracion_ms = round((time.time() - start_time) * 1000, 2)

    return {
        "status": "SUCCESS",
        "parquet_file": parquet_path,
        "registros_indexados": len(registros),
        "latencia_lectura_ms": duracion_ms,
        "sub_segundo": duracion_ms < 1000.0
    }

@app.get("/api/v1/analytics/prescripcion/descuentos")
def prescribir_descuentos(
    producto_id: int = Query(10, description="ID del producto a evaluar"),
    zona: str = Query("Norte", description="Zona comercial del análisis"),
    costo_unitario: Optional[float] = Query(None, gt=0, description="Costo unitario del proveedor. Si no se envía, se asume el 50% del precio promedio")
):
    """
    HU-30 (SCRUM-43): calcula la elasticidad precio-demanda del producto EN LA ZONA (regresión log-log) y prescribe:
    - demanda elástica: descuento por escala en unidades bonificadas que no sacrifica margen;
    - demanda inelástica: bloquea el descuento directo y prescribe exhibidores o paquetes cruzados (bundles).
    """
    filas = db.query_df("""
        SELECT pedido_id, producto_id, nombre_producto, precio_unitario, cantidad, zona
        FROM ventas_olap
        WHERE producto_id = ?
    """, [producto_id]).to_dict(orient="records")

    precios, cantidades, alcance = seleccionar_serie_por_zona(filas, zona)
    datos_simulados = len(set(precios)) < 2
    if datos_simulados:
        # Sin historial suficiente: serie de demostración (comportamiento previo del endpoint)
        precios = [4200.0, 4500.0, 4800.0, 5000.0]
        cantidades = [36.0, 24.0, 18.0, 12.0]
        alcance = "DEMOSTRACION"

    nombre_producto = filas[0]["nombre_producto"] if filas else f"Producto {producto_id}"

    # Productos que se piden junto con este (base de los paquetes cruzados)
    pedidos_relacionados = db.query_df("""
        SELECT pedido_id, producto_id, nombre_producto, zona
        FROM ventas_olap
        WHERE pedido_id IN (SELECT pedido_id FROM ventas_olap WHERE producto_id = ?)
    """, [producto_id]).to_dict(orient="records")
    relacionados = productos_afines(pedidos_relacionados, producto_id, zona)

    costo = costo_unitario if costo_unitario is not None else round(sum(precios) / len(precios) * 0.5, 2)

    resultado = calcular_elasticidad_y_prescribir_descuento(
        precios, cantidades, costo_unitario=costo, zona=zona,
        nombre_producto=nombre_producto, productos_relacionados=relacionados)
    resultado["producto_id"] = producto_id
    resultado["alcance_datos"] = alcance
    resultado["datos_simulados"] = datos_simulados
    return resultado

@app.get("/api/v1/analytics/prescripcion/empaques")
def prescribir_empaques(
    proveedor_id: int = Query(1, description="ID del proveedor para sugerir combos")
):
    """
    Ejecuta minería de canasta de mercado (FP-Growth) y prescribe nuevos formatos de empaque. (SCRUM-44)
    """
    df = db.query_df("""
        SELECT pedido_id, nombre_producto 
        FROM ventas_olap 
        WHERE proveedor_id = ?
        ORDER BY pedido_id
    """, [proveedor_id])

    if len(df) == 0:
        transacciones = [
            ["Coca-Cola 1.5L", "Papas Fritas 150g"],
            ["Coca-Cola 1.5L", "Galletas Chocolate"],
            ["Coca-Cola 1.5L", "Papas Fritas 150g"],
            ["Coca-Cola 1.5L", "Galletas Chocolate"],
            ["Coca-Cola 1.5L", "Papas Fritas 150g"]
        ]
    else:
        transacciones_dict = df.groupby("pedido_id")["nombre_producto"].apply(list).to_dict()
        transacciones = list(transacciones_dict.values())

    resultado = calcular_reglas_asociacion_y_empaques(transacciones, soporte_minimo=0.20, confianza_minima=0.30)
    resultado["proveedor_id"] = proveedor_id
    return resultado

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
