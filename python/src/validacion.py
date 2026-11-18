"""Validación de los datos de entrada leídos desde JSON."""
from datetime import datetime

from errores import ErrorValidacion
from modelos import Categoria, Prioridad, Solicitud, Tecnico

LONGITUD_MAXIMA_TITULO = 120
CAPACIDAD_MAXIMA = 10


def validar_solicitud(datos):
    if not isinstance(datos, dict):
        raise ErrorValidacion("La solicitud debe ser un objeto JSON")
    descripcion = datos.get("descripcion")
    return Solicitud(
        id=_texto_obligatorio(datos, "id"),
        titulo=_texto_obligatorio(datos, "titulo", LONGITUD_MAXIMA_TITULO),
        descripcion="" if descripcion is None else str(descripcion).strip(),
        categoria=Categoria.desde_texto(datos.get("categoria")),
        prioridad=Prioridad.desde_texto(datos.get("prioridad")),
        fecha_creacion=_fecha_iso(datos.get("fecha_creacion")),
    )


def validar_tecnico(datos):
    if not isinstance(datos, dict):
        raise ErrorValidacion("El técnico debe ser un objeto JSON")
    return Tecnico(
        id=_texto_obligatorio(datos, "id"),
        nombre=_texto_obligatorio(datos, "nombre"),
        especialidades=_especialidades(datos),
        capacidad=_capacidad(datos),
    )


def _texto_obligatorio(datos, campo, longitud_maxima=None):
    valor = datos.get(campo)
    if not isinstance(valor, str) or not valor.strip():
        raise ErrorValidacion(f"El campo '{campo}' es obligatorio y debe ser texto")
    texto = valor.strip()
    if longitud_maxima is not None and len(texto) > longitud_maxima:
        raise ErrorValidacion(f"El campo '{campo}' excede {longitud_maxima} caracteres")
    return texto


def _fecha_iso(texto):
    if texto is None:
        raise ErrorValidacion("El campo 'fecha_creacion' es obligatorio (formato ISO 8601)")
    try:
        fecha = datetime.fromisoformat(str(texto).replace("Z", "+00:00"))
    except ValueError:
        raise ErrorValidacion(f"Fecha inválida: {texto}") from None
    if fecha.tzinfo is not None:  # con zona horaria: se normaliza a hora local sin zona para poder comparar
        fecha = fecha.astimezone().replace(tzinfo=None)
    return fecha


def _capacidad(datos):
    if "capacidad" not in datos:
        return 2
    valor = datos["capacidad"]
    es_numero = isinstance(valor, (int, float)) and not isinstance(valor, bool)
    if es_numero and float(valor).is_integer() and 1 <= valor <= CAPACIDAD_MAXIMA:
        return int(valor)
    raise ErrorValidacion(f"La capacidad debe ser un entero entre 1 y {CAPACIDAD_MAXIMA}")


def _especialidades(datos):
    valor = datos.get("especialidades")
    if valor is None:
        return []
    if not isinstance(valor, list):
        raise ErrorValidacion("'especialidades' debe ser una lista")
    return [Categoria.desde_texto(elemento) for elemento in valor]
