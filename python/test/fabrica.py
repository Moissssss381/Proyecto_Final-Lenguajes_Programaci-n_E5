"""Crea objetos de prueba; 'minutos' desplaza la fecha para controlar la antigüedad."""
from datetime import datetime, timedelta

from modelos import Categoria, Prioridad, Solicitud, Tecnico

BASE = datetime(2026, 11, 2, 8, 0)


def solicitud(id_, prioridad=Prioridad.MEDIA, categoria=Categoria.SOFTWARE, minutos=0):
    return Solicitud(id_, f"Solicitud {id_}", "", categoria, prioridad, BASE + timedelta(minutes=minutos))


def tecnico(id_, capacidad=2, *especialidades):
    return Tecnico(id_, f"Técnico {id_}", list(especialidades), capacidad)
