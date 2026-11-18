import pytest

import fabrica
from despachador import Despachador
from errores import ErrorValidacion
from modelos import Categoria, Estado, Prioridad
from reglas import calcular_tiempo_estimado, elegir_tecnico


def test_las_criticas_se_asignan_primero():
    despachador = Despachador([fabrica.tecnico("T1", 1)], 4)
    baja = fabrica.solicitud("BAJA", Prioridad.BAJA, minutos=0)        # llegó antes
    critica = fabrica.solicitud("CRIT", Prioridad.CRITICA, minutos=60)  # llegó después
    despachador.registrar(baja)
    despachador.registrar(critica)
    despachador.procesar_pendientes()
    assert critica.estado == Estado.ASIGNADA
    assert baja.estado == Estado.PENDIENTE


def test_a_igual_prioridad_se_atiende_la_mas_antigua():
    despachador = Despachador([fabrica.tecnico("T1", 1)], 2)
    nueva = fabrica.solicitud("NUEVA", minutos=30)
    vieja = fabrica.solicitud("VIEJA", minutos=0)
    despachador.registrar(nueva)
    despachador.registrar(vieja)
    despachador.procesar_pendientes()
    assert vieja.estado == Estado.ASIGNADA
    assert nueva.estado == Estado.PENDIENTE


def test_sin_tecnico_disponible_la_solicitud_permanece_en_cola():
    despachador = Despachador([fabrica.tecnico("T1", 2, Categoria.HARDWARE)], 2)
    solicitud = fabrica.solicitud("S1", categoria=Categoria.REDES)  # nadie atiende redes
    despachador.registrar(solicitud)
    assert despachador.procesar_pendientes() == 0
    assert solicitud.estado == Estado.PENDIENTE
    assert solicitud.tecnico_id is None


def test_resolver_libera_al_tecnico_y_reasigna_la_cola():
    despachador = Despachador([fabrica.tecnico("T1", 1)], 2)
    primera = fabrica.solicitud("S1", minutos=0)
    segunda = fabrica.solicitud("S2", minutos=1)
    despachador.registrar(primera)
    despachador.registrar(segunda)
    despachador.procesar_pendientes()
    assert segunda.estado == Estado.PENDIENTE
    despachador.resolver("S1")
    assert primera.estado == Estado.RESUELTA
    assert segunda.estado == Estado.ASIGNADA


def test_no_se_puede_registrar_ni_resolver_lo_invalido():
    despachador = Despachador([fabrica.tecnico("T1", 2)], 2)
    despachador.registrar(fabrica.solicitud("S1"))
    with pytest.raises(ErrorValidacion):
        despachador.registrar(fabrica.solicitud("S1", minutos=5))
    with pytest.raises(ErrorValidacion):
        despachador.resolver("NO-EXISTE")
    with pytest.raises(ErrorValidacion):
        despachador.resolver("S1")  # todavía no está asignada


def test_se_elige_al_tecnico_de_menor_carga():
    ocupado, libre = fabrica.tecnico("T1", 3), fabrica.tecnico("T2", 3)
    ocupado.asignar("X")
    assert elegir_tecnico([ocupado, libre], Categoria.SOFTWARE) is libre


def test_se_prefiere_especialista_sobre_generalista_con_igual_carga():
    generalista = fabrica.tecnico("T1", 2)
    especialista = fabrica.tecnico("T2", 2, Categoria.REDES)
    assert elegir_tecnico([generalista, especialista], Categoria.REDES) is especialista


def test_formula_del_tiempo_estimado_crece_con_la_carga():
    assert calcular_tiempo_estimado(Categoria.REDES, 0) == 2.0
    assert calcular_tiempo_estimado(Categoria.REDES, 2) == 3.0
    assert calcular_tiempo_estimado(Categoria.ACCESOS, 1) == 0.63  # 0.625 redondea hacia arriba


def test_siguiente_id_continua_la_numeracion():
    despachador = Despachador([fabrica.tecnico("T1")], 2)
    assert despachador.siguiente_id() == "S0001"
    despachador.registrar(fabrica.solicitud("S0007"))
    despachador.registrar(fabrica.solicitud("X1", minutos=1))  # ids ajenos a S#### se ignoran
    assert despachador.siguiente_id() == "S0008"


def test_plazos_por_prioridad():
    assert [p.horas_maximas for p in Prioridad] == [1, 2, 8, 24]


def test_cumple_plazo():
    s = fabrica.solicitud("S1", Prioridad.ALTA)
    assert s.cumple_plazo() is None
    s.tiempo_estimado_horas = 2.0
    assert s.cumple_plazo() is True
    s.tiempo_estimado_horas = 2.01
    assert s.cumple_plazo() is False


def test_num_hilos_debe_ser_positivo():
    with pytest.raises(ValueError):
        Despachador([], 0)


def test_concurrencia_nunca_sobrepasa_la_capacidad_ni_duplica_asignaciones():
    tecnicos = [fabrica.tecnico(f"T{i}", 3) for i in range(5)]  # 15 cupos en total
    despachador = Despachador(tecnicos, 8)
    solicitudes = [fabrica.solicitud(f"S{i:03d}", minutos=i) for i in range(200)]
    for s in solicitudes:
        despachador.registrar(s)
    assert despachador.procesar_pendientes() == 15
    assert all(t.carga <= t.capacidad for t in tecnicos)
    activos = [i for t in tecnicos for i in t.activas]
    assert len(activos) == 15 and len(set(activos)) == 15
    assert sum(1 for s in solicitudes if s.estado == Estado.PENDIENTE) == 185
