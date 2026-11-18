"""
Gestor de persistencia analítica OLAP híbrido (DuckDB + Parquet con fallback SQLite3 para CPUs sin AVX).
(HU-27 / SCRUM-39 y HU-29 / SCRUM-41).
"""

import os
import sqlite3
import pandas as pd
from typing import List, Dict, Any

DUCKDB_AVAILABLE = False
try:
    import duckdb
    # Verificar que duckdb no falle en runtime por falta de soporte AVX
    _test_conn = duckdb.connect(":memory:")
    _test_conn.execute("SELECT 1").fetchone()
    _test_conn.close()
    DUCKDB_AVAILABLE = True
except Exception:
    DUCKDB_AVAILABLE = False

DB_PATH = os.environ.get("DUCKDB_PATH", "prisma_analytics.duckdb")
SQLITE_DB_PATH = os.environ.get("SQLITE_PATH", "prisma_analytics.sqlite")
PARQUET_DIR = os.environ.get("PARQUET_DIR", "data/parquet")

class AnalyticsDatabase:
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        self.is_duckdb = DUCKDB_AVAILABLE
        self._duckdb_conn = None
        self._sqlite_conn = None
        self.init_database()

    def get_connection(self):
        if self.is_duckdb:
            if self._duckdb_conn is None:
                self._duckdb_conn = duckdb.connect(self.db_path)
            return self._duckdb_conn
        else:
            if self._sqlite_conn is None:
                self._sqlite_conn = sqlite3.connect(SQLITE_DB_PATH, check_same_thread=False)
            return self._sqlite_conn

    def init_database(self):
        os.makedirs(PARQUET_DIR, exist_ok=True)
        conn = self.get_connection()
        if self.is_duckdb:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS ventas_olap (
                    pedido_id BIGINT,
                    tienda_id BIGINT,
                    proveedor_id BIGINT,
                    producto_id BIGINT,
                    nombre_producto VARCHAR,
                    categoria VARCHAR,
                    cantidad INT,
                    precio_unitario DOUBLE,
                    total DOUBLE,
                    zona VARCHAR,
                    fecha_pedido TIMESTAMP
                );
            """)
            count = conn.execute("SELECT COUNT(*) FROM ventas_olap").fetchone()[0]
            if count == 0:
                self._cargar_semilla_analitica()
                self._cargar_semilla_formatos_empaque()
        else:
            cursor = conn.cursor()
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS ventas_olap (
                    pedido_id INTEGER,
                    tienda_id INTEGER,
                    proveedor_id INTEGER,
                    producto_id INTEGER,
                    nombre_producto TEXT,
                    categoria TEXT,
                    cantidad INTEGER,
                    precio_unitario REAL,
                    total REAL,
                    zona TEXT,
                    fecha_pedido TEXT
                );
            """)
            conn.commit()
            cursor.execute("SELECT COUNT(*) FROM ventas_olap")
            count = cursor.fetchone()[0]
            if count == 0:
                self._cargar_semilla_analitica()
                self._cargar_semilla_formatos_empaque()

    def _cargar_semilla_analitica(self):
        conn = self.get_connection()
        seed_data = [
            (101, 1, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 24, 4500.0, 108000.0, 'Norte', '2026-11-01 10:00:00'),
            (101, 1, 1, 11, 'Papas Fritas 150g', 'Snacks', 30, 2500.0, 75000.0, 'Norte', '2026-11-01 10:00:00'),
            (102, 2, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 18, 4800.0, 86400.0, 'Centro', '2026-11-02 11:30:00'),
            (102, 2, 1, 12, 'Galletas Chocolate', 'Snacks', 25, 1800.0, 45000.0, 'Centro', '2026-11-02 11:30:00'),
            (103, 3, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 36, 4200.0, 151200.0, 'Sur', '2026-11-03 09:15:00'),
            (103, 3, 1, 11, 'Papas Fritas 150g', 'Snacks', 40, 2400.0, 96000.0, 'Sur', '2026-11-03 09:15:00'),
            (104, 1, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 12, 5000.0, 60000.0, 'Norte', '2026-11-04 14:00:00'),
            (104, 1, 1, 12, 'Galletas Chocolate', 'Snacks', 20, 1900.0, 38000.0, 'Norte', '2026-11-04 14:00:00'),
            (105, 4, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 48, 4000.0, 192000.0, 'Occidente', '2026-11-05 16:20:00'),
            (105, 4, 1, 11, 'Papas Fritas 150g', 'Snacks', 50, 2300.0, 115000.0, 'Occidente', '2026-11-05 16:20:00')
        ]
        if self.is_duckdb:
            conn.executemany("INSERT INTO ventas_olap VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", seed_data)
            self.exportar_a_parquet()
        else:
            cursor = conn.cursor()
            cursor.executemany("INSERT INTO ventas_olap VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", seed_data)
            conn.commit()

    def _cargar_semilla_formatos_empaque(self):
        """
        HU-31: pedidos de Postobón (proveedor 2) para la minería de canasta de formatos de empaque.
        - Sur: los tenderos piden siempre Postobón Manzana 400ml y nunca la 2.5L, que sí se vende en Occidente
          (rechazo sistemático -> se prescribe un formato más accesible).
        - Centro: Agua Cristal se compra tanto en 600ml como en 5L (sin patrón -> no se prescribe nada).
        """
        conn = self.get_connection()
        seed_data = [
            (201, 31, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 24, 2000.0, 48000.0, 'Sur', '2026-11-09 08:30:00'),
            (202, 32, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 30, 2000.0, 60000.0, 'Sur', '2026-11-10 09:10:00'),
            (203, 33, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 18, 2100.0, 37800.0, 'Sur', '2026-11-11 10:00:00'),
            (203, 33, 2, 22, 'Agua Cristal 600ml', 'Bebidas', 24, 1500.0, 36000.0, 'Sur', '2026-11-11 10:00:00'),
            (204, 31, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 24, 2000.0, 48000.0, 'Sur', '2026-11-12 08:45:00'),
            (205, 32, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 36, 1900.0, 68400.0, 'Sur', '2026-11-13 09:30:00'),
            (206, 34, 2, 21, 'Postobón Manzana 2.5L', 'Bebidas', 6, 7500.0, 45000.0, 'Occidente', '2026-11-09 11:00:00'),
            (207, 34, 2, 21, 'Postobón Manzana 2.5L', 'Bebidas', 8, 7400.0, 59200.0, 'Occidente', '2026-11-12 11:20:00'),
            (207, 34, 2, 23, 'Agua Cristal 5L', 'Bebidas', 4, 6500.0, 26000.0, 'Occidente', '2026-11-12 11:20:00'),
            (208, 35, 2, 22, 'Agua Cristal 600ml', 'Bebidas', 24, 1500.0, 36000.0, 'Centro', '2026-11-09 15:00:00'),
            (208, 35, 2, 20, 'Postobón Manzana 400ml', 'Bebidas', 12, 2000.0, 24000.0, 'Centro', '2026-11-09 15:00:00'),
            (209, 36, 2, 22, 'Agua Cristal 600ml', 'Bebidas', 30, 1450.0, 43500.0, 'Centro', '2026-11-10 15:30:00'),
            (210, 35, 2, 23, 'Agua Cristal 5L', 'Bebidas', 5, 6500.0, 32500.0, 'Centro', '2026-11-11 16:00:00'),
            (211, 36, 2, 23, 'Agua Cristal 5L', 'Bebidas', 6, 6400.0, 38400.0, 'Centro', '2026-11-12 16:10:00')
        ]
        if self.is_duckdb:
            conn.executemany("INSERT INTO ventas_olap VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", seed_data)
            self.exportar_a_parquet()
        else:
            cursor = conn.cursor()
            cursor.executemany("INSERT INTO ventas_olap VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", seed_data)
            conn.commit()

    def exportar_a_parquet(self, output_dir: str = PARQUET_DIR):
        """Exporta la tabla OLAP a particiones Parquet para lectura columnar acelerada (SCRUM-41)"""
        conn = self.get_connection()
        os.makedirs(output_dir, exist_ok=True)
        parquet_file = os.path.join(output_dir, "ventas_consolidadas.parquet")
        if self.is_duckdb:
            conn.execute(f"COPY ventas_olap TO '{parquet_file}' (FORMAT PARQUET);")
        else:
            df = pd.read_sql("SELECT * FROM ventas_olap", conn)
            df.to_csv(parquet_file, index=False)
        return parquet_file

    def query_df(self, sql: str, params: list = None) -> pd.DataFrame:
        """Ejecuta una consulta SQL y retorna un DataFrame de Pandas de manera agnóstica al motor."""
        conn = self.get_connection()
        if params:
            return pd.read_sql(sql, conn, params=params)
        return pd.read_sql(sql, conn)

    def consultar_parquet(self, parquet_file: str = None) -> List[Dict[str, Any]]:
        """Lee directamente desde archivos Parquet o almacenamiento columnar (SCRUM-41)"""
        conn = self.get_connection()
        if self.is_duckdb:
            if parquet_file is None:
                parquet_file = os.path.join(PARQUET_DIR, "ventas_consolidadas.parquet")
            if not os.path.exists(parquet_file):
                self.exportar_a_parquet()
            df = conn.execute(f"SELECT * FROM read_parquet('{parquet_file}')").df()
            return df.to_dict(orient="records")
        else:
            df = pd.read_sql("SELECT * FROM ventas_olap", conn)
            return df.to_dict(orient="records")

db = AnalyticsDatabase()
