"""Resumen estadístico de solicitudes y técnicos."""
from collections import Counter
from dataclasses import asdict, dataclass

from modelos import Categoria, Estado, Prioridad
from reglas import redondear


@dataclass(frozen=True)
class CargaTecnico:
    id: str
    nombre: str
    carga: int
    capacidad: int


@dataclass(frozen=True)
class Resumen:
    total: int
    en_cola: int
    por_estado: dict
    por_categoria: dict
    por_prioridad: dict
    tiempo_promedio_horas: float
    cumplimiento_plazo_pct: float
    carga_tecnicos: list

    def a_dict(self):
        return asdict(self)


def generar_resumen(solicitudes, tecnicos):
    asignadas = [s for s in solicitudes if s.tiempo_estimado_horas is not None]
    promedio = sum(s.tiempo_estimado_horas for s in asignadas) / len(asignadas) if asignadas else 0.0
    cumplen = sum(1 for s in asignadas if s.cumple_plazo())
    porcentaje = 100.0 * cumplen / len(asignadas) if asignadas else 0.0

    return Resumen(
        total=len(solicitudes),
        en_cola=sum(1 for s in solicitudes if s.estado == Estado.PENDIENTE),
        por_estado=_contar_por(solicitudes, lambda s: s.estado.etiqueta, [e.etiqueta for e in Estado]),
        por_categoria=_contar_por(solicitudes, lambda s: s.categoria.etiqueta, [c.etiqueta for c in Categoria]),
        por_prioridad=_contar_por(solicitudes, lambda s: s.prioridad.etiqueta, [p.etiqueta for p in Prioridad]),
        tiempo_promedio_horas=redondear(promedio, 2),
        cumplimiento_plazo_pct=redondear(porcentaje, 1),
        carga_tecnicos=[CargaTecnico(t.id, t.nombre, t.carga, t.capacidad) for t in tecnicos],
    )


def _contar_por(solicitudes, clave, valores_posibles):
    """Cuenta solicitudes según 'clave' (función de orden superior), incluyendo los valores en cero."""
    conteo = Counter(clave(s) for s in solicitudes)
    return {valor: conteo.get(valor, 0) for valor in valores_posibles}


def formatear(resumen):
    """Texto legible del resumen, para la interfaz o la consola."""
    def linea(titulo, datos):
        return f"{titulo}: " + ", ".join(f"{k}={v}" for k, v in datos.items())

    return "\n".join([
        f"Total de solicitudes: {resumen.total}",
        f"En cola (sin técnico): {resumen.en_cola}",
        linea("Por estado", resumen.por_estado),
        linea("Por prioridad", resumen.por_prioridad),
        linea("Por categoría", resumen.por_categoria),
        f"Tiempo estimado promedio: {resumen.tiempo_promedio_horas} h",
        f"Cumplimiento del plazo (asignadas): {resumen.cumplimiento_plazo_pct} %",
    ])
