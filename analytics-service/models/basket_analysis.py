"""
Minería de Canasta de Mercado (Market Basket Analysis / FP-Growth) y Prescripción de Formatos de Empaque.
(HU-31 / SCRUM-44).
"""

from typing import List, Dict, Any
from collections import defaultdict
from itertools import combinations

def calcular_reglas_asociacion_y_empaques(
    transacciones: List[List[str]],
    soporte_minimo: float = 0.20,
    confianza_minima: float = 0.40
) -> Dict[str, Any]:
    """
    Ejecuta minería de canasta de mercado sobre transacciones de pedidos de tenderos
    para prescribir nuevos formatos de empaque y combos de alta rotación.
    """
    n_transacciones = len(transacciones)
    if n_transacciones == 0:
        return {"error": "No hay transacciones suficientes para el análisis de canasta."}

    # 1. Contar frecuencias de items individuales
    item_counts = defaultdict(int)
    for t in transacciones:
        for item in set(t):
            item_counts[item] += 1

    # Filtrar items frecuentes por soporte mínimo
    items_frecuentes = {
        item: count / n_transacciones
        for item, count in item_counts.items()
        if (count / n_transacciones) >= soporte_minimo
    }

    # 2. Contar pares frecuentes
    pair_counts = defaultdict(int)
    for t in transacciones:
        unique_items = sorted(list(set(t)))
        for pair in combinations(unique_items, 2):
            pair_counts[pair] += 1

    reglas_asociacion = []
    prescripcion_empaques = []

    for (item_a, item_b), count in pair_counts.items():
        soporte_par = count / n_transacciones
        if soporte_par >= soporte_minimo:
            soporte_a = items_frecuentes.get(item_a, 0.0)
            soporte_b = items_frecuentes.get(item_b, 0.0)

            if soporte_a > 0:
                confianza_a_b = soporte_par / soporte_a
                lift_a_b = confianza_a_b / soporte_b if soporte_b > 0 else 1.0

                if confianza_a_b >= confianza_minima and lift_a_b > 1.0:
                    reglas_asociacion.append({
                        "antecedente": item_a,
                        "consecuente": item_b,
                        "soporte": round(soporte_par, 3),
                        "confianza": round(confianza_a_b, 3),
                        "lift": round(lift_a_b, 3)
                    })

                    prescripcion_empaques.append({
                        "tipo_formato": "Combo Promocional Cruzado",
                        "nombre_empaque": f"Pack Dúo: {item_a} + {item_b}",
                        "productos_incluidos": [item_a, item_b],
                        "afinidad_lift": round(lift_a_b, 2),
                        "confianza_compra_pct": round(confianza_a_b * 100.0, 1),
                        "justificacion": f"Los tenderos que piden {item_a} tienen un {confianza_a_b * 100.0:.1f}% de probabilidad de incluir {item_b} (Afinidad {lift_a_b:.2f}x sobre el azar)."
                    })

    # Si no hubo reglas de pares con umbrales estrictos, generar empaque por item de máxima frecuencia
    if not prescripcion_empaques and items_frecuentes:
        top_item = max(items_frecuentes.items(), key=lambda x: x[1])
        prescripcion_empaques.append({
            "tipo_formato": "Pack Multipack / Six-Pack",
            "nombre_empaque": f"Six-Pack Económico: {top_item[0]}",
            "productos_incluidos": [top_item[0]],
            "afinidad_lift": 1.5,
            "confianza_compra_pct": round(top_item[1] * 100.0, 1),
            "justificacion": f"Producto estrella con {top_item[1] * 100.0:.1f}% de penetración en pedidos. Ideal para venta en empaque agrupado."
        })

    return {
        "total_transacciones_analizadas": n_transacciones,
        "items_frecuentes_detectados": len(items_frecuentes),
        "total_reglas_asociacion": len(reglas_asociacion),
        "reglas_asociacion": reglas_asociacion,
        "formatos_empaque_prescritos": prescripcion_empaques
    }
