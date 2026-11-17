"""
HU-17 + N-12: Recomendación comercial narrativa con IA a costo $0.

- El resultado del motor estadístico (elasticidad de una categoría en una zona) se envía como contexto JSON
  estructurado a un modelo de lenguaje en su nivel gratuito (Gemini). Llave en la variable GEMINI_API_KEY.
- Si no hay llave, la API no responde o excede el tiempo límite: se conserva la matriz numérica, se informa
  "Recomendación narrativa no disponible temporalmente" y se genera un resumen con plantilla determinista.
"""

import json
import os
from typing import Any, Callable, Dict, Optional

import requests

MENSAJE_NO_DISPONIBLE = "Recomendación narrativa no disponible temporalmente"
MODELO_POR_DEFECTO = "gemini-2.5-flash"
TIMEOUT_POR_DEFECTO = 8.0
URL_GEMINI = "https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent"


def construir_contexto(resultado_elasticidad: Dict[str, Any], categoria: str, zona: str) -> Dict[str, Any]:
    """Matriz numérica (contexto estructurado) que se envía al modelo y que se conserva si la IA falla."""
    escalas = resultado_elasticidad.get("escalas_prescritas") or []
    escala = None
    if escalas:
        e = escalas[0]
        escala = e.get("descripcion") or f"{e.get('escala_unidades')} con {e.get('descuento_porcentaje')}% de descuento"
    bloqueado = resultado_elasticidad.get("descuento_bloqueado")
    if bloqueado is None:  # versión del motor sin el campo: se deduce del tipo de demanda
        bloqueado = resultado_elasticidad.get("tipo_demanda") != "Elástica"
    alternativas = [a.get("titulo") for a in (resultado_elasticidad.get("alternativas_prescritas") or []) if a.get("titulo")]
    return {
        "categoria": categoria,
        "zona": zona,
        "coeficiente_elasticidad": resultado_elasticidad.get("coeficiente_elasticidad"),
        "tipo_demanda": resultado_elasticidad.get("tipo_demanda"),
        "descuento_bloqueado": bool(bloqueado),
        "escala_recomendada": None if bloqueado else escala,
        "descuento_optimo_recomendado_pct": resultado_elasticidad.get("descuento_optimo_recomendado_pct"),
        "incremento_volumen_esperado_pct": resultado_elasticidad.get("incremento_volumen_esperado_pct"),
        "precio_base_promedio": resultado_elasticidad.get("precio_base_promedio"),
        "alternativas_sin_descuento": alternativas,
    }


def generar_narrativa_plantilla(ctx: Dict[str, Any]) -> str:
    """N-12: texto determinista en lenguaje claro a partir de la matriz numérica (sin IA)."""
    categoria, zona = ctx.get("categoria", "la categoría"), ctx.get("zona", "la zona")
    e = ctx.get("coeficiente_elasticidad")
    e_txt = f"{abs(float(e)):.1f}" if e is not None else "?"
    if not ctx.get("descuento_bloqueado"):
        escala = ctx.get("escala_recomendada") or "una promoción por volumen"
        inc = ctx.get("incremento_volumen_esperado_pct")
        inc_txt = f" Esperamos que tus ventas en unidades crezcan alrededor de {float(inc):.0f}%." if inc is not None else ""
        return (f"En la zona {zona}, los tenderos son sensibles al precio de {categoria}: si el precio baja un 1%, "
                f"compran cerca de {e_txt}% más. Te recomendamos una promoción por volumen: {escala}.{inc_txt}")
    alternativas = ctx.get("alternativas_sin_descuento") or ["exhibidores en las tiendas", "paquetes con productos complementarios"]
    return (f"En la zona {zona}, las ventas de {categoria} casi no cambian con el precio, así que un descuento solo "
            f"reduciría tu margen. Mantén el precio y activa la demanda con: {', '.join(alternativas).lower()}.")


def construir_prompt(ctx: Dict[str, Any]) -> str:
    return (
        "Eres un asesor comercial de una red de distribuidores que venden a tiendas de barrio en Colombia. "
        "Con base en el siguiente diagnóstico estadístico en JSON, escribe una recomendación de máximo 4 frases, "
        "en español claro, para un proveedor que no es experto en estadística. No uses términos técnicos como "
        "'elasticidad' ni 'coeficiente'; explica qué hacer con el precio o la promoción y por qué. "
        "Si 'descuento_bloqueado' es true, NO recomiendes descuentos.\n\n"
        f"Diagnóstico: {json.dumps(ctx, ensure_ascii=False)}"
    )


def llamar_gemini(ctx: Dict[str, Any], api_key: Optional[str] = None, modelo: Optional[str] = None,
                  timeout: Optional[float] = None) -> str:
    """Llama a la API de Gemini (nivel gratuito). Lanza excepción si no hay llave, falla o excede el tiempo."""
    api_key = api_key or os.environ.get("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY no configurada")
    modelo = modelo or os.environ.get("GEMINI_MODEL", MODELO_POR_DEFECTO)
    timeout = timeout or float(os.environ.get("LLM_TIMEOUT_SEGUNDOS", TIMEOUT_POR_DEFECTO))
    respuesta = requests.post(
        URL_GEMINI.format(modelo=modelo),
        params={"key": api_key},
        json={"contents": [{"parts": [{"text": construir_prompt(ctx)}]}]},
        timeout=timeout,
    )
    respuesta.raise_for_status()
    texto = respuesta.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
    if not texto:
        raise RuntimeError("El modelo devolvió una respuesta vacía")
    return texto


def generar_recomendacion(ctx: Dict[str, Any], llamar_llm: Callable[[Dict[str, Any]], str] = llamar_gemini) -> Dict[str, Any]:
    """
    Criterio 1: si el modelo responde, publica la recomendación narrativa.
    Criterio 2: si no responde o excede el tiempo límite, conserva la matriz numérica y muestra el diagnóstico crudo
    con el mensaje "Recomendación narrativa no disponible temporalmente" (más un resumen por plantilla, N-12).
    """
    try:
        narrativa = llamar_llm(ctx)
        return {"estado": "GENERADA", "fuente": "IA_GENERATIVA",
                "modelo": os.environ.get("GEMINI_MODEL", MODELO_POR_DEFECTO),
                "narrativa": narrativa, "resumen_plantilla": generar_narrativa_plantilla(ctx),
                "mensaje": None, "error": None, "matriz": ctx}
    except Exception as ex:  # noqa: BLE001 - cualquier fallo de la IA activa el respaldo
        return {"estado": "NARRATIVA_NO_DISPONIBLE", "fuente": "PLANTILLA", "modelo": None,
                "narrativa": None, "resumen_plantilla": generar_narrativa_plantilla(ctx),
                "mensaje": MENSAJE_NO_DISPONIBLE, "error": type(ex).__name__, "matriz": ctx}
