"""
Pruebas de aceptación HU-30 (SCRUM-43): Elasticidad precio-demanda y prescripción de descuentos por escala.

Criterio 1: demanda elástica -> prescribe descuento por escala ("Por la compra de 10 unidades, 1 unidad bonificada").
Criterio 2: demanda inelástica -> bloquea el descuento directo y prescribe exhibidores o paquetes cruzados (bundles).
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from models.elasticity import (
    calcular_elasticidad_y_prescribir_descuento,
    describir_oferta,
    seleccionar_serie_por_zona,
    productos_afines,
)

# Serie elástica (eps ~ -4.5): a mayor precio, la cantidad cae mucho más que proporcionalmente
PRECIOS_ELASTICA = [4000.0, 4500.0, 4800.0, 5200.0]
CANTIDADES_ELASTICA = [50.0, 35.0, 25.0, 15.0]

# Serie inelástica (eps ~ -0.58): canasta básica, el volumen casi no cambia con el precio
PRECIOS_INELASTICA = [2800.0, 3000.0, 3200.0]
CANTIDADES_INELASTICA = [40.0, 39.0, 37.0]


class TestElasticidadHU30(unittest.TestCase):

    # ------------------------------------------------------------ Criterio 1: demanda elástica

    def test_criterio1_elastica_prescribe_descuento_por_escala_bonificado(self):
        r = calcular_elasticidad_y_prescribir_descuento(
            PRECIOS_ELASTICA, CANTIDADES_ELASTICA, costo_unitario=2000.0, zona="Norte", nombre_producto="Coca-Cola 1.5L")

        self.assertEqual(r["tipo_demanda"], "Elástica")
        self.assertLess(r["coeficiente_elasticidad"], -1.0)
        self.assertFalse(r["descuento_bloqueado"])
        self.assertGreater(len(r["escalas_prescritas"]), 0)

        descripciones = [e["descripcion"] for e in r["escalas_prescritas"]]
        self.assertIn("Por la compra de 10 unidades, 1 unidad bonificada", descripciones)
        # Solo se prescriben ofertas que no sacrifican el margen del proveedor
        for escala in r["escalas_prescritas"]:
            self.assertTrue(escala["protege_margen"])
            self.assertGreaterEqual(escala["margen_proyectado"], r["margen_actual_proyectado"])
            self.assertGreater(escala["incremento_volumen_pct"], 0)
        self.assertGreater(r["descuento_optimo_recomendado_pct"], 0)
        self.assertEqual(r["alternativas_prescritas"], [])
        self.assertEqual(r["regresion"]["metodo"], "OLS log-log")

    def test_criterio1_regresion_log_log_recupera_la_elasticidad_real(self):
        # Demanda generada con Q = 1e9 * P^-2.5: la regresión log-log debe devolver exactamente -2.5
        precios = [1000.0, 1500.0, 2000.0, 2500.0]
        cantidades = [1e9 * p ** -2.5 for p in precios]
        r = calcular_elasticidad_y_prescribir_descuento(precios, cantidades, costo_unitario=500.0)
        self.assertAlmostEqual(r["coeficiente_elasticidad"], -2.5, places=3)
        self.assertAlmostEqual(r["regresion"]["r2"], 1.0, places=4)

    # ------------------------------------------------------------ Criterio 2: demanda inelástica

    def test_criterio2_inelastica_bloquea_descuento_y_prescribe_exhibidor_y_bundles(self):
        r = calcular_elasticidad_y_prescribir_descuento(
            PRECIOS_INELASTICA, CANTIDADES_INELASTICA, costo_unitario=1500.0, zona="Norte",
            nombre_producto="Arroz Blanco 500g", productos_relacionados=["Aceite Vegetal 1L", "Papas Fritas 150g"])

        self.assertEqual(r["tipo_demanda"], "Inelástica")
        self.assertGreaterEqual(r["coeficiente_elasticidad"], -1.0)
        self.assertTrue(r["descuento_bloqueado"])
        self.assertIn("inelástica", r["motivo_bloqueo"])
        # Sin descuentos directos: el precio y el margen se mantienen
        self.assertEqual(r["escalas_prescritas"], [])
        self.assertEqual(r["descuento_optimo_recomendado_pct"], 0.0)
        self.assertEqual(r["precio_con_descuento_sugerido"], r["precio_base_promedio"])
        self.assertEqual(r["margen_ganancia_proyectado"], r["margen_actual_proyectado"])

        tipos = [a["tipo"] for a in r["alternativas_prescritas"]]
        self.assertIn("EXHIBIDOR", tipos)
        self.assertIn("BUNDLE", tipos)
        bundle = next(a for a in r["alternativas_prescritas"] if a["tipo"] == "BUNDLE")
        self.assertEqual(bundle["productos"], ["Arroz Blanco 500g", "Aceite Vegetal 1L"])

    def test_criterio2_sin_productos_relacionados_igual_prescribe_bundle_generico(self):
        r = calcular_elasticidad_y_prescribir_descuento(PRECIOS_INELASTICA, CANTIDADES_INELASTICA, costo_unitario=1500.0)
        self.assertTrue(r["descuento_bloqueado"])
        self.assertEqual([a["tipo"] for a in r["alternativas_prescritas"]], ["EXHIBIDOR", "BUNDLE"])

    def test_demanda_atipica_tambien_bloquea_descuento(self):
        # La cantidad SUBE con el precio (eps > 0): un descuento no aumentaría el volumen
        r = calcular_elasticidad_y_prescribir_descuento([1000.0, 1100.0, 1200.0], [80.0, 85.0, 90.0], costo_unitario=500.0)
        self.assertEqual(r["tipo_demanda"], "Atípica / Rígida")
        self.assertTrue(r["descuento_bloqueado"])
        self.assertEqual(r["escalas_prescritas"], [])

    def test_elastica_pero_sin_margen_bloquea_descuento(self):
        # Elástica leve (eps ~ -1.09) con un costo del 90%: ninguna escala protege el margen
        r = calcular_elasticidad_y_prescribir_descuento([1000.0, 1100.0, 1200.0], [100.0, 90.0, 82.0], costo_unitario=900.0)
        self.assertEqual(r["tipo_demanda"], "Elástica")
        self.assertTrue(r["descuento_bloqueado"])
        self.assertIn("margen", r["motivo_bloqueo"])
        self.assertGreater(len(r["alternativas_prescritas"]), 0)

    # ------------------------------------------------------------ Validaciones y datos por zona

    def test_datos_insuficientes_o_invalidos(self):
        self.assertIn("error", calcular_elasticidad_y_prescribir_descuento([4000.0], [10.0]))
        self.assertIn("error", calcular_elasticidad_y_prescribir_descuento([4000.0, 4000.0], [10.0, 12.0]))
        self.assertIn("error", calcular_elasticidad_y_prescribir_descuento([4000.0, 0.0], [10.0, 12.0]))

    def test_texto_de_la_oferta(self):
        self.assertEqual(describir_oferta(10, 1), "Por la compra de 10 unidades, 1 unidad bonificada")
        self.assertEqual(describir_oferta(20, 3), "Por la compra de 20 unidades, 3 unidades bonificadas")

    def test_usa_los_datos_de_la_zona_y_si_no_alcanzan_todas_las_zonas(self):
        filas = [
            {"zona": "Norte", "precio_unitario": 4500.0, "cantidad": 24},
            {"zona": "norte", "precio_unitario": 5000.0, "cantidad": 12},
            {"zona": "Sur", "precio_unitario": 4200.0, "cantidad": 36},
        ]
        precios, cantidades, alcance = seleccionar_serie_por_zona(filas, "Norte")
        self.assertEqual(alcance, "ZONA")
        self.assertEqual(sorted(precios), [4500.0, 5000.0])

        precios, _, alcance = seleccionar_serie_por_zona(filas, "Sur")  # un solo precio en Sur
        self.assertEqual(alcance, "TODAS_LAS_ZONAS")
        self.assertEqual(len(precios), 3)

    def test_productos_afines_priorizan_la_zona(self):
        filas = [
            {"pedido_id": 1, "producto_id": 13, "nombre_producto": "Arroz", "zona": "Norte"},
            {"pedido_id": 1, "producto_id": 14, "nombre_producto": "Aceite", "zona": "Norte"},
            {"pedido_id": 2, "producto_id": 13, "nombre_producto": "Arroz", "zona": "Norte"},
            {"pedido_id": 2, "producto_id": 14, "nombre_producto": "Aceite", "zona": "Norte"},
            {"pedido_id": 2, "producto_id": 11, "nombre_producto": "Papas", "zona": "Norte"},
            {"pedido_id": 3, "producto_id": 13, "nombre_producto": "Arroz", "zona": "Sur"},
            {"pedido_id": 3, "producto_id": 12, "nombre_producto": "Galletas", "zona": "Sur"},
        ]
        self.assertEqual(productos_afines(filas, 13, "Norte"), ["Aceite", "Papas"])
        self.assertEqual(productos_afines(filas, 13, "Sur"), ["Galletas"])
        # Zona sin pedidos del producto: usa todas las zonas
        self.assertEqual(productos_afines(filas, 13, "Centro")[0], "Aceite")


if __name__ == "__main__":
    unittest.main()
