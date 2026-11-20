"""
Pruebas de aceptación Modo Pro:
  HU-16  Detección de brecha de penetración territorial (alertas)
  HU-17  Recomendación comercial narrativa con IA (+ N-12 respaldo por plantilla a costo $0)
  N-11   Ranking de productos
"""

import os
import sys
import unittest
from unittest import mock

import requests

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from models.brechas import detectar_brechas, agrupar_zonas
from models.narrativa import (
    MENSAJE_NO_DISPONIBLE,
    construir_contexto,
    construir_prompt,
    generar_narrativa_plantilla,
    generar_recomendacion,
    llamar_gemini,
)
from models.ranking import ranking_productos


def venta(pedido, zona, producto, nombre, categoria, cantidad, precio=1000.0):
    return {"pedido_id": pedido, "zona": zona, "producto_id": producto, "nombre_producto": nombre,
            "categoria": categoria, "cantidad": cantidad, "precio_unitario": precio, "total": cantidad * precio}


# Misma información que la semilla OLAP del motor (4 zonas, 3 productos)
SEMILLA = [
    venta(101, "Norte", 10, "Coca-Cola 1.5L", "Bebidas", 24, 4500), venta(101, "Norte", 11, "Papas Fritas 150g", "Snacks", 30, 2500),
    venta(102, "Centro", 10, "Coca-Cola 1.5L", "Bebidas", 18, 4800), venta(102, "Centro", 12, "Galletas Chocolate", "Snacks", 25, 1800),
    venta(103, "Sur", 10, "Coca-Cola 1.5L", "Bebidas", 36, 4200), venta(103, "Sur", 11, "Papas Fritas 150g", "Snacks", 40, 2400),
    venta(104, "Norte", 10, "Coca-Cola 1.5L", "Bebidas", 12, 5000), venta(104, "Norte", 12, "Galletas Chocolate", "Snacks", 20, 1900),
    venta(105, "Occidente", 10, "Coca-Cola 1.5L", "Bebidas", 48, 4000), venta(105, "Occidente", 11, "Papas Fritas 150g", "Snacks", 50, 2300),
]


class TestBrechasHU16(unittest.TestCase):

    def test_criterio1_superventas_en_una_zona_y_nulo_en_zona_similar_genera_alerta(self):
        r = detectar_brechas(SEMILLA)
        self.assertTrue(r["suficientes_datos"])
        # Las 4 zonas tienen una mezcla Bebidas/Snacks muy parecida: un solo clúster comparable
        self.assertEqual(r["clusters"], [["Centro", "Norte", "Occidente", "Sur"]])

        brechas = {(a["nombre_producto"], a["zona_rezagada"]) for a in r["alertas"]}
        self.assertEqual(brechas, {("Papas Fritas 150g", "Centro"),
                                   ("Galletas Chocolate", "Sur"),
                                   ("Galletas Chocolate", "Occidente")})
        papas = next(a for a in r["alertas"] if a["nombre_producto"] == "Papas Fritas 150g")
        self.assertEqual(papas["tipo"], "DESABASTECIMIENTO_ARTIFICIAL")
        self.assertEqual(papas["zona_lider"], "Occidente")  # la zona líder con más unidades
        self.assertEqual(papas["unidades_zona_rezagada"], 0)
        self.assertIn("desabastecimiento artificial", papas["mensaje"])
        self.assertIn("promoción de introducción", papas["prescripcion"])

    def test_criterio2_ventas_homogeneas_no_generan_alerta(self):
        filas = [
            venta(1, "Norte", 10, "Coca-Cola", "Bebidas", 40), venta(1, "Norte", 11, "Papas", "Snacks", 30),
            venta(2, "Sur", 10, "Coca-Cola", "Bebidas", 38), venta(2, "Sur", 11, "Papas", "Snacks", 31),
        ]
        r = detectar_brechas(filas)
        self.assertEqual(r["alertas"], [])
        self.assertTrue(r["suficientes_datos"])

    def test_zonas_no_comparables_no_se_cruzan(self):
        # Norte vende solo bebidas y Sur solo snacks: comportamiento distinto, clústeres separados, sin alertas
        filas = [venta(1, "Norte", 10, "Coca-Cola", "Bebidas", 50), venta(2, "Sur", 11, "Papas", "Snacks", 50)]
        clusters, _ = agrupar_zonas(filas)
        self.assertEqual(clusters, [["Norte"], ["Sur"]])
        self.assertEqual(detectar_brechas(filas)["alertas"], [])

    def test_sin_datos_suficientes(self):
        r = detectar_brechas([venta(1, "Norte", 10, "Coca-Cola", "Bebidas", 10)])
        self.assertFalse(r["suficientes_datos"])
        self.assertEqual(r["mensaje"], "Aún no hay suficientes datos")


class TestRankingN11(unittest.TestCase):

    def test_ranking_ordenado_por_ingresos_con_participacion(self):
        r = ranking_productos(SEMILLA)
        nombres = [p["nombre_producto"] for p in r["productos"]]
        self.assertEqual(nombres[0], "Coca-Cola 1.5L")
        self.assertEqual([p["posicion"] for p in r["productos"]], [1, 2, 3])
        self.assertAlmostEqual(sum(p["participacion_pct"] for p in r["productos"]), 100.0, places=1)
        self.assertEqual(r["productos"][0]["zonas_con_ventas"], ["Centro", "Norte", "Occidente", "Sur"])

    def test_ranking_por_zona_y_estado_vacio(self):
        self.assertEqual({p["nombre_producto"] for p in ranking_productos(SEMILLA, "centro")["productos"]},
                         {"Coca-Cola 1.5L", "Galletas Chocolate"})
        vacio = ranking_productos(SEMILLA, "Oriente")
        self.assertEqual(vacio["productos"], [])
        self.assertEqual(vacio["mensaje"], "Aún no hay suficientes datos")


RESULTADO_ELASTICO = {
    "coeficiente_elasticidad": -6.579, "tipo_demanda": "Elástica", "descuento_bloqueado": False,
    "descuento_optimo_recomendado_pct": 14.9, "incremento_volumen_esperado_pct": 188.9, "precio_base_promedio": 4750.0,
    "escalas_prescritas": [{"descripcion": "Por la compra de 10 unidades, 1 unidad bonificada"}],
}
RESULTADO_INELASTICO = {
    "coeficiente_elasticidad": -0.581, "tipo_demanda": "Inelástica", "descuento_bloqueado": True,
    "descuento_optimo_recomendado_pct": 0.0, "incremento_volumen_esperado_pct": 0.0, "precio_base_promedio": 3000.0,
    "escalas_prescritas": [],
    "alternativas_prescritas": [{"titulo": "Exhibidor en punto de venta"}, {"titulo": "Paquete cruzado: Arroz + Aceite"}],
}


class TestRecomendacionHU17(unittest.TestCase):

    def test_contexto_estructurado_para_el_modelo(self):
        ctx = construir_contexto(RESULTADO_ELASTICO, "Bebidas", "Norte")
        self.assertEqual(ctx["categoria"], "Bebidas")
        self.assertEqual(ctx["zona"], "Norte")
        self.assertFalse(ctx["descuento_bloqueado"])
        self.assertEqual(ctx["escala_recomendada"], "Por la compra de 10 unidades, 1 unidad bonificada")
        # El prompt lleva el contexto como JSON
        self.assertIn('"categoria": "Bebidas"', construir_prompt(ctx))

    def test_contexto_compatible_con_motor_sin_campo_bloqueo(self):
        sin_campo = {k: v for k, v in RESULTADO_INELASTICO.items() if k != "descuento_bloqueado"}
        self.assertTrue(construir_contexto(sin_campo, "Granos", "Norte")["descuento_bloqueado"])

    def test_criterio1_recomendacion_generada_por_el_modelo(self):
        ctx = construir_contexto(RESULTADO_ELASTICO, "Bebidas", "Norte")
        rec = generar_recomendacion(ctx, llamar_llm=lambda c: "Haz una promoción 10+1 en Norte.")
        self.assertEqual(rec["estado"], "GENERADA")
        self.assertEqual(rec["fuente"], "IA_GENERATIVA")
        self.assertEqual(rec["narrativa"], "Haz una promoción 10+1 en Norte.")
        self.assertIsNone(rec["mensaje"])

    def test_criterio2_si_la_ia_excede_el_tiempo_conserva_la_matriz_y_muestra_el_mensaje(self):
        ctx = construir_contexto(RESULTADO_INELASTICO, "Granos", "Norte")

        def ia_caida(_):
            raise requests.Timeout("timeout")

        rec = generar_recomendacion(ctx, llamar_llm=ia_caida)
        self.assertEqual(rec["estado"], "NARRATIVA_NO_DISPONIBLE")
        self.assertEqual(rec["mensaje"], MENSAJE_NO_DISPONIBLE)
        self.assertEqual(rec["mensaje"], "Recomendación narrativa no disponible temporalmente")
        self.assertIsNone(rec["narrativa"])
        self.assertEqual(rec["matriz"], ctx)  # la matriz numérica se conserva intacta
        self.assertEqual(rec["error"], "Timeout")
        self.assertTrue(rec["resumen_plantilla"])  # N-12: respaldo determinista

    def test_n12_sin_llave_de_api_usa_el_respaldo_sin_llamar_a_internet(self):
        ctx = construir_contexto(RESULTADO_ELASTICO, "Bebidas", "Norte")
        with mock.patch.dict(os.environ, {"GEMINI_API_KEY": ""}), mock.patch("requests.post") as post:
            rec = generar_recomendacion(ctx)
        post.assert_not_called()
        self.assertEqual(rec["estado"], "NARRATIVA_NO_DISPONIBLE")

    def test_n12_llamada_a_gemini_con_timeout(self):
        ctx = construir_contexto(RESULTADO_ELASTICO, "Bebidas", "Norte")
        respuesta = mock.Mock()
        respuesta.json.return_value = {"candidates": [{"content": {"parts": [{"text": " Recomendación clara. "}]}}]}
        respuesta.raise_for_status.return_value = None
        with mock.patch("requests.post", return_value=respuesta) as post:
            texto = llamar_gemini(ctx, api_key="llave-de-prueba", modelo="gemini-2.5-flash", timeout=5)
        self.assertEqual(texto, "Recomendación clara.")
        self.assertEqual(post.call_args.kwargs["timeout"], 5)
        # La llave viaja en el encabezado x-goog-api-key, nunca en la URL
        self.assertEqual(post.call_args.kwargs["headers"]["x-goog-api-key"], "llave-de-prueba")
        self.assertNotIn("params", post.call_args.kwargs)

    def test_plantilla_en_lenguaje_claro(self):
        elastica = generar_narrativa_plantilla(construir_contexto(RESULTADO_ELASTICO, "Bebidas", "Norte"))
        self.assertIn("Por la compra de 10 unidades, 1 unidad bonificada", elastica)
        self.assertNotIn("elasticidad", elastica.lower())

        inelastica = generar_narrativa_plantilla(construir_contexto(RESULTADO_INELASTICO, "Granos", "Norte"))
        self.assertIn("reduciría tu margen", inelastica)
        self.assertIn("exhibidor en punto de venta", inelastica)


if __name__ == "__main__":
    unittest.main()
