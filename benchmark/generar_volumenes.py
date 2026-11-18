"""Genera conjuntos de datos sintéticos (mismo formato que datos/) para medir tiempos con volúmenes grandes.

Es determinista (semilla fija), así que los tres lenguajes procesan exactamente los mismos datos.
Uso:  python benchmark/generar_volumenes.py
Salida: benchmark/volumenes/solicitudes_<N>.json   (no se versionan: se regeneran con este script)
"""
import json
import random
from datetime import datetime, timedelta
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
TAMANOS = [1_000, 5_000, 20_000, 50_000]
CATEGORIAS = ["hardware", "software", "redes", "accesos", "otro"]
PRIORIDADES = ["critica", "alta", "media", "baja"]
PESOS_PRIORIDAD = [5, 20, 45, 30]


def generar(n, tecnicos):
    azar = random.Random(2026)
    base = datetime(2026, 11, 2, 8, 0, 0)
    solicitudes = []
    for i in range(1, n + 1):
        solicitudes.append({
            "id": f"S{i:05d}",
            "titulo": f"Solicitud sintética {i}",
            "descripcion": "Generada para el benchmark",
            "categoria": azar.choice(CATEGORIAS),
            "prioridad": azar.choices(PRIORIDADES, PESOS_PRIORIDAD)[0],
            "fecha_creacion": (base + timedelta(minutes=azar.randint(0, 60 * 24 * 5))).isoformat(),
        })
    return {"tecnicos": tecnicos, "solicitudes": solicitudes}


def main():
    modelo = json.loads((RAIZ.parent / "datos" / "solicitudes_grandes.json").read_text(encoding="utf-8"))
    # Mismos 12 técnicos del dataset grande, todos con capacidad 10 (120 cupos): el trabajo crece con el número de solicitudes.
    tecnicos = [{**t, "capacidad": 10} for t in modelo["tecnicos"]]
    destino = RAIZ / "volumenes"
    destino.mkdir(exist_ok=True)
    for n in TAMANOS:
        ruta = destino / f"solicitudes_{n}.json"
        ruta.write_text(json.dumps(generar(n, tecnicos), ensure_ascii=False), encoding="utf-8")
        print(f"{ruta.name}: {n} solicitudes, {len(tecnicos)} técnicos")


if __name__ == "__main__":
    main()
