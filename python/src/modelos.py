"""Modelos del dominio: enumeraciones, solicitudes y técnicos."""
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional

from errores import ErrorSoporte, ErrorValidacion


class _EnumTexto(Enum):
    """Enumeración con etiqueta de texto y conversión desde texto."""

    def __str__(self):
        return self.etiqueta

    @classmethod
    def desde_texto(cls, texto):
        if isinstance(texto, str):
            normalizado = texto.strip().lower()
            for miembro in cls:
                if miembro.etiqueta == normalizado:
                    return miembro
        raise ErrorValidacion(f"{cls.__name__} inválida: {texto}")


class Categoria(_EnumTexto):
    HARDWARE = "hardware"
    SOFTWARE = "software"
    REDES = "redes"
    ACCESOS = "accesos"
    OTRO = "otro"

    @property
    def etiqueta(self):
        return self.value


class Estado(_EnumTexto):
    PENDIENTE = "pendiente"
    ASIGNADA = "asignada"
    RESUELTA = "resuelta"

    @property
    def etiqueta(self):
        return self.value


class Prioridad(_EnumTexto):
    """Cada prioridad guarda (etiqueta, orden de atención, horas máximas de respuesta)."""
    CRITICA = ("critica", 0, 1)
    ALTA = ("alta", 1, 2)
    MEDIA = ("media", 2, 8)
    BAJA = ("baja", 3, 24)

    @property
    def etiqueta(self):
        return self.value[0]

    @property
    def orden(self):
        return self.value[1]

    @property
    def horas_maximas(self):
        return self.value[2]


@dataclass
class Solicitud:
    id: str
    titulo: str
    descripcion: str
    categoria: Categoria
    prioridad: Prioridad
    fecha_creacion: datetime
    estado: Estado = Estado.PENDIENTE
    tecnico_id: Optional[str] = None
    tiempo_estimado_horas: Optional[float] = None

    def clave_urgencia(self):
        """Más urgente primero; a igual prioridad, la más antigua; el id desempata."""
        return (self.prioridad.orden, self.fecha_creacion, self.id)

    def cumple_plazo(self):
        """None mientras no tenga tiempo estimado (no está asignada)."""
        if self.tiempo_estimado_horas is None:
            return None
        return self.tiempo_estimado_horas <= self.prioridad.horas_maximas


@dataclass
class Tecnico:
    """Técnico con capacidad máxima de solicitudes activas.

    Sin especialidades significa generalista: puede atender cualquier categoría.
    """
    id: str
    nombre: str
    especialidades: list = field(default_factory=list)
    capacidad: int = 2
    activas: list = field(default_factory=list)

    @property
    def carga(self):
        return len(self.activas)

    @property
    def esta_disponible(self):
        return self.carga < self.capacidad

    @property
    def es_generalista(self):
        return not self.especialidades

    def puede_atender(self, categoria):
        return self.es_generalista or categoria in self.especialidades

    def asignar(self, id_solicitud):
        if not self.esta_disponible:
            raise ErrorSoporte(f"El técnico {self.id} ya alcanzó su capacidad máxima")
        self.activas.append(id_solicitud)

    def liberar(self, id_solicitud):
        if id_solicitud not in self.activas:
            raise ErrorSoporte(f"El técnico {self.id} no tiene asignada la solicitud {id_solicitud}")
        self.activas.remove(id_solicitud)
