"""Reglas de negocio: tiempo estimado de atención y elección del técnico."""
import math

from modelos import Categoria

# Horas base de atención por categoría (supuesto del proyecto, ajustable).
HORAS_BASE = {
    Categoria.ACCESOS: 0.5,
    Categoria.SOFTWARE: 1.0,
    Categoria.OTRO: 1.0,
    Categoria.HARDWARE: 1.5,
    Categoria.REDES: 2.0,
}

# Cada solicitud activa del técnico aumenta el tiempo estimado en este porcentaje.
FACTOR_POR_CARGA = 0.25


def redondear(valor, decimales):
    """Redondeo 'mitad hacia arriba', igual que Java (Math.round) y C# (AwayFromZero)."""
    factor = 10 ** decimales
    return math.floor(valor * factor + 0.5) / factor


def calcular_tiempo_estimado(categoria, carga_tecnico):
    """tiempo = horas_base * (1 + 0.25 * solicitudes_activas_del_tecnico)."""
    return redondear(HORAS_BASE[categoria] * (1 + FACTOR_POR_CARGA * carga_tecnico), 2)


def elegir_tecnico(tecnicos, categoria):
    """Entre los técnicos disponibles que atienden la categoría elige el de menor carga.

    A igual carga prefiere al especialista sobre el generalista y el id desempata.
    Devuelve None si no hay ninguno.
    """
    candidatos = [t for t in tecnicos if t.esta_disponible and t.puede_atender(categoria)]
    return min(candidatos, key=lambda t: (t.carga, t.es_generalista, t.id), default=None)
