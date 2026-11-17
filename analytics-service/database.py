"""
Gestor de persistencia analítica OLAP con DuckDB y particiones Parquet.
(HU-27 / SCRUM-39 y HU-29 / SCRUM-41).
"""

import duckdb
import os
import pandas as pd
from typing import List, Dict, Any

DB_PATH = os.environ.get("DUCKDB_PATH", "prisma_analytics.duckdb")
PARQUET_DIR = os.environ.get("PARQUET_DIR", "data/parquet")

class AnalyticsDatabase:
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        self._conn = None
        self.init_database()

    def get_connection(self):
        if self._conn is None:
            self._conn = duckdb.connect(self.db_path)
        return self._conn

    def init_database(self):
        os.makedirs(PARQUET_DIR, exist_ok=True)
        conn = self.get_connection()
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
        # Cargar datos semilla sintéticos si la tabla está vacía
        count = conn.execute("SELECT COUNT(*) FROM ventas_olap").fetchone()[0]
        if count == 0:
            self._cargar_semilla_analitica()

    def _cargar_semilla_analitica(self):
        conn = self.get_connection()
        conn.execute("""
            INSERT INTO ventas_olap VALUES
            (101, 1, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 24, 4500.0, 108000.0, 'Norte', '2026-11-01 10:00:00'),
            (101, 1, 1, 11, 'Papas Fritas 150g', 'Snacks', 30, 2500.0, 75000.0, 'Norte', '2026-11-01 10:00:00'),
            (102, 2, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 18, 4800.0, 86400.0, 'Centro', '2026-11-02 11:30:00'),
            (102, 2, 1, 12, 'Galletas Chocolate', 'Snacks', 25, 1800.0, 45000.0, 'Centro', '2026-11-02 11:30:00'),
            (103, 3, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 36, 4200.0, 151200.0, 'Sur', '2026-11-03 09:15:00'),
            (103, 3, 1, 11, 'Papas Fritas 150g', 'Snacks', 40, 2400.0, 96000.0, 'Sur', '2026-11-03 09:15:00'),
            (104, 1, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 12, 5000.0, 60000.0, 'Norte', '2026-11-04 14:00:00'),
            (104, 1, 1, 12, 'Galletas Chocolate', 'Snacks', 20, 1900.0, 38000.0, 'Norte', '2026-11-04 14:00:00'),
            (105, 4, 1, 10, 'Coca-Cola 1.5L', 'Bebidas', 48, 4000.0, 192000.0, 'Occidente', '2026-11-05 16:20:00'),
            (105, 4, 1, 11, 'Papas Fritas 150g', 'Snacks', 50, 2300.0, 115000.0, 'Occidente', '2026-11-05 16:20:00');
        """)
        self.exportar_a_parquet()

    def exportar_a_parquet(self, output_dir: str = PARQUET_DIR):
        """Exporta la tabla OLAP a particiones Parquet para lectura columnar acelerada (SCRUM-41)"""
        conn = self.get_connection()
        os.makedirs(output_dir, exist_ok=True)
        parquet_file = os.path.join(output_dir, "ventas_consolidadas.parquet")
        conn.execute(f"COPY ventas_olap TO '{parquet_file}' (FORMAT PARQUET);")
        return parquet_file

    def consultar_parquet(self, parquet_file: str = None) -> List[Dict[str, Any]]:
        """Lee directamente desde archivos Parquet usando DuckDB (SCRUM-41)"""
        conn = self.get_connection()
        if parquet_file is None:
            parquet_file = os.path.join(PARQUET_DIR, "ventas_consolidadas.parquet")
        if not os.path.exists(parquet_file):
            self.exportar_a_parquet()
        df = conn.execute(f"SELECT * FROM read_parquet('{parquet_file}')").df()
        return df.to_dict(orient="records")

db = AnalyticsDatabase()
