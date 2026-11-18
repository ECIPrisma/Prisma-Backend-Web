"""
Modelo Econométrico de Elasticidad Precio-Demanda y Prescripción de Descuentos por Escala.
(HU-30 / SCRUM-43).

1. Calcula el coeficiente de elasticidad (epsilon) con regresión log-log por mínimos cuadrados:
       ln(Q) = alpha + epsilon * ln(P)
2. Demanda ELÁSTICA (|epsilon| > 1): prescribe descuentos por escala expresados como unidades bonificadas
   ("Por la compra de 10 unidades, 1 unidad bonificada"), solo los que NO sacrifican el margen del proveedor.
3. Demanda INELÁSTICA (|epsilon| <= 1) o atípica: BLOQUEA el descuento directo para cuidar el margen y prescribe
   activaciones sin tocar el precio: exhibidores en punto de venta y paquetes cruzados (bundles).
"""

import math
from typing import Dict, Any, List, Optional, Tuple

import numpy as np

# Ofertas por escala que evalúa el motor: (unidades compradas, unidades bonificadas).
# Equivalen a un descuento de B / (N + B): 10+1 = 9.1 %, 20+3 = 13.0 %, 40+7 = 14.9 %.
OFERTAS_POR_ESCALA: List[Tuple[int, int]] = [(10, 1), (20, 3), (40, 7)]

# Oferta de referencia para cuantificar el daño al margen cuando la demanda es inelástica.
OFERTA_REFERENCIA: Tuple[int, int] = (10, 1)

MAX_BUNDLES = 2


def describir_oferta(compra: int, bonificadas: int) -> str:
    """'Por la compra de 10 unidades, 1 unidad bonificada'."""
    unidad = "unidad bonificada" if bonificadas == 1 else "unidades bonificadas"
    return f"Por la compra de {compra} unidades, {bonificadas} {unidad}"


def _regresion_log_log(precios: List[float], cantidades: List[float]) -> Tuple[float, float, float]:
    """Devuelve (epsilon, alpha, r2) del ajuste ln(Q) = alpha + epsilon * ln(P)."""
    ln_p = np.log(np.array(precios, dtype=float))
    ln_q = np.log(np.array(cantidades, dtype=float))
    epsilon, alpha = np.polyfit(ln_p, ln_q, deg=1)
    predicho = alpha + epsilon * ln_p
    ss_res = float(np.sum((ln_q - predicho) ** 2))
    ss_tot = float(np.sum((ln_q - np.mean(ln_q)) ** 2))
    r2 = 1.0 - ss_res / ss_tot if ss_tot > 0 else 1.0
    return float(epsilon), float(alpha), r2


def _evaluar_oferta(compra: int, bonificadas: int, precio: float, volumen: float, epsilon: float,
                    costo_unitario: float, margen_actual: float) -> Dict[str, Any]:
    """Proyecta volumen, ingreso y margen de una oferta 'N + B' usando la elasticidad (Q ∝ P^epsilon)."""
    descuento = bonificadas / (compra + bonificadas)
    precio_efectivo = precio * (1.0 - descuento)
    volumen_proyectado = volumen * (1.0 - descuento) ** epsilon
    margen_proyectado = volumen_proyectado * (precio_efectivo - costo_unitario)
    return {
        "escala_unidades": f">= {compra} unidades",
        "compra_unidades": compra,
        "unidades_bonificadas": bonificadas,
        "descripcion": describir_oferta(compra, bonificadas),
        "descuento_porcentaje": round(descuento * 100.0, 1),
        "precio_unitario_final": round(precio_efectivo, 2),
        "incremento_volumen_pct": round((volumen_proyectado / volumen - 1.0) * 100.0, 2),
        "ingreso_proyectado": round(volumen_proyectado * precio_efectivo, 2),
        "margen_proyectado": round(margen_proyectado, 2),
        "variacion_margen_pct": round((margen_proyectado / margen_actual - 1.0) * 100.0, 2) if margen_actual > 0 else None,
        "protege_margen": precio_efectivo > costo_unitario and margen_proyectado >= margen_actual,
        "_volumen": volumen_proyectado,
        "_precio": precio_efectivo,
    }


def _alternativas_sin_descuento(nombre_producto: str, zona: str,
                                productos_relacionados: Optional[List[str]]) -> List[Dict[str, Any]]:
    """Activaciones que no tocan el precio: exhibidor en punto de venta y paquetes cruzados (bundles)."""
    alternativas: List[Dict[str, Any]] = [{
        "tipo": "EXHIBIDOR",
        "titulo": "Exhibidor en punto de venta",
        "descripcion": (f"Instala un exhibidor o cabecera de góndola de {nombre_producto} en las tiendas de la zona "
                        f"{zona} para ganar visibilidad y rotación sin bajar el precio."),
        "productos": [nombre_producto],
    }]
    relacionados = [p for p in (productos_relacionados or []) if p and p != nombre_producto][:MAX_BUNDLES]
    if relacionados:
        for relacionado in relacionados:
            alternativas.append({
                "tipo": "BUNDLE",
                "titulo": f"Paquete cruzado: {nombre_producto} + {relacionado}",
                "descripcion": (f"Los tenderos de la zona {zona} suelen pedir {relacionado} junto con {nombre_producto}. "
                                f"Ofrécelos como paquete manteniendo el precio de {nombre_producto}."),
                "productos": [nombre_producto, relacionado],
            })
    else:
        alternativas.append({
            "tipo": "BUNDLE",
            "titulo": f"Paquete cruzado con {nombre_producto}",
            "descripcion": (f"Arma un paquete de {nombre_producto} con un producto complementario de tu catálogo "
                            f"para mover volumen sin descuento directo."),
            "productos": [nombre_producto],
        })
    return alternativas


def calcular_elasticidad_y_prescribir_descuento(
    precios: List[float],
    cantidades: List[float],
    costo_unitario: float = 2000.0,
    zona: str = "General",
    nombre_producto: str = "este producto",
    productos_relacionados: Optional[List[str]] = None,
) -> Dict[str, Any]:
    """
    Calcula la elasticidad precio de la demanda (regresión log-log OLS) y prescribe la acción comercial.
    """
    if len(precios) < 2 or len(cantidades) < 2 or len(precios) != len(cantidades):
        return {"error": "Se requieren al menos 2 puntos históricos de precio-cantidad para calcular elasticidad."}
    if any(p <= 0 for p in precios) or any(q <= 0 for q in cantidades):
        return {"error": "Los precios y las cantidades deben ser mayores que cero para la regresión log-log."}
    if len(set(round(p, 6) for p in precios)) < 2:
        return {"error": "Se requieren al menos 2 precios distintos para estimar la elasticidad."}

    epsilon, alpha, r2 = _regresion_log_log(precios, cantidades)

    precio_actual = float(np.mean(precios))
    volumen_actual = float(math.exp(alpha + epsilon * math.log(precio_actual)))
    ingreso_actual = volumen_actual * precio_actual
    margen_actual = volumen_actual * (precio_actual - costo_unitario)

    if epsilon < -1.0:
        tipo_demanda = "Elástica"
    elif epsilon < 0:
        tipo_demanda = "Inelástica"
    else:
        tipo_demanda = "Atípica / Rígida"

    ofertas = [_evaluar_oferta(n, b, precio_actual, volumen_actual, epsilon, costo_unitario, margen_actual)
               for n, b in OFERTAS_POR_ESCALA]
    viables = [o for o in ofertas if o["protege_margen"]]

    descuento_bloqueado = tipo_demanda != "Elástica" or not viables
    if not descuento_bloqueado:
        # Criterio 1: demanda elástica -> descuento por escala (solo las ofertas que no sacrifican margen)
        mejor = max(viables, key=lambda o: o["margen_proyectado"])
        motivo_bloqueo = None
        recomendacion = (f"La demanda de {nombre_producto} en la zona {zona} es elástica (ε = {epsilon:.2f}): "
                         f"un descuento por escala aumenta el volumen y el margen. Recomendado: {mejor['descripcion']}.")
        escalas = viables
        alternativas: List[Dict[str, Any]] = []
        precio_sugerido = mejor["_precio"]
        volumen_sugerido = mejor["_volumen"]
        descuento_pct = mejor["descuento_porcentaje"]
    else:
        # Criterio 2: demanda inelástica (o descuento que destruye margen) -> se bloquea el descuento directo
        referencia = _evaluar_oferta(*OFERTA_REFERENCIA, precio_actual, volumen_actual, epsilon, costo_unitario, margen_actual)
        if tipo_demanda == "Elástica":
            motivo_bloqueo = (f"Aunque la demanda es elástica (ε = {epsilon:.2f}), ninguna escala de descuento mantiene el "
                              f"margen con el costo actual: se bloquea el descuento directo.")
        elif tipo_demanda == "Inelástica":
            motivo_bloqueo = (f"La demanda es inelástica (|ε| = {abs(epsilon):.2f} ≤ 1): el volumen casi no responde al "
                              f"precio. Una oferta de {referencia['descripcion'].lower()} cambiaría el margen en "
                              f"{referencia['variacion_margen_pct']}%. Se bloquea el descuento directo para cuidar tu margen.")
        else:
            motivo_bloqueo = (f"Patrón atípico (ε = {epsilon:.2f} ≥ 0): la demanda no baja cuando sube el precio. "
                              f"Un descuento no aumentaría el volumen; se bloquea el descuento directo.")
        recomendacion = (f"No apliques descuentos directos a {nombre_producto} en la zona {zona}. "
                         f"Activa la demanda con exhibidores o paquetes cruzados.")
        escalas = []
        alternativas = _alternativas_sin_descuento(nombre_producto, zona, productos_relacionados)
        precio_sugerido = precio_actual
        volumen_sugerido = volumen_actual
        descuento_pct = 0.0

    ingreso_proyectado = volumen_sugerido * precio_sugerido
    margen_proyectado = volumen_sugerido * (precio_sugerido - costo_unitario)

    for oferta in escalas:
        oferta.pop("_volumen", None)
        oferta.pop("_precio", None)

    return {
        "zona": zona,
        "nombre_producto": nombre_producto,
        "coeficiente_elasticidad": round(epsilon, 3),
        "tipo_demanda": tipo_demanda,
        "descuento_bloqueado": descuento_bloqueado,
        "motivo_bloqueo": motivo_bloqueo,
        "descuento_optimo_recomendado_pct": round(descuento_pct, 2),
        "precio_base_promedio": round(precio_actual, 2),
        "costo_unitario": round(costo_unitario, 2),
        "precio_con_descuento_sugerido": round(precio_sugerido, 2),
        "incremento_volumen_esperado_pct": round((volumen_sugerido / volumen_actual - 1.0) * 100.0, 2),
        "ingreso_actual_proyectado": round(ingreso_actual, 2),
        "ingreso_con_descuento_proyectado": round(ingreso_proyectado, 2),
        "margen_actual_proyectado": round(margen_actual, 2),
        "margen_ganancia_proyectado": round(margen_proyectado, 2),
        "recomendacion_estrategica": recomendacion,
        "escalas_prescritas": escalas,
        "alternativas_prescritas": alternativas,
        "regresion": {"metodo": "OLS log-log", "puntos": len(precios), "alpha": round(alpha, 4), "r2": round(r2, 4)},
    }


def seleccionar_serie_por_zona(filas: List[Dict[str, Any]], zona: str) -> Tuple[List[float], List[float], str]:
    """
    Elige los puntos precio-cantidad para la regresión: primero los de la zona pedida; si en la zona no hay al menos
    2 precios distintos, usa todas las zonas del producto. Devuelve (precios, cantidades, alcance).
    """
    zona_norm = (zona or "").strip().lower()
    en_zona = [f for f in filas if str(f.get("zona", "")).strip().lower() == zona_norm]
    if len({float(f["precio_unitario"]) for f in en_zona}) >= 2:
        elegidas, alcance = en_zona, "ZONA"
    else:
        elegidas, alcance = filas, "TODAS_LAS_ZONAS"
    precios = [float(f["precio_unitario"]) for f in elegidas]
    cantidades = [float(f["cantidad"]) for f in elegidas]
    return precios, cantidades, alcance


def productos_afines(filas_pedidos: List[Dict[str, Any]], producto_id: int, zona: str, limite: int = MAX_BUNDLES) -> List[str]:
    """
    Productos que más se piden junto con el producto (mismo pedido), priorizando la zona. Base de los bundles.
    """
    zona_norm = (zona or "").strip().lower()

    def contar(filas: List[Dict[str, Any]]) -> List[str]:
        pedidos = {f["pedido_id"] for f in filas if int(f["producto_id"]) == int(producto_id)}
        conteo: Dict[str, int] = {}
        for f in filas:
            if f["pedido_id"] in pedidos and int(f["producto_id"]) != int(producto_id):
                conteo[f["nombre_producto"]] = conteo.get(f["nombre_producto"], 0) + 1
        return [nombre for nombre, _ in sorted(conteo.items(), key=lambda kv: (-kv[1], kv[0]))][:limite]

    en_zona = contar([f for f in filas_pedidos if str(f.get("zona", "")).strip().lower() == zona_norm])
    return en_zona if en_zona else contar(filas_pedidos)
