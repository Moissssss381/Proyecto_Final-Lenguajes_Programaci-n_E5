"""Benchmark: mide carga y procesamiento con distintos volúmenes y números de hilos.

Uso:  python src/benchmark.py <archivo.json> [--hilos 1,2,4,8] [--repeticiones 5]
Imprime una línea JSON por cada cantidad de hilos. Protocolo común a los tres lenguajes:
1 ejecución de calentamiento + N repeticiones; se reporta la mediana en milisegundos.
"""
import argparse
import hashlib
import json
import statistics
import sys
import time
from pathlib import Path

from despachador import Despachador
from persistencia import cargar_datos


def huella(solicitudes):
    """SHA-256 de 'id|técnico|tiempo|estado' ordenado por id: sirve para comprobar que los 3 lenguajes dan lo mismo."""
    lineas = sorted(f"{s.id}|{s.tecnico_id or ''}|"
                    f"{'' if s.tiempo_estimado_horas is None else format(s.tiempo_estimado_horas, '.2f')}|{s.estado.etiqueta}"
                    for s in solicitudes)
    return hashlib.sha256("\n".join(lineas).encode("utf-8")).hexdigest()


def una_ejecucion(ruta, hilos):
    t0 = time.perf_counter()
    datos = cargar_datos(ruta)
    t1 = time.perf_counter()
    despachador = Despachador(datos.tecnicos, hilos)
    for solicitud in datos.solicitudes:
        despachador.registrar(solicitud)
    asignadas = despachador.procesar_pendientes()
    t2 = time.perf_counter()
    return (t1 - t0) * 1000, (t2 - t1) * 1000, asignadas, len(datos.solicitudes), len(datos.errores), huella(despachador.solicitudes)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archivo")
    parser.add_argument("--hilos", default="1,2,4,8")
    parser.add_argument("--repeticiones", type=int, default=5)
    args = parser.parse_args()
    ruta = Path(args.archivo)

    for hilos in (int(h) for h in args.hilos.split(",")):
        una_ejecucion(ruta, hilos)  # calentamiento
        medidas = [una_ejecucion(ruta, hilos) for _ in range(args.repeticiones)]
        print(json.dumps({
            "lenguaje": "python", "dataset": ruta.stem, "solicitudes": medidas[0][3], "invalidos": medidas[0][4],
            "hilos": hilos, "carga_ms": round(statistics.median(m[0] for m in medidas), 3),
            "proceso_ms": round(statistics.median(m[1] for m in medidas), 3),
            "asignadas": medidas[0][2], "huella": medidas[0][5],
        }), flush=True)


if __name__ == "__main__":
    sys.exit(main())
