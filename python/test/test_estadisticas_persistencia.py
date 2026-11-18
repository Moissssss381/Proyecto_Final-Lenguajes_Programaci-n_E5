import json
from pathlib import Path

import pytest

import fabrica
from despachador import Despachador
from errores import ErrorArchivo
from estadisticas import formatear, generar_resumen
from modelos import Categoria, Prioridad
from persistencia import cargar_datos, guardar_resultados

DATOS = Path(__file__).resolve().parents[2] / "datos"


def test_resumen_cuenta_estados_y_calcula_promedio():
    tecnicos = [fabrica.tecnico("T1", 2, Categoria.REDES)]
    despachador = Despachador(tecnicos, 2)
    despachador.registrar(fabrica.solicitud("S1", Prioridad.ALTA, Categoria.REDES, 0))     # 2.0 h, cumple
    despachador.registrar(fabrica.solicitud("S2", Prioridad.CRITICA, Categoria.REDES, 1))  # 2.0 * 1.25 = 2.5 h, no cumple
    despachador.registrar(fabrica.solicitud("S3", Prioridad.BAJA, Categoria.HARDWARE, 2))  # nadie la atiende
    despachador.procesar_pendientes()
    resumen = generar_resumen(despachador.solicitudes, tecnicos)
    assert resumen.total == 3 and resumen.en_cola == 1
    assert resumen.por_estado == {"pendiente": 1, "asignada": 2, "resuelta": 0}
    assert resumen.por_prioridad["critica"] == 1
    assert resumen.tiempo_promedio_horas == 2.25
    assert resumen.cumplimiento_plazo_pct == 0.0
    assert "Total de solicitudes: 3" in formatear(resumen)


def test_resumen_vacio_no_divide_entre_cero():
    resumen = generar_resumen([], [])
    assert resumen.total == 0
    assert resumen.tiempo_promedio_horas == 0.0
    assert resumen.cumplimiento_plazo_pct == 0.0


def test_cargar_omite_registros_invalidos_y_los_reporta(tmp_path):
    archivo = tmp_path / "datos.json"
    archivo.write_text(json.dumps({
        "tecnicos": [{"id": "T1", "nombre": "Ana"}, {"id": "T1", "nombre": "Repetido"}],
        "solicitudes": [
            {"id": "S1", "titulo": "Ok", "categoria": "redes", "prioridad": "alta", "fecha_creacion": "2026-11-02T08:00:00"},
            {"id": "S2", "titulo": "", "categoria": "redes", "prioridad": "alta", "fecha_creacion": "2026-11-02T08:00:00"},
        ]}), encoding="utf-8")
    datos = cargar_datos(archivo)
    assert len(datos.tecnicos) == 1 and len(datos.solicitudes) == 1
    assert len(datos.errores) == 2


def test_archivo_corrupto_o_inexistente_lanza_error_archivo(tmp_path):
    corrupto = tmp_path / "malo.json"
    corrupto.write_text("{esto no es json", encoding="utf-8")
    with pytest.raises(ErrorArchivo):
        cargar_datos(corrupto)
    with pytest.raises(ErrorArchivo):
        cargar_datos(tmp_path / "no-existe.json")
    sin_listas = tmp_path / "vacio.json"
    sin_listas.write_text("{}", encoding="utf-8")
    with pytest.raises(ErrorArchivo):
        cargar_datos(sin_listas)


def test_guardar_resultados_genera_json_legible(tmp_path):
    tecnicos = [fabrica.tecnico("T1", 1)]
    despachador = Despachador(tecnicos, 2)
    despachador.registrar(fabrica.solicitud("S1"))
    despachador.procesar_pendientes()
    salida = tmp_path / "resultados.json"
    guardar_resultados(salida, despachador.solicitudes, tecnicos, generar_resumen(despachador.solicitudes, tecnicos))
    contenido = json.loads(salida.read_text(encoding="utf-8"))
    assert contenido["resumen"]["total"] == 1
    assert contenido["solicitudes"][0]["estado"] == "asignada"
    assert contenido["tecnicos"][0]["carga"] == 1


def test_guardar_en_ruta_inexistente_lanza_error_archivo(tmp_path):
    with pytest.raises(ErrorArchivo):
        guardar_resultados(tmp_path / "no-existe" / "x.json", [], [], generar_resumen([], []))


@pytest.mark.parametrize("nombre,invalidos", [("pequenas", 0), ("medias", 2), ("grandes", 5)])
def test_los_datasets_incluidos_se_cargan(nombre, invalidos):
    datos = cargar_datos(DATOS / f"solicitudes_{nombre}.json")
    assert datos.solicitudes
    assert len(datos.errores) == invalidos
