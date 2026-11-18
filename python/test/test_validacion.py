import copy

import pytest

from errores import ErrorValidacion
from modelos import Categoria, Prioridad
from validacion import validar_solicitud, validar_tecnico

VALIDA = {"id": "S1", "titulo": "Sin internet", "descripcion": "", "categoria": "REDES",
          "prioridad": "ALTA", "fecha_creacion": "2026-11-02T08:30:00"}


def test_solicitud_valida_se_convierte_a_objeto():
    solicitud = validar_solicitud(VALIDA)
    assert solicitud.categoria == Categoria.REDES
    assert solicitud.prioridad == Prioridad.ALTA  # no distingue mayúsculas


@pytest.mark.parametrize("campo,valor", [("titulo", "   "), ("prioridad", "urgentisima"),
                                         ("categoria", "cafeteria"), ("fecha_creacion", "ayer")])
def test_solicitud_con_dato_invalido_lanza_error_validacion(campo, valor):
    datos = copy.deepcopy(VALIDA)
    datos[campo] = valor
    with pytest.raises(ErrorValidacion):
        validar_solicitud(datos)


def test_solicitud_sin_id_o_que_no_es_objeto_lanza_error():
    sin_id = {k: v for k, v in VALIDA.items() if k != "id"}
    with pytest.raises(ErrorValidacion):
        validar_solicitud(sin_id)
    with pytest.raises(ErrorValidacion):
        validar_solicitud("texto")


def test_titulo_demasiado_largo():
    with pytest.raises(ErrorValidacion):
        validar_solicitud({**VALIDA, "titulo": "x" * 121})


def test_tecnico_valido():
    tecnico = validar_tecnico({"id": "T1", "nombre": "Ana", "especialidades": ["redes"], "capacidad": 3})
    assert tecnico.capacidad == 3
    assert tecnico.especialidades == [Categoria.REDES]
    assert validar_tecnico({"id": "T2", "nombre": "Luis"}).capacidad == 2  # valor por defecto


@pytest.mark.parametrize("capacidad", [0, 11, True, "2", 2.5, None])
def test_tecnico_con_capacidad_invalida(capacidad):
    with pytest.raises(ErrorValidacion):
        validar_tecnico({"id": "T1", "nombre": "Ana", "capacidad": capacidad})
