"""
N-11: Ranking de productos del proveedor para la pantalla /modo-pro/ranking.
"""

from typing import Any, Dict, List, Optional


def ranking_productos(filas: List[Dict[str, Any]], zona: Optional[str] = None) -> Dict[str, Any]:
    """
    filas: ventas con producto_id, nombre_producto, categoria, cantidad, total, zona.
    Ordena los productos por ingresos (desempate por unidades) y calcula su participación en el total.
    """
    zona_norm = (zona or "").strip().lower()
    if zona_norm:
        filas = [f for f in filas if str(f.get("zona", "")).strip().lower() == zona_norm]
    if not filas:
        return {"zona": zona, "total_ingresos": 0.0, "productos": [], "suficientes_datos": False,
                "mensaje": "Aún no hay suficientes datos"}

    agregados: Dict[int, Dict[str, Any]] = {}
    for f in filas:
        pid = int(f["producto_id"])
        a = agregados.setdefault(pid, {
            "producto_id": pid,
            "nombre_producto": str(f["nombre_producto"]),
            "categoria": str(f.get("categoria", "")),
            "unidades": 0.0,
            "ingresos": 0.0,
            "_zonas": set(),
            "_pedidos": set(),
        })
        a["unidades"] += float(f["cantidad"])
        a["ingresos"] += float(f["total"])
        a["_zonas"].add(str(f.get("zona", "")))
        if "pedido_id" in f:
            a["_pedidos"].add(f["pedido_id"])

    total = sum(a["ingresos"] for a in agregados.values())
    productos = sorted(agregados.values(), key=lambda a: (-a["ingresos"], -a["unidades"], a["nombre_producto"]))
    for posicion, a in enumerate(productos, start=1):
        a["posicion"] = posicion
        a["unidades"] = round(a["unidades"], 2)
        a["ingresos"] = round(a["ingresos"], 2)
        a["participacion_pct"] = round(a["ingresos"] / total * 100.0, 2) if total > 0 else 0.0
        a["zonas_con_ventas"] = sorted(a.pop("_zonas"))
        a["pedidos"] = len(a.pop("_pedidos"))

    return {"zona": zona, "total_ingresos": round(total, 2), "productos": productos, "suficientes_datos": True,
            "mensaje": None}
