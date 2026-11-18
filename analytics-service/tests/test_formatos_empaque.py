"""
HU-31 – Minería de canasta: prescripción de formatos de empaque (FP-Growth).
Pruebas de funciones puras (no dependen de la base de datos).
"""

import os
import random
import sys
import unittest
from itertools import combinations

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from models.fpgrowth import fp_growth, regla
from models.formatos_empaque import interpretar_presentacion, etiqueta_tamano, prescribir_formatos_empaque

M4, M25 = "Postobón Manzana 400ml", "Postobón Manzana 2.5L"
A6, A5 = "Agua Cristal 600ml", "Agua Cristal 5L"


def venta(pedido, nombre, precio, zona):
    return {"pedido_id": pedido, "nombre_producto": nombre, "precio_unitario": precio, "zona": zona}


def ventas_demo():
    """Mismo escenario que la semilla de database.py (proveedor 2)."""
    return [
        venta(201, M4, 2000, "Sur"), venta(202, M4, 2000, "Sur"), venta(203, M4, 2100, "Sur"),
        venta(203, A6, 1500, "Sur"), venta(204, M4, 2000, "Sur"), venta(205, M4, 1900, "Sur"),
        venta(206, M25, 7500, "Occidente"), venta(207, M25, 7400, "Occidente"), venta(207, A5, 6500, "Occidente"),
        venta(208, A6, 1500, "Centro"), venta(208, M4, 2000, "Centro"), venta(209, A6, 1450, "Centro"),
        venta(210, A5, 6500, "Centro"), venta(211, A5, 6400, "Centro"),
    ]


class TestFpGrowth(unittest.TestCase):

    def test_coincide_con_fuerza_bruta(self):
        """FP-Growth debe encontrar exactamente los mismos conjuntos frecuentes que contar todas las combinaciones."""
        azar = random.Random(31)
        items = list("ABCDEFG")
        transacciones = [azar.sample(items, azar.randint(1, 5)) for _ in range(60)]
        for minimo in (3, 8, 15):
            esperado = {}
            for t in transacciones:
                for k in range(1, len(t) + 1):
                    for combo in combinations(sorted(t), k):
                        esperado[frozenset(combo)] = esperado.get(frozenset(combo), 0) + 1
            esperado = {k: v for k, v in esperado.items() if v >= minimo}
            self.assertEqual(fp_growth(transacciones, minimo), esperado, f"soporte mínimo {minimo}")

    def test_regla_de_asociacion(self):
        transacciones = [["pan", "leche"], ["pan", "leche"], ["pan"], ["leche"]]
        itemsets = fp_growth(transacciones, 1)
        r = regla(itemsets, 4, "pan", "leche")
        self.assertEqual(r["soporte"], 0.5)
        self.assertAlmostEqual(r["confianza"], 0.667, places=3)
        self.assertIsNone(regla(fp_growth(transacciones, 3), 4, "pan", "leche"))  # par no frecuente: sin regla

    def test_soporte_minimo_invalido(self):
        with self.assertRaises(ValueError):
            fp_growth([["a"]], 0)


class TestPresentaciones(unittest.TestCase):

    def test_interpretar_tamanos(self):
        self.assertEqual(interpretar_presentacion("Postobón Manzana 2.5L"),
                         {"referencia": "Postobón Manzana", "tamano": 2500.0, "unidad": "ml"})
        self.assertEqual(interpretar_presentacion("Papas Fritas 150g")["tamano"], 150.0)
        self.assertEqual(interpretar_presentacion("Arroz 1,5kg")["tamano"], 1500.0)
        self.assertIsNone(interpretar_presentacion("Galletas Chocolate"))

    def test_etiquetas(self):
        self.assertEqual(etiqueta_tamano(2500, "ml"), "2.5L")
        self.assertEqual(etiqueta_tamano(400, "ml"), "400ml")
        self.assertEqual(etiqueta_tamano(1000, "g"), "1kg")
        self.assertEqual(etiqueta_tamano(1250, "ml"), "1.25L")  # no debe redondear a "1.2L"


class TestFormatosEmpaqueHU31(unittest.TestCase):

    def test_criterio1_rechazo_sistematico_prescribe_formatos_accesibles(self):
        r = prescribir_formatos_empaque(ventas_demo())

        self.assertEqual(r["algoritmo"], "FP-Growth")
        self.assertEqual(len(r["prescripciones"]), 1)
        p = r["prescripciones"][0]
        self.assertEqual((p["referencia"], p["zona"]), ("Postobón Manzana", "Sur"))
        self.assertEqual(p["presentacion_aceptada"]["nombre"], M4)
        self.assertEqual(p["presentacion_rechazada"]["nombre"], M25)
        self.assertEqual(p["presentacion_rechazada"]["zonas_donde_si_se_vende"], ["Occidente"])
        self.assertEqual(p["regla_aceptacion"]["confianza"], 1.0)
        self.assertEqual(p["confianza_presentacion_grande"], 0.0)

        tipos = [f["tipo"] for f in p["formatos_sugeridos"]]
        self.assertEqual(tipos, ["EMPAQUE_FRACCIONADO", "FORMATO_INSTITUCIONAL"])
        fraccionado, institucional = p["formatos_sugeridos"]
        self.assertEqual(fraccionado["nombre"], "Postobón Manzana 1.25L")
        self.assertLess(fraccionado["precio_estimado"], p["presentacion_rechazada"]["precio_promedio"])
        # Ambos formatos son más accesibles que la presentación rechazada
        self.assertEqual(institucional["unidades"], 6)  # 6 x 400ml = 2.4L, sin superar los 2.5L
        self.assertLessEqual(institucional["precio_estimado"], p["presentacion_rechazada"]["precio_promedio"])
        self.assertLess(institucional["precio_por_unidad"], p["presentacion_aceptada"]["precio_promedio"])
        self.assertIsNone(r["mensaje"])

    def test_criterio2_compras_en_ambas_presentaciones_no_prescriben(self):
        r = prescribir_formatos_empaque(ventas_demo(), zona="Centro")
        self.assertEqual(r["prescripciones"], [])
        motivos = {(x["referencia"], x["motivo"].split(" ")[0]) for x in r["referencias_sin_patron"]}
        self.assertIn(("Agua Cristal", "No"), motivos)       # sin regla con confianza hacia la presentación pequeña
        self.assertIn(("Postobón Manzana", "La"), motivos)   # soporte por debajo del mínimo
        self.assertIn("no se emite ninguna prescripción", r["mensaje"])

    def test_criterio2_pocos_pedidos_no_son_evidencia(self):
        ventas = [v for v in ventas_demo() if v["zona"] == "Sur" and v["pedido_id"] <= 202]
        ventas += [venta(206, M25, 7500, "Occidente")]
        r = prescribir_formatos_empaque(ventas)
        self.assertEqual(r["prescripciones"], [])
        self.assertTrue(any("evidencia suficiente" in x["motivo"] for x in r["referencias_sin_patron"]))

    def test_criterio2_si_la_grande_se_compra_no_hay_rechazo(self):
        ventas = ventas_demo() + [venta(204, M25, 7500, "Sur"), venta(205, M25, 7500, "Sur")]
        r = prescribir_formatos_empaque(ventas, zona="Sur")
        self.assertEqual(r["prescripciones"], [])
        self.assertTrue(any("sí se compra" in x["motivo"] for x in r["referencias_sin_patron"]))

    def test_criterio2_si_la_grande_no_se_vende_en_otra_zona_no_se_afirma_rechazo(self):
        # 11 pedidos en Sur con la 400ml y uno solo con la 2.5L (9%): casi nadie la pide, pero como no se vende en
        # ninguna otra zona no hay prueba de que esté disponible, así que no se afirma un rechazo.
        ventas = [venta(300 + i, M4, 2000, "Sur") for i in range(11)] + [venta(300, M25, 7500, "Sur")]
        r = prescribir_formatos_empaque(ventas)
        self.assertEqual(r["prescripciones"], [])
        self.assertTrue(any("ninguna otra zona" in x["motivo"] for x in r["referencias_sin_patron"]))

    def test_sin_referencias_en_varias_presentaciones(self):
        r = prescribir_formatos_empaque([venta(1, "Galletas Chocolate", 1800, "Norte"),
                                         venta(2, "Coca-Cola 1.5L", 4500, "Norte")])
        self.assertFalse(r["suficientes_datos"])
        self.assertEqual(r["prescripciones"], [])


if __name__ == "__main__":
    unittest.main()
