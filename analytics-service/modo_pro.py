"""
Endpoints de las pantallas Modo Pro del portal del proveedor (N-11):
  GET  /api/v1/analytics/modo-pro/ranking                     -> ranking de productos
  GET  /api/v1/analytics/modo-pro/alertas                     -> brechas de penetración territorial (HU-16)
  GET  /api/v1/analytics/modo-pro/recomendaciones             -> recomendaciones narrativas publicadas (HU-17)
  POST /api/v1/analytics/modo-pro/recomendaciones/generar     -> proceso nocturno de generación (HU-17 + N-12)
  GET  /api/v1/analytics/modo-pro/formatos-empaque            -> prescripción de formatos de empaque (HU-31)

El proceso nocturno lo dispara el timer de systemd prisma-reportes-nocturnos.timer (02:00 todos los días).
"""

import json
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Query

from database import db
from models.brechas import detectar_brechas
from models.elasticity import calcular_elasticidad_y_prescribir_descuento
from models.formatos_empaque import prescribir_formatos_empaque
from models.narrativa import construir_contexto, generar_recomendacion
from models.ranking import ranking_productos

router = APIRouter(prefix="/api/v1/analytics/modo-pro", tags=["Modo Pro"])

TODAS_LAS_ZONAS = "Todas las zonas"


def _ventas(proveedor_id: int) -> List[Dict[str, Any]]:
    return db.query_df("""
        SELECT pedido_id, producto_id, nombre_producto, categoria, cantidad, precio_unitario, total, zona
        FROM ventas_olap
        WHERE proveedor_id = ?
    """, [proveedor_id]).to_dict(orient="records")


# ---------------------------------------------------------------------------------------------- Ranking (N-11)

@router.get("/ranking")
def ranking(proveedor_id: int = Query(1), zona: Optional[str] = Query(None, description="Filtrar por zona")):
    resultado = ranking_productos(_ventas(proveedor_id), zona)
    resultado["proveedor_id"] = proveedor_id
    return resultado


# ---------------------------------------------------------------------------------------------- Alertas (HU-16)

@router.get("/alertas")
def alertas(proveedor_id: int = Query(1)):
    resultado = detectar_brechas(_ventas(proveedor_id))
    resultado["proveedor_id"] = proveedor_id
    resultado["generado_en"] = datetime.now(timezone.utc).isoformat()
    return resultado


# ------------------------------------------------------------------------------ Recomendaciones (HU-17 + N-12)

def _contextos_por_categoria_zona(filas: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Calcula la elasticidad de cada categoría en cada zona con al menos 2 precios distintos. Si una categoría no tiene
    datos suficientes en ninguna zona, se calcula una sola vez con todas las zonas.
    """
    contextos: List[Dict[str, Any]] = []
    categorias = sorted({str(f["categoria"]) for f in filas})
    for categoria in categorias:
        de_categoria = [f for f in filas if str(f["categoria"]) == categoria]
        grupos = []
        for zona in sorted({str(f["zona"]) for f in de_categoria}):
            en_zona = [f for f in de_categoria if str(f["zona"]) == zona]
            if len({float(f["precio_unitario"]) for f in en_zona}) >= 2:
                grupos.append((zona, en_zona))
        if not grupos and len({float(f["precio_unitario"]) for f in de_categoria}) >= 2:
            grupos.append((TODAS_LAS_ZONAS, de_categoria))

        for zona, datos in grupos:
            precios = [float(f["precio_unitario"]) for f in datos]
            cantidades = [float(f["cantidad"]) for f in datos]
            costo = round(sum(precios) / len(precios) * 0.5, 2)  # supuesto: margen bruto del 50%
            resultado = calcular_elasticidad_y_prescribir_descuento(precios, cantidades, costo_unitario=costo, zona=zona)
            if "error" not in resultado:
                contextos.append(construir_contexto(resultado, categoria, zona))
    return contextos


def _ejecutar(sql: str, params: Optional[list] = None) -> None:
    conn = db.get_connection()
    if db.is_duckdb:
        conn.execute(sql, params or [])
    else:
        cursor = conn.cursor()
        cursor.execute(sql, params or [])
        conn.commit()


def _asegurar_tabla() -> None:
    _ejecutar("""
        CREATE TABLE IF NOT EXISTS recomendaciones_modo_pro (
            lote_id TEXT,
            proveedor_id INTEGER,
            generado_en TEXT,
            categoria TEXT,
            zona TEXT,
            estado TEXT,
            fuente TEXT,
            narrativa TEXT,
            resumen_plantilla TEXT,
            mensaje TEXT,
            matriz_json TEXT
        )
    """)


def generar_reportes_nocturnos(proveedor_id: int) -> Dict[str, Any]:
    """HU-17: genera y publica una recomendación por categoría y zona. Si la IA falla, guarda el respaldo."""
    _asegurar_tabla()
    lote_id = str(uuid.uuid4())
    generado_en = datetime.now(timezone.utc).isoformat()
    recomendaciones = []
    for ctx in _contextos_por_categoria_zona(_ventas(proveedor_id)):
        rec = generar_recomendacion(ctx)
        _ejecutar(
            "INSERT INTO recomendaciones_modo_pro VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [lote_id, proveedor_id, generado_en, ctx["categoria"], ctx["zona"], rec["estado"], rec["fuente"],
             rec["narrativa"], rec["resumen_plantilla"], rec["mensaje"], json.dumps(rec["matriz"], ensure_ascii=False)])
        recomendaciones.append({"categoria": ctx["categoria"], "zona": ctx["zona"], **rec})
    return {"lote_id": lote_id, "proveedor_id": proveedor_id, "generado_en": generado_en,
            "total": len(recomendaciones), "con_ia": sum(1 for r in recomendaciones if r["estado"] == "GENERADA"),
            "recomendaciones": recomendaciones}


@router.post("/recomendaciones/generar")
def generar(proveedor_id: int = Query(1)):
    return generar_reportes_nocturnos(proveedor_id)


@router.get("/recomendaciones")
def recomendaciones(proveedor_id: int = Query(1)):
    """Último lote publicado. Si nunca se ha generado (instalación nueva), lo genera una vez."""
    _asegurar_tabla()
    df = db.query_df("""
        SELECT * FROM recomendaciones_modo_pro
        WHERE proveedor_id = ?
        ORDER BY generado_en DESC
    """, [proveedor_id])
    if len(df) == 0:
        lote = generar_reportes_nocturnos(proveedor_id)
        filas = lote["recomendaciones"]
        generado_en = lote["generado_en"]
    else:
        ultimo_lote = df.iloc[0]["lote_id"]
        df = df[df["lote_id"] == ultimo_lote]
        generado_en = str(df.iloc[0]["generado_en"])
        filas = [{
            "categoria": r["categoria"], "zona": r["zona"], "estado": r["estado"], "fuente": r["fuente"],
            "narrativa": r["narrativa"], "resumen_plantilla": r["resumen_plantilla"], "mensaje": r["mensaje"],
            "matriz": json.loads(r["matriz_json"]) if r["matriz_json"] else {},
        } for r in df.to_dict(orient="records")]

    # pandas devuelve NaN en columnas de texto vacías: se normalizan a None para el JSON
    for f in filas:
        for clave in ("narrativa", "mensaje", "resumen_plantilla"):
            if isinstance(f.get(clave), float):
                f[clave] = None

    return {"proveedor_id": proveedor_id, "generado_en": generado_en, "recomendaciones": filas,
            "suficientes_datos": len(filas) > 0,
            "mensaje": None if filas else "Aún no hay suficientes datos"}


# ---------------------------------------------------------------------------- Formatos de empaque (HU-31)

@router.get("/formatos-empaque")
def formatos_empaque(proveedor_id: int = Query(1), zona: Optional[str] = Query(None, description="Analizar solo esta zona")):
    """
    Minería de canasta (FP-Growth) por zona: si los tenderos demandan una referencia pero rechazan sistemáticamente
    su presentación grande, prescribe empaques fraccionados o formatos institucionales más accesibles.
    Sin reglas con soporte y confianza suficientes no se emite ninguna prescripción para esa referencia.
    """
    resultado = prescribir_formatos_empaque(_ventas(proveedor_id), zona)
    resultado["proveedor_id"] = proveedor_id
    return resultado
