"""
FP-Growth (Han, Pei y Yin, 2000): minería de conjuntos de ítems frecuentes sin generar candidatos.

1. Se cuentan los ítems y se descartan los que no alcanzan el soporte mínimo.
2. Cada transacción se inserta en un árbol de prefijos (FP-tree) ordenando sus ítems por frecuencia descendente,
   de modo que las transacciones parecidas comparten ramas.
3. Para cada ítem (del menos al más frecuente) se arma su base de patrones condicional (los caminos que llevan
   a él), se construye un FP-tree condicional y se mina recursivamente.

Determinista: los empates de frecuencia se rompen por el nombre del ítem.
"""

from collections import defaultdict
from typing import Dict, FrozenSet, Iterable, List, Optional, Tuple


class _Nodo:
    __slots__ = ("item", "conteo", "padre", "hijos")

    def __init__(self, item: Optional[str], padre: Optional["_Nodo"]):
        self.item = item
        self.conteo = 0
        self.padre = padre
        self.hijos: Dict[str, "_Nodo"] = {}


def _construir_arbol(transacciones: Iterable[Tuple[List[str], int]], soporte_minimo: int):
    """Construye el FP-tree a partir de transacciones ponderadas (ítems, peso)."""
    transacciones = list(transacciones)
    conteos: Dict[str, int] = defaultdict(int)
    for items, peso in transacciones:
        for item in set(items):
            conteos[item] += peso
    frecuentes = {item: c for item, c in conteos.items() if c >= soporte_minimo}
    if not frecuentes:
        return None, {}

    raiz = _Nodo(None, None)
    cabecera: Dict[str, List[_Nodo]] = defaultdict(list)
    for items, peso in transacciones:
        ordenados = sorted({i for i in items if i in frecuentes}, key=lambda i: (-frecuentes[i], i))
        nodo = raiz
        for item in ordenados:
            hijo = nodo.hijos.get(item)
            if hijo is None:
                hijo = _Nodo(item, nodo)
                nodo.hijos[item] = hijo
                cabecera[item].append(hijo)
            hijo.conteo += peso
            nodo = hijo
    return cabecera, frecuentes


def _minar(cabecera, frecuentes, soporte_minimo: int, sufijo: FrozenSet[str],
           resultado: Dict[FrozenSet[str], int]) -> None:
    for item in sorted(frecuentes, key=lambda i: (frecuentes[i], i)):
        patron = sufijo | {item}
        resultado[patron] = frecuentes[item]

        # Base de patrones condicional: caminos desde la raíz hasta cada aparición del ítem
        base = []
        for nodo in cabecera[item]:
            camino = []
            padre = nodo.padre
            while padre is not None and padre.item is not None:
                camino.append(padre.item)
                padre = padre.padre
            if camino:
                base.append((camino, nodo.conteo))

        cabecera_cond, frecuentes_cond = _construir_arbol(base, soporte_minimo)
        if frecuentes_cond:
            _minar(cabecera_cond, frecuentes_cond, soporte_minimo, patron, resultado)


def fp_growth(transacciones: List[List[str]], soporte_minimo: int) -> Dict[FrozenSet[str], int]:
    """
    Devuelve todos los conjuntos de ítems con soporte absoluto (número de transacciones) >= soporte_minimo,
    como {frozenset(items): conteo}.
    """
    if soporte_minimo < 1:
        raise ValueError("El soporte mínimo absoluto debe ser al menos 1")
    cabecera, frecuentes = _construir_arbol(((t, 1) for t in transacciones), soporte_minimo)
    resultado: Dict[FrozenSet[str], int] = {}
    if frecuentes:
        _minar(cabecera, frecuentes, soporte_minimo, frozenset(), resultado)
    return resultado


def regla(itemsets: Dict[FrozenSet[str], int], total: int, antecedente: str, consecuente: str) -> Optional[Dict]:
    """
    Regla de asociación antecedente -> consecuente a partir de los conjuntos frecuentes.
    Devuelve None si el par no es frecuente (no hay regla con soporte suficiente).
    """
    par = itemsets.get(frozenset({antecedente, consecuente}))
    base = itemsets.get(frozenset({antecedente}))
    cons = itemsets.get(frozenset({consecuente}))
    if not par or not base or not cons or total <= 0:
        return None
    confianza = par / base
    return {
        "antecedente": antecedente,
        "consecuente": consecuente,
        "soporte": round(par / total, 3),
        "confianza": round(confianza, 3),
        "lift": round(confianza / (cons / total), 3),
    }
