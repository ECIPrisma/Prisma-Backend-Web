"""
Modelo Econométrico de Elasticidad Precio-Demanda y Prescripción de Descuentos por Escala.
(HU-30 / SCRUM-43).
"""

import numpy as np
from typing import Dict, Any, List

def calcular_elasticidad_y_prescribir_descuento(
    precios: List[float],
    cantidades: List[float],
    costo_unitario: float = 2000.0,
    zona: str = "General"
) -> Dict[str, Any]:
    """
    Calcula la elasticidad precio de la demanda usando regresión logarítmica (log-log OLS):
    ln(Q) = alpha + beta * ln(P)
    donde beta es el coeficiente de elasticidad.
    """
    if len(precios) < 2 or len(cantidades) < 2:
        return {
            "error": "Se requieren al menos 2 puntos históricos de precio-cantidad para calcular elasticidad."
        }

    ln_p = np.log(np.array(precios, dtype=float))
    ln_q = np.log(np.array(cantidades, dtype=float))

    # Regresión lineal log-log (ajuste de mínimos cuadrados)
    coef = np.polyfit(ln_p, ln_q, deg=1)
    beta = float(coef[0]) # Elasticidad precio de la demanda
    alpha = float(coef[1])

    # Clasificación de elasticidad
    if beta < -1.0:
        tipo_demanda = "Elástica"
        recomendacion = "Un descuento por escala aumentará significativamente el volumen e incrementará los ingresos netos."
        descuento_sugerido_pct = 8.5 # Porcentaje sugerido
    elif -1.0 <= beta < 0:
        tipo_demanda = "Inelástica"
        recomendacion = "La demanda responde poco al precio. Mantener precios o aplicar descuentos moderados solo por volumen muy alto."
        descuento_sugerido_pct = 3.0
    else:
        tipo_demanda = "Atípica / Rígida"
        recomendacion = "Patrón inusual de demanda. Analizar factores de estacionalidad o exclusividad."
        descuento_sugerido_pct = 0.0

    precio_actual = float(np.mean(precios))
    precio_con_descuento = precio_actual * (1.0 - (descuento_sugerido_pct / 100.0))
    volumen_estimado_actual = float(np.exp(alpha + beta * np.log(precio_actual)))
    volumen_estimado_con_descuento = float(np.exp(alpha + beta * np.log(precio_con_descuento)))
    variacion_volumen_pct = ((volumen_estimado_con_descuento - volumen_estimado_actual) / volumen_estimado_actual) * 100.0

    # Margen e ingreso proyectado
    ingreso_actual = volumen_estimado_actual * precio_actual
    ingreso_proyectado = volumen_estimado_con_descuento * precio_con_descuento
    margen_actual = volumen_estimado_actual * (precio_actual - costo_unitario)
    margen_proyectado = volumen_estimado_con_descuento * (precio_con_descuento - costo_unitario)

    escalas_prescritas = [
        {
            "escala_unidades": ">= 24 unidades",
            "descuento_porcentaje": round(descuento_sugerido_pct * 0.7, 1),
            "precio_unitario_final": round(precio_actual * (1.0 - (descuento_sugerido_pct * 0.007)), 2)
        },
        {
            "escala_unidades": ">= 48 unidades",
            "descuento_porcentaje": round(descuento_sugerido_pct, 1),
            "precio_unitario_final": round(precio_con_descuento, 2)
        },
        {
            "escala_unidades": ">= 96 unidades",
            "descuento_porcentaje": round(descuento_sugerido_pct * 1.3, 1),
            "precio_unitario_final": round(precio_actual * (1.0 - (descuento_sugerido_pct * 0.013)), 2)
        }
    ]

    return {
        "zona": zona,
        "coeficiente_elasticidad": round(beta, 3),
        "tipo_demanda": tipo_demanda,
        "descuento_optimo_recomendado_pct": round(descuento_sugerido_pct, 2),
        "precio_base_promedio": round(precio_actual, 2),
        "precio_con_descuento_sugerido": round(precio_con_descuento, 2),
        "incremento_volumen_esperado_pct": round(variacion_volumen_pct, 2),
        "ingreso_actual_proyectado": round(ingreso_actual, 2),
        "ingreso_con_descuento_proyectado": round(ingreso_proyectado, 2),
        "margen_ganancia_proyectado": round(margen_proyectado, 2),
        "recomendacion_estrategica": recomendacion,
        "escalas_prescritas": escalas_prescritas
    }
