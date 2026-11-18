"""Lectura y escritura de archivos JSON."""
import json
from dataclasses import dataclass

from errores import ErrorArchivo, ErrorValidacion
from validacion import validar_solicitud, validar_tecnico


@dataclass(frozen=True)
class DatosCargados:
    """Los registros inválidos no detienen la carga: se omiten y se reportan en `errores`."""
    tecnicos: list
    solicitudes: list
    errores: list


def cargar_datos(ruta):
    try:
        with open(ruta, "r", encoding="utf-8") as archivo:
            raiz = json.load(archivo)
    except FileNotFoundError:
        raise ErrorArchivo(f"No se encontró el archivo: {ruta}") from None
    except json.JSONDecodeError as error:
        raise ErrorArchivo(f"JSON mal formado en {ruta}: {error}") from None
    except (OSError, UnicodeDecodeError) as error:
        raise ErrorArchivo(f"No se pudo leer {ruta}: {error}") from None

    if not (isinstance(raiz, dict) and isinstance(raiz.get("tecnicos"), list)
            and isinstance(raiz.get("solicitudes"), list)):
        raise ErrorArchivo("El archivo debe contener las listas 'tecnicos' y 'solicitudes'")

    errores = []
    tecnicos = _validar_lista(raiz["tecnicos"], validar_tecnico, "Técnico", errores)
    solicitudes = _validar_lista(raiz["solicitudes"], validar_solicitud, "Solicitud", errores)
    return DatosCargados(tecnicos, solicitudes, errores)


def _validar_lista(registros, validar, nombre, errores):
    validos, ids = [], set()
    for posicion, datos in enumerate(registros, start=1):
        try:
            elemento = validar(datos)
            if elemento.id in ids:
                raise ErrorValidacion(f"Id de {nombre.lower()} repetido: {elemento.id}")
            ids.add(elemento.id)
            validos.append(elemento)
        except ErrorValidacion as error:
            errores.append(f"{nombre} #{posicion}: {error}")
    return validos


def guardar_resultados(ruta, solicitudes, tecnicos, resumen):
    resultado = {
        "resumen": resumen.a_dict(),
        "tecnicos": [_tecnico_a_dict(t) for t in tecnicos],
        "solicitudes": [_solicitud_a_dict(s) for s in sorted(solicitudes, key=lambda s: s.clave_urgencia())],
    }
    # El resumen usa 'carga_tecnicos' igual que en Java y C#.
    try:
        with open(ruta, "w", encoding="utf-8") as archivo:
            json.dump(resultado, archivo, indent=2, ensure_ascii=False)
    except OSError as error:
        raise ErrorArchivo(f"No se pudo escribir {ruta}: {error}") from None


def _solicitud_a_dict(s):
    return {
        "id": s.id, "titulo": s.titulo, "descripcion": s.descripcion,
        "categoria": s.categoria.etiqueta, "prioridad": s.prioridad.etiqueta,
        "fecha_creacion": s.fecha_creacion.isoformat(), "estado": s.estado.etiqueta,
        "tecnico_id": s.tecnico_id, "tiempo_estimado_horas": s.tiempo_estimado_horas,
        "cumple_plazo": s.cumple_plazo(),
    }


def _tecnico_a_dict(t):
    return {"id": t.id, "nombre": t.nombre, "especialidades": [c.etiqueta for c in t.especialidades],
            "capacidad": t.capacidad, "carga": t.carga}
