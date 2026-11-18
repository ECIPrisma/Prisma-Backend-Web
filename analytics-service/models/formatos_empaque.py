"""
HU-31 – Minería de canasta: prescripción de formatos de empaque (FP-Growth).

El rechazo de una presentación grande se DEDUCE de los pedidos (no requiere registrar eventos nuevos):
en una zona, los tenderos demandan la referencia y compran su presentación pequeña, pero no la grande,
aunque la grande sí está disponible (se vende en otra zona).

Por zona, cada pedido es una transacción con:
  - las presentaciones que contiene (ej. "Postobón Manzana 400ml"), y
  - un ítem virtual "REF::<referencia>" que representa la demanda de la referencia, sin importar el tamaño.
FP-Growth encuentra los conjuntos frecuentes y de ellos salen las reglas:
  REF::Postobón Manzana -> Postobón Manzana 400ml   (aceptación de la presentación pequeña)
  REF::Postobón Manzana -> Postobón Manzana 2.5L    (aceptación de la presentación grande)

Criterio 1: la referencia es frecuente en la zona, la regla hacia la presentación pequeña tiene confianza alta y la
presentación grande no forma una regla con soporte suficiente (casi nunca se pide) -> rechazo sistemático ->
se prescriben empaques fraccionados o formatos institucionales más accesibles.
Criterio 2: si no hay reglas con soporte y confianza suficientes, no se emite ninguna prescripción para la referencia.
"""

import math
import re
from collections import defaultdict
from typing import Any, Dict, List, Optional

from models.fpgrowth import fp_growth, regla

SOPORTE_MINIMO = 0.30            # la referencia debe estar en al menos el 30% de los pedidos de la zona
CONFIANZA_MINIMA = 0.80          # y comprarse en su presentación pequeña en al menos el 80% de esos pedidos
CONFIANZA_MAXIMA_GRANDE = 0.10   # la presentación grande aparece en a lo sumo el 10% (rechazo sistemático)
MIN_TRANSACCIONES = 3            # con menos pedidos en la zona no hay evidencia suficiente

PREFIJO_REFERENCIA = "REF::"
_TAMANO = re.compile(r"^(?P<ref>.*?)\s*(?P<num>\d+(?:[.,]\d+)?)\s*(?P<unidad>ml|cc|l|lt|g|gr|kg)\s*$", re.IGNORECASE)
_A_BASE = {"ml": ("ml", 1), "cc": ("ml", 1), "l": ("ml", 1000), "lt": ("ml", 1000),
           "g": ("g", 1), "gr": ("g", 1), "kg": ("g", 1000)}


def interpretar_presentacion(nombre: str) -> Optional[Dict[str, Any]]:
    """'Postobón Manzana 2.5L' -> {referencia: 'Postobón Manzana', tamano: 2500, unidad: 'ml'}. None si no hay tamaño."""
    m = _TAMANO.match((nombre or "").strip())
    if not m or not m.group("ref").strip():
        return None
    unidad, factor = _A_BASE[m.group("unidad").lower()]
    return {
        "referencia": m.group("ref").strip(),
        "tamano": round(float(m.group("num").replace(",", ".")) * factor, 3),
        "unidad": unidad,
    }


def etiqueta_tamano(tamano: float, unidad: str) -> str:
    """2500 ml -> '2.5L'; 400 ml -> '400ml'; 1500 g -> '1.5kg'."""
    grande = "L" if unidad == "ml" else "kg"
    if tamano >= 1000:
        valor = tamano / 1000
        texto = f"{valor:.2f}".rstrip("0").rstrip(".")
        return f"{texto}{grande}"
    return f"{int(round(tamano))}{unidad}"


def _formato_fraccionado(referencia: str, pequena: Dict, grande: Dict) -> Dict[str, Any]:
    """Tamaño intermedio (la mitad de la grande, sin bajar de la pequeña) al precio por unidad de la grande."""
    tamano = max(pequena["tamano"] * 2, grande["tamano"] / 2)
    if tamano >= grande["tamano"]:
        tamano = (pequena["tamano"] + grande["tamano"]) / 2
    paso = 250 if tamano >= 1000 else 50
    tamano = max(paso, round(tamano / paso) * paso)
    precio = round(grande["precio_unitario_base"] * tamano, -1)
    return {
        "tipo": "EMPAQUE_FRACCIONADO",
        "nombre": f"{referencia} {etiqueta_tamano(tamano, grande['unidad'])}",
        "tamano": tamano,
        "unidad": grande["unidad"],
        "precio_estimado": precio,
        "ticket_vs_presentacion_grande_pct": round(precio / grande["precio_promedio"] * 100, 1),
        "descripcion": (f"Desarrollar una presentación intermedia de {etiqueta_tamano(tamano, grande['unidad'])}: "
                        f"conserva el precio por {grande['unidad']} de la presentación grande pero con un desembolso "
                        f"menor para el tendero."),
    }


def _formato_institucional(pequena: Dict, grande: Dict) -> Dict[str, Any]:
    """
    Caja de N presentaciones pequeñas, sin superar el contenido de la grande (para que el desembolso no sea mayor),
    al precio por unidad de medida de la grande: cada unidad le sale al tendero más barata que comprarla suelta.
    """
    unidades = max(2, math.floor(grande["tamano"] / pequena["tamano"]))
    precio_caja = round(grande["precio_unitario_base"] * pequena["tamano"] * unidades, -1)
    return {
        "tipo": "FORMATO_INSTITUCIONAL",
        "nombre": f"Caja institucional {unidades} x {pequena['nombre']}",
        "unidades": unidades,
        "precio_estimado": precio_caja,
        "precio_por_unidad": round(precio_caja / unidades, -1),
        "ticket_vs_presentacion_grande_pct": round(precio_caja / grande["precio_promedio"] * 100, 1),
        "descripcion": (f"Despachar {unidades} unidades de {pequena['nombre']} como un solo formato, al precio por "
                        f"{grande['unidad']} de la presentación grande: cada unidad le sale al tendero más barata que "
                        f"suelta y puede venderlas por separado, rotándolas más rápido que una unidad de alto valor."),
    }


def prescribir_formatos_empaque(ventas: List[Dict[str, Any]], zona: Optional[str] = None,
                                soporte_minimo: float = SOPORTE_MINIMO,
                                confianza_minima: float = CONFIANZA_MINIMA,
                                confianza_maxima_grande: float = CONFIANZA_MAXIMA_GRANDE,
                                min_transacciones: int = MIN_TRANSACCIONES) -> Dict[str, Any]:
    """
    ventas: filas de ventas_olap del proveedor (pedido_id, nombre_producto, precio_unitario, zona).
    Devuelve las prescripciones (criterio 1) y, por transparencia, las referencias evaluadas sin patrón (criterio 2).
    """
    # 1. Catálogo de presentaciones por referencia (a partir de lo vendido en todas las zonas)
    presentaciones: Dict[str, Dict[str, Dict[str, Any]]] = defaultdict(dict)
    precios: Dict[str, List[float]] = defaultdict(list)
    zonas_venta: Dict[str, set] = defaultdict(set)
    for fila in ventas:
        info = interpretar_presentacion(fila.get("nombre_producto"))
        if not info:
            continue
        nombre = fila["nombre_producto"]
        presentaciones[info["referencia"]][nombre] = {**info, "nombre": nombre}
        precios[nombre].append(float(fila.get("precio_unitario") or 0))
        zonas_venta[nombre].add(fila.get("zona"))

    multipresentacion = {}
    for referencia, items in presentaciones.items():
        unidades = {p["unidad"] for p in items.values()}
        if len(items) < 2 or len(unidades) != 1:
            continue
        for p in items.values():
            p["precio_promedio"] = round(sum(precios[p["nombre"]]) / len(precios[p["nombre"]]), 2)
            p["precio_unitario_base"] = p["precio_promedio"] / p["tamano"]
            p["zonas_con_ventas"] = sorted(z for z in zonas_venta[p["nombre"]] if z)
        ordenadas = sorted(items.values(), key=lambda p: p["tamano"])
        multipresentacion[referencia] = ordenadas

    base = {
        "algoritmo": "FP-Growth",
        "zona": zona,
        "parametros": {
            "soporte_minimo": soporte_minimo,
            "confianza_minima_presentacion_pequena": confianza_minima,
            "confianza_maxima_presentacion_grande": confianza_maxima_grande,
            "min_transacciones": min_transacciones,
        },
        "prescripciones": [],
        "referencias_sin_patron": [],
    }
    if not multipresentacion:
        return {**base, "suficientes_datos": False, "total_transacciones": 0,
                "mensaje": "No hay referencias vendidas en más de una presentación para analizar"}

    # 2. Transacciones por zona: presentaciones del pedido + ítem virtual de la referencia demandada
    pedidos: Dict[str, Dict[Any, set]] = defaultdict(lambda: defaultdict(set))
    for fila in ventas:
        z = fila.get("zona")
        if zona and z != zona:
            continue
        info = interpretar_presentacion(fila.get("nombre_producto"))
        items = pedidos[z][fila.get("pedido_id")]
        items.add(fila["nombre_producto"])
        if info and info["referencia"] in multipresentacion:
            items.add(PREFIJO_REFERENCIA + info["referencia"])

    total_transacciones = 0
    for z in sorted(k for k in pedidos if k):
        transacciones = [sorted(items) for items in pedidos[z].values()]
        n = len(transacciones)
        total_transacciones += n
        minimo_abs = max(1, math.ceil(soporte_minimo * n))
        itemsets = fp_growth(transacciones, minimo_abs)

        for referencia, ordenadas in sorted(multipresentacion.items()):
            ref_item = PREFIJO_REFERENCIA + referencia
            demanda = sum(1 for t in transacciones if ref_item in t)
            if demanda == 0:
                continue  # la referencia no se pide en esta zona: nada que evaluar

            def sin_patron(motivo: str):
                base["referencias_sin_patron"].append({"referencia": referencia, "zona": z, "motivo": motivo,
                                                       "pedidos_con_demanda": demanda, "pedidos_zona": n})

            if n < min_transacciones:
                sin_patron(f"Solo hay {n} pedidos en la zona (mínimo {min_transacciones}): no hay evidencia suficiente")
                continue
            if frozenset({ref_item}) not in itemsets:
                sin_patron(f"La referencia aparece en el {demanda / n:.0%} de los pedidos, por debajo del soporte "
                           f"mínimo ({soporte_minimo:.0%})")
                continue

            pequena = ordenadas[0]
            regla_pequena = regla(itemsets, n, ref_item, pequena["nombre"])
            if not regla_pequena or regla_pequena["confianza"] < confianza_minima:
                sin_patron(f"No hay una regla con confianza suficiente hacia {pequena['nombre']} "
                           f"(mínimo {confianza_minima:.0%})")
                continue

            # Presentaciones grandes rechazadas: disponibles (se venden en otra zona) pero casi nunca pedidas aquí
            for grande in ordenadas[1:]:
                compras_grande = sum(1 for t in transacciones if ref_item in t and grande["nombre"] in t)
                confianza_grande = compras_grande / demanda
                regla_grande = regla(itemsets, n, ref_item, grande["nombre"])
                disponible = any(zv != z for zv in grande["zonas_con_ventas"])
                if regla_grande is not None or confianza_grande > confianza_maxima_grande:
                    sin_patron(f"{grande['nombre']} sí se compra en la zona ({confianza_grande:.0%} de los pedidos "
                               f"con la referencia): no hay rechazo sistemático")
                    continue
                if not disponible:
                    sin_patron(f"{grande['nombre']} no se vende en ninguna otra zona: no se puede afirmar que se "
                               f"rechace (podría no estar disponible)")
                    continue

                base["prescripciones"].append({
                    "referencia": referencia,
                    "zona": z,
                    "pedidos_zona": n,
                    "pedidos_con_demanda": demanda,
                    "presentacion_aceptada": {"nombre": pequena["nombre"], "precio_promedio": pequena["precio_promedio"]},
                    "presentacion_rechazada": {"nombre": grande["nombre"], "precio_promedio": grande["precio_promedio"],
                                               "zonas_donde_si_se_vende": [x for x in grande["zonas_con_ventas"] if x != z]},
                    "regla_aceptacion": regla_pequena,
                    "confianza_presentacion_grande": round(confianza_grande, 3),
                    "formatos_sugeridos": [_formato_fraccionado(referencia, pequena, grande),
                                           _formato_institucional(pequena, grande)],
                    "mensaje": (f"En la zona {z} los tenderos piden {referencia} en el {demanda / n:.0%} de sus pedidos "
                                f"y lo compran como {pequena['nombre']} el {regla_pequena['confianza']:.0%} de las veces, "
                                f"pero rechazan {grande['nombre']} (${grande['precio_promedio']:,.0f}), que sí se vende "
                                f"en otras zonas. Desarrolla y despacha un formato más accesible.").replace(",", "."),
                })

    prescripciones = base["prescripciones"]
    return {**base, "suficientes_datos": total_transacciones > 0, "total_transacciones": total_transacciones,
            "mensaje": None if prescripciones else
            "No se detectaron patrones de rechazo con soporte y confianza suficientes: no se emite ninguna prescripción de empaque"}
