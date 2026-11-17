"""
Suite de Pruebas Automatizadas de Aceptación para el Motor Analítico Modo Pro.
Cubre HU-27 (SCRUM-39), HU-29 (SCRUM-41), HU-30 (SCRUM-43) y HU-31 (SCRUM-44).
"""

import sys
import os
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from database import db
from models.elasticity import calcular_elasticidad_y_prescribir_descuento
from models.basket_analysis import calcular_reglas_asociacion_y_empaques

class TestAnalyticsEngine(unittest.TestCase):

    def test_scrum_39_aislamiento_y_memoria(self):
        """
        HU-27 (SCRUM-39): Criterio de aislamiento y bajo consumo de recursos.
        """
        import psutil
        process = psutil.Process(os.getpid())
        mem_mb = process.memory_info().rss / (1024 * 1024)
        
        # Debe mantenerse muy por debajo del límite de 300 MB
        self.assertLess(mem_mb, 300.0, f"El consumo de memoria ({mem_mb:.2f} MB) debe ser inferior a 300 MB")

    def test_scrum_41_pipeline_olap_parquet_sub_segundo(self):
        """
        HU-29 (SCRUM-41): Pipeline híbrido OLTP/OLAP.
        Verifica exportación a Parquet y consulta analítica en DuckDB sub-segundo.
        """
        import time
        start_time = time.time()
        
        parquet_path = db.exportar_a_parquet()
        self.assertTrue(os.path.exists(parquet_path), "El archivo Parquet debe haberse generado exitosamente")
        
        registros = db.consultar_parquet(parquet_path)
        duracion_ms = (time.time() - start_time) * 1000.0
        
        self.assertGreater(len(registros), 0, "DuckDB debe leer registros desde las particiones Parquet")
        self.assertLess(duracion_ms, 1000.0, f"La consulta analítica ({duracion_ms:.2f} ms) debe ser sub-segundo (<1000 ms)")

    def test_scrum_43_elasticidad_precio_demanda(self):
        """
        HU-30 (SCRUM-43): Elasticidad precio-demanda y prescripción de descuentos.
        """
        # Datos históricos: A mayor precio, menor cantidad (demanda elástica esperada)
        precios = [4000.0, 4500.0, 4800.0, 5200.0]
        cantidades = [50.0, 35.0, 25.0, 15.0]

        resultado = calcular_elasticidad_y_prescribir_descuento(precios, cantidades, costo_unitario=2000.0, zona="Norte")

        # Coeficiente de elasticidad debe ser negativo
        self.assertLess(resultado["coeficiente_elasticidad"], 0, "La elasticidad precio debe ser negativa")
        self.assertEqual(resultado["tipo_demanda"], "Elástica")
        self.assertGreater(resultado["descuento_optimo_recomendado_pct"], 0)
        self.assertGreater(resultado["incremento_volumen_esperado_pct"], 0)
        self.assertEqual(len(resultado["escalas_prescritas"]), 3, "Debe prescribir 3 escalas de descuento por volumen")

    def test_scrum_44_mineria_canasta_empaques(self):
        """
        HU-31 (SCRUM-44): Minería de canasta de mercado (FP-Growth) y empaques.
        """
        transacciones = [
            ["Coca-Cola 1.5L", "Papas Fritas 150g", "Pan Blanco"],
            ["Coca-Cola 1.5L", "Papas Fritas 150g"],
            ["Coca-Cola 1.5L", "Galletas Chocolate"],
            ["Coca-Cola 1.5L", "Papas Fritas 150g", "Galletas Chocolate"],
            ["Papas Fritas 150g", "Pan Blanco"]
        ]

        resultado = calcular_reglas_asociacion_y_empaques(transacciones, soporte_minimo=0.20, confianza_minima=0.30)

        self.assertGreater(len(resultado["reglas_asociacion"]), 0, "Debe descubrir reglas de asociación")
        self.assertGreater(len(resultado["formatos_empaque_prescritos"]), 0, "Debe prescribir al menos un formato de empaque/combo")
        
        primer_empaque = resultado["formatos_empaque_prescritos"][0]
        self.assertIn("Pack", primer_empaque["nombre_empaque"])
        self.assertGreater(primer_empaque["afinidad_lift"], 1.0, "El Lift de la regla debe ser superior a 1.0")

if __name__ == "__main__":
    unittest.main()
