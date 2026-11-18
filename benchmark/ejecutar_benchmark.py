"""Ejecuta el benchmark de cada lenguaje y guarda los resultados en benchmark/resultados.json.

Uso (desde la raíz del repositorio):
    python benchmark/generar_volumenes.py              # una sola vez
    python benchmark/ejecutar_benchmark.py             # Python, Java y C# (los que estén instalados)
    python benchmark/ejecutar_benchmark.py --lenguajes csharp   # solo C# (se mezcla con lo ya medido)

Requisitos: Python 3.10+; Java 17+ y Maven (o --gson ruta/gson.jar); .NET 8 SDK para C#.
El tablero benchmark/tablero.html lee resultados.json.
"""
import argparse
import json
import platform
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
BENCH = RAIZ / "benchmark"
DATASETS = [RAIZ / "datos" / f"solicitudes_{n}.json" for n in ("pequenas", "medias", "grandes")]
HILOS = "1,2,4,8"


def conjuntos():
    volumenes = sorted((BENCH / "volumenes").glob("solicitudes_*.json"), key=lambda p: int(p.stem.split("_")[1]))
    return DATASETS + volumenes


def ejecutar(comando, **kw):
    return subprocess.run(comando, capture_output=True, text=True, check=True, **kw)


def lineas_json(salida):
    return [json.loads(l) for l in salida.splitlines() if l.startswith("{")]


def medir_python(repeticiones):
    resultados = []
    for ruta in conjuntos():
        salida = ejecutar([sys.executable, str(RAIZ / "python" / "src" / "benchmark.py"), str(ruta),
                           "--hilos", HILOS, "--repeticiones", str(repeticiones)], cwd=RAIZ / "python" / "src").stdout
        resultados += lineas_json(salida)
    return resultados, platform.python_version()


def medir_java(repeticiones, gson):
    java = RAIZ / "java"
    salida_clases = Path(tempfile.mkdtemp(prefix="policode-java-"))
    if gson:
        cp = str(gson)
        fuentes = [str(p) for p in (java / "src").rglob("*.java")]
        ejecutar(["javac", "-encoding", "UTF-8", "-d", str(salida_clases), "-cp", cp, *fuentes])
    else:
        ejecutar(["mvn", "-q", "compile"], cwd=java)
        archivo_cp = salida_clases / "cp.txt"
        ejecutar(["mvn", "-q", "dependency:build-classpath", f"-Dmdep.outputFile={archivo_cp}"], cwd=java)
        cp = archivo_cp.read_text().strip()
        salida_clases = java / "target" / "classes"
    resultados = []
    for ruta in conjuntos():
        salida = ejecutar(["java", "-cp", f"{salida_clases}{';' if platform.system() == 'Windows' else ':'}{cp}",
                           "com.policode.soporte.Benchmark", str(ruta), HILOS, str(repeticiones)]).stdout
        resultados += lineas_json(salida)
    lineas = subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()
    version = next((l for l in lineas if "version" in l and not l.startswith("Picked up")), "desconocida")
    return resultados, version


def medir_csharp(repeticiones):
    resultados = []
    for ruta in conjuntos():
        salida = ejecutar(["dotnet", "run", "-c", "Release", "--project", "csharp/src/Soporte.Benchmark", "--",
                           str(ruta), HILOS, str(repeticiones)], cwd=RAIZ).stdout
        resultados += lineas_json(salida)
    version = subprocess.run(["dotnet", "--version"], capture_output=True, text=True).stdout.strip()
    return resultados, version


def contar_lineas(rutas, estilo):
    """Líneas de código aproximadas: sin líneas vacías, comentarios ni docstrings."""
    total = 0
    for ruta in rutas:
        en_bloque = False
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            t = linea.strip()
            if not t:
                continue
            if estilo == "python":
                if en_bloque:
                    en_bloque = '"""' not in t
                    continue
                if t.startswith('"""'):
                    en_bloque = t.count('"""') == 1
                    continue
                if t.startswith("#"):
                    continue
            else:
                if en_bloque:
                    en_bloque = "*/" not in t
                    continue
                if t.startswith("/*"):
                    en_bloque = "*/" not in t
                    continue
                if t.startswith("//") or t.startswith("*"):
                    continue
            total += 1
    return total


def lineas_de_codigo():
    def archivos(base, patron, excluir=()):
        return [p for p in base.rglob(patron)
                if not any(x in p.parts for x in ("obj", "bin", "target", ".vs")) and p.name not in excluir]

    py = archivos(RAIZ / "python", "*.py")
    ja = archivos(RAIZ / "java", "*.java")
    cs = archivos(RAIZ / "csharp", "*.cs")
    def es_prueba(p): return "tests" in p.parts
    def es_bench(p): return p.name in ("benchmark.py", "Benchmark.java") or "Soporte.Benchmark" in p.parts
    def es_gui(p): return p.name in ("interfaz.py", "VentanaPrincipal.java", "PanelBarras.cs", "VentanaPrincipal.cs", "Program.cs") and "Soporte.Benchmark" not in p.parts
    def es_gui_java(p): return p.name in ("VentanaPrincipal.java", "Principal.java")
    resultado = {}
    for nombre, lista, estilo, gui in (("python", py, "python", es_gui), ("java", ja, "otro", es_gui_java), ("csharp", cs, "otro", es_gui)):
        nucleo = [p for p in lista if not es_prueba(p) and not es_bench(p) and not gui(p)]
        interfaz = [p for p in lista if not es_prueba(p) and not es_bench(p) and gui(p)]
        pruebas = [p for p in lista if es_prueba(p)]
        resultado[nombre] = {"nucleo": contar_lineas(nucleo, estilo), "interfaz": contar_lineas(interfaz, estilo),
                             "pruebas": contar_lineas(pruebas, estilo), "archivos_nucleo": len(nucleo)}
    return resultado


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--lenguajes", default="python,java,csharp")
    parser.add_argument("--repeticiones", type=int, default=5)
    parser.add_argument("--gson", type=Path, help="ruta a gson.jar para compilar Java sin Maven")
    args = parser.parse_args()

    destino = BENCH / "resultados.json"
    datos = json.loads(destino.read_text(encoding="utf-8")) if destino.exists() else {"mediciones": [], "entornos": {}}
    medidores = {"python": lambda: medir_python(args.repeticiones),
                 "java": lambda: medir_java(args.repeticiones, args.gson),
                 "csharp": lambda: medir_csharp(args.repeticiones)}
    ejecutables = {"python": "python3", "java": "javac" if args.gson else "mvn", "csharp": "dotnet"}

    for lenguaje in args.lenguajes.split(","):
        if lenguaje not in medidores:
            continue
        if lenguaje != "python" and not shutil.which(ejecutables[lenguaje]):
            print(f"[{lenguaje}] omitido: no se encontró '{ejecutables[lenguaje]}' en el PATH")
            continue
        print(f"[{lenguaje}] midiendo…", flush=True)
        mediciones, version = medidores[lenguaje]()
        datos["mediciones"] = [m for m in datos["mediciones"] if m["lenguaje"] != lenguaje] + mediciones
        datos["entornos"][lenguaje] = {"version": version, "sistema": f"{platform.system()} {platform.release()}",
                                       "procesadores": __import__("os").cpu_count(),
                                       "fecha": datetime.now().isoformat(timespec="seconds")}
    datos["lineas_de_codigo"] = lineas_de_codigo()
    datos["repeticiones"] = args.repeticiones
    datos["hilos"] = [int(h) for h in HILOS.split(",")]
    destino.write_text(json.dumps(datos, indent=2, ensure_ascii=False), encoding="utf-8")
    # El tablero se abre con doble clic (file://), donde no se puede leer JSON con fetch: se publica también como .js
    (BENCH / "resultados.js").write_text("window.RESULTADOS = " + json.dumps(datos, ensure_ascii=False) + ";\n", encoding="utf-8")
    print(f"Resultados guardados en {destino}")


if __name__ == "__main__":
    main()
