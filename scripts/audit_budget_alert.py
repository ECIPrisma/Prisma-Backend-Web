#!/usr/bin/env python3
"""
HU-28 (SCRUM-40): Alerta de presupuesto de infraestructura.
Evalúa el consumo acumulado y proyectado frente al techo mensual de $110 USD.
Si supera el 80% ($88 USD), genera una alerta formal para el administrador.
"""

from typing import Dict, Any, Optional

PRESUPUESTO_TECHO_USD = 110.0
UMBRAL_ALERTA_PORCENTAJE = 80.0
UMBRAL_ALERTA_USD = PRESUPUESTO_TECHO_USD * (UMBRAL_ALERTA_PORCENTAJE / 100.0) # $88.0 USD

def evaluar_presupuesto_infraestructura(
    gasto_acumulado_usd: float,
    gasto_proyectado_usd: float,
    admin_email: str = "admin@prisma.com.co"
) -> Dict[str, Any]:
    """
    Evalúa el gasto proyectado y determina si se dispara una alerta de presupuesto.
    """
    porcentaje_consumido = (gasto_proyectado_usd / PRESUPUESTO_TECHO_USD) * 100.0
    debe_alertar = porcentaje_consumido >= UMBRAL_ALERTA_PORCENTAJE

    resultado = {
        "techo_presupuestal_usd": PRESUPUESTO_TECHO_USD,
        "gasto_acumulado_usd": round(gasto_acumulado_usd, 2),
        "gasto_proyectado_usd": round(gasto_proyectado_usd, 2),
        "porcentaje_proyectado": round(porcentaje_consumido, 2),
        "umbral_alerta_porcentaje": UMBRAL_ALERTA_PORCENTAJE,
        "umbral_alerta_usd": UMBRAL_ALERTA_USD,
        "alerta_generada": debe_alertar,
        "destinatario": admin_email,
        "mensaje": None
    }

    if debe_alertar:
        resultado["mensaje"] = (
            f"ALERTA PRESUPUESTAL PRISMA: El consumo proyectado de infraestructura (${gasto_proyectado_usd:.2f} USD) "
            f"ha alcanzado el {porcentaje_consumido:.1f}% del techo mensual (${PRESUPUESTO_TECHO_USD:.2f} USD). "
            f"Umbral de seguridad: {UMBRAL_ALERTA_PORCENTAJE}% (${UMBRAL_ALERTA_USD:.2f} USD). "
            f"Notificación enviada a {admin_email} para toma de decisiones preventiva."
        )
    else:
        resultado["mensaje"] = (
            f"Consumo dentro de rango normal: {porcentaje_consumido:.1f}% del presupuesto proyectado "
            f"(${gasto_proyectado_usd:.2f} / ${PRESUPUESTO_TECHO_USD:.2f} USD). No se requiere alerta."
        )

    return resultado

if __name__ == "__main__":
    import sys
    gasto = float(sys.argv[1]) if len(sys.argv) > 1 else 95.0
    res = evaluar_presupuesto_infraestructura(gasto * 0.7, gasto)
    print(res["mensaje"])
