"""
HU-16: Detección de brecha de penetración territorial ("desabastecimiento artificial").

1. Agrupa las zonas en clústeres de comportamiento similar: cada zona se describe por su mezcla de ventas por
   categoría (vector de participación) y dos zonas son similares si su similitud coseno >= umbral. Los clústeres
   son los grupos conectados de zonas similares (determinista, sin aleatoriedad).
2. Dentro de cada clúster, un producto "superventas" en una zona (percentil >= 80 de unidades en esa zona) que en
   otra zona del mismo clúster vende por debajo del percentil 15 (incluye ventas nulas) y menos del 25% que la
   zona líder genera una alerta.
3. Si las ventas del producto son homogéneas entre zonas comparables, no se genera alerta.
"""

from typing import Any, Dict, List, Tuple

import numpy as np

PERCENTIL_SUPERVENTAS = 80.0
PERCENTIL_BRECHA = 15.0
UMBRAL_SIMILITUD = 0.80
# Protección para catálogos pequeños (el percentil 15 de pocos productos puede quedar alto): además de estar bajo el
# percentil 15, la zona rezagada debe vender menos del 25% de lo que vende la zona líder.
FRACCION_MAXIMA_REZAGADA = 0.25


def _vectores_por_zona(filas: List[Dict[str, Any]]) -> Tuple[List[str], List[str], Dict[str, np.ndarray]]:
    zonas = sorted({str(f["zona"]) for f in filas})
    categorias = sorted({str(f["categoria"]) for f in filas})
    vectores: Dict[str, np.ndarray] = {}
    for zona in zonas:
        v = np.zeros(len(categorias))
        for f in filas:
            if str(f["zona"]) == zona:
                v[categorias.index(str(f["categoria"]))] += float(f["cantidad"])
        total = v.sum()
        vectores[zona] = v / total if total > 0 else v
    return zonas, categorias, vectores


def _similitud(a: np.ndarray, b: np.ndarray) -> float:
    na, nb = np.linalg.norm(a), np.linalg.norm(b)
    return float(np.dot(a, b) / (na * nb)) if na > 0 and nb > 0 else 0.0


def agrupar_zonas(filas: List[Dict[str, Any]], umbral: float = UMBRAL_SIMILITUD) -> Tuple[List[List[str]], Dict[Tuple[str, str], float]]:
    """Clústeres de zonas similares (componentes conectados) y la similitud de cada par de zonas."""
    zonas, _, vectores = _vectores_por_zona(filas)
    similitudes: Dict[Tuple[str, str], float] = {}
    vecinos: Dict[str, set] = {z: set() for z in zonas}
    for i, a in enumerate(zonas):
        for b in zonas[i + 1:]:
            s = _similitud(vectores[a], vectores[b])
            similitudes[(a, b)] = similitudes[(b, a)] = s
            if s >= umbral:
                vecinos[a].add(b)
                vecinos[b].add(a)

    clusters: List[List[str]] = []
    visitadas: set = set()
    for zona in zonas:
        if zona in visitadas:
            continue
        pila, grupo = [zona], []
        while pila:
            actual = pila.pop()
            if actual in visitadas:
                continue
            visitadas.add(actual)
            grupo.append(actual)
            pila.extend(vecinos[actual] - visitadas)
        clusters.append(sorted(grupo))
    return clusters, similitudes


def detectar_brechas(filas: List[Dict[str, Any]], umbral_similitud: float = UMBRAL_SIMILITUD) -> Dict[str, Any]:
    """
    filas: ventas con las claves zona, categoria, producto_id, nombre_producto, cantidad.
    Devuelve los clústeres de zonas y las alertas de desabastecimiento artificial.
    """
    if not filas or len({str(f["zona"]) for f in filas}) < 2:
        return {"clusters": [], "alertas": [], "suficientes_datos": False,
                "mensaje": "Aún no hay suficientes datos"}

    clusters, similitudes = agrupar_zonas(filas, umbral_similitud)

    # Unidades por (zona, producto)
    unidades: Dict[Tuple[str, int], float] = {}
    nombres: Dict[int, str] = {}
    for f in filas:
        clave = (str(f["zona"]), int(f["producto_id"]))
        unidades[clave] = unidades.get(clave, 0.0) + float(f["cantidad"])
        nombres[int(f["producto_id"])] = str(f["nombre_producto"])

    alertas: Dict[Tuple[int, str], Dict[str, Any]] = {}
    for cluster in clusters:
        if len(cluster) < 2:
            continue
        productos = sorted({p for (z, p) in unidades if z in cluster})
        # Distribución de unidades por zona sobre TODOS los productos del clúster (las ventas nulas cuentan como 0)
        distribucion = {z: np.array([unidades.get((z, p), 0.0) for p in productos]) for z in cluster}
        p80 = {z: float(np.percentile(distribucion[z], PERCENTIL_SUPERVENTAS)) for z in cluster}
        p15 = {z: float(np.percentile(distribucion[z], PERCENTIL_BRECHA)) for z in cluster}

        for lider in cluster:
            for producto in productos:
                u_lider = unidades.get((lider, producto), 0.0)
                if u_lider <= 0 or u_lider < p80[lider]:
                    continue  # no es superventas en esta zona
                for rezagada in cluster:
                    if rezagada == lider:
                        continue
                    u_rezagada = unidades.get((rezagada, producto), 0.0)
                    if u_rezagada > p15[rezagada] or u_rezagada > FRACCION_MAXIMA_REZAGADA * u_lider:
                        continue  # ventas homogéneas: sin brecha
                    clave = (producto, rezagada)
                    # Si varias zonas líderes apuntan a la misma brecha, se conserva la de más unidades
                    if clave in alertas and alertas[clave]["unidades_zona_lider"] >= u_lider:
                        continue
                    nombre = nombres[producto]
                    alertas[clave] = {
                        "tipo": "DESABASTECIMIENTO_ARTIFICIAL",
                        "producto_id": producto,
                        "nombre_producto": nombre,
                        "zona_lider": lider,
                        "unidades_zona_lider": round(u_lider, 2),
                        "zona_rezagada": rezagada,
                        "unidades_zona_rezagada": round(u_rezagada, 2),
                        "percentil_15_zona_rezagada": round(p15[rezagada], 2),
                        "similitud_zonas": round(similitudes[(lider, rezagada)], 3),
                        "mensaje": (f"{nombre} es superventas en la zona {lider} ({u_lider:.0f} uds) pero en la zona "
                                    f"{rezagada}, de comportamiento similar, vende {u_rezagada:.0f} uds (por debajo del "
                                    f"percentil 15). Posible desabastecimiento artificial."),
                        "prescripcion": (f"Evalúa una promoción de introducción de {nombre} en la zona {rezagada} y "
                                         f"verifica que tus rutas lo estén ofreciendo a esas tiendas."),
                    }

    lista = sorted(alertas.values(), key=lambda a: (-a["unidades_zona_lider"], a["nombre_producto"], a["zona_rezagada"]))
    return {"clusters": clusters, "alertas": lista, "suficientes_datos": True,
            "mensaje": None if lista else "No se detectaron brechas de penetración entre zonas comparables"}
