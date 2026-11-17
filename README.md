# proyecto-policode

Sistema de **gestión y priorización de solicitudes de soporte técnico**, implementado con el mismo diseño en tres lenguajes (Python, Java y C#) para compararlos en el documento `documentos/analisis-comparativo.pdf`.

## Qué hace

- Lee técnicos y solicitudes desde un archivo JSON y **valida** cada registro (los inválidos se omiten y se reportan).
- **Registra** solicitudes nuevas desde la interfaz (botón *Nueva solicitud…*): el id y la fecha se generan solos y los datos se validan.
- Clasifica por **categoría** (hardware, software, redes, accesos, otro), **prioridad** (crítica, alta, media, baja) y **estado** (pendiente, asignada, resuelta).
- **Asigna** solicitudes a técnicos disponibles con varios hilos; las críticas se atienden primero y las que no tienen técnico disponible **permanecen en cola**.
- Calcula el **tiempo estimado** de atención y si cumple el plazo de su prioridad.
- Genera un **resumen estadístico**, permite **filtrar y ordenar** y muestra un **tablero** (gráfico de barras y carga de técnicos).
- Guarda los resultados en JSON.

## Qué pide el problema y dónde se cumple

| Punto del problema | Cómo se resuelve | Módulo (Python / Java / C#) |
|--------------------|------------------|------------------------------|
| Registrar solicitudes | Desde la interfaz (*Nueva solicitud…*, con id y fecha automáticos) o cargando un JSON | `interfaz` + `validacion` / `VentanaPrincipal` + `Validador` / igual |
| Clasificar por categoría, prioridad y estado | Cada solicitud lleva los tres atributos (enumeraciones); el estado cambia solo: pendiente → asignada → resuelta | `modelos` / `Categoria`, `Prioridad`, `Estado` / `Enumeraciones` |
| Asignar a técnicos disponibles | Técnico con cupo y que atiende la categoría; gana el de menor carga | `reglas.elegir_tecnico` / `Reglas.elegirTecnico` / `Reglas.ElegirTecnico` |
| Calcular tiempos estimados | `calcular_tiempo_estimado` según categoría y carga del técnico; se compara con el plazo de la prioridad | `reglas` / `Reglas` / `Reglas` |
| Generar estadísticas | Totales por estado, categoría y prioridad, tiempo promedio, cumplimiento del plazo y carga por técnico | `estadisticas` / `Estadisticas` / `Estadisticas` |
| Filtrar y ordenar | Filtros por categoría, prioridad, estado y texto; orden por cualquier columna | `interfaz` / `VentanaPrincipal` / `VentanaPrincipal` |
| Procesar varias solicitudes de forma concurrente | Pool de hilos (configurable en la interfaz) que toma de una cola de prioridad segura para hilos | `despachador` / `Despachador` / `Despachador` |
| Interfaz visual o tablero | Tabla, resumen, gráfico de barras por prioridad y carga de técnicos | `interfaz` / `VentanaPrincipal` + `PanelBarras` |

**Decisiones de diseño que conviene confirmar con el profesor**

- **Concurrencia:** los hilos toman solicitudes de la cola, pero elegir al técnico y asignarlo es una sección crítica protegida con un candado. Así dos hilos nunca ocupan el mismo cupo y las críticas se asignan antes que las demás aunque haya varios hilos. El costo es que la asignación en sí se serializa; es la solución correcta para este problema y es un buen punto de comparación entre lenguajes en el documento.
- **Plazo de respuesta:** se compara el *tiempo estimado de atención* con el *tiempo máximo de respuesta* de la prioridad. Es una simplificación; si el profesor entiende "respuesta" como el tiempo de espera hasta la asignación, solo cambia `cumple_plazo`.
- Las solicitudes registradas a mano viven en memoria: se conservan al **Guardar resultados…** y se pueden volver a cargar con **Cargar JSON…** (volverán como pendientes).

## Reglas de negocio

| Prioridad | Tiempo máximo de respuesta |
|-----------|----------------------------|
| Crítica   | 1 hora (supuesto del proyecto; el enunciado no la define) |
| Alta      | 2 horas |
| Media     | 8 horas |
| Baja      | 24 horas |

- **Orden de atención:** prioridad (crítica primero) → fecha de creación (la más antigua primero) → id.
- **Elección del técnico:** debe estar disponible (carga menor a su capacidad) y poder atender la categoría (sin especialidades = generalista, atiende todo). Gana el de menor carga; a igual carga, el especialista sobre el generalista; luego el id.
- **Tiempo estimado:** `horas_base_de_la_categoría × (1 + 0.25 × solicitudes_activas_del_técnico)`. Horas base: accesos 0.5, software 1.0, otro 1.0, hardware 1.5, redes 2.0. Es un supuesto ajustable, definido en `Reglas`.
- **Cumple plazo:** el tiempo estimado es menor o igual al tiempo máximo de su prioridad.
- Al **resolver** una solicitud se libera al técnico y se reasigna la cola en espera.

## Estructura

```
proyecto-policode/
├── README.md
├── datos/                      solicitudes_pequenas / medias / grandes (.json)
├── python/   src/ tests/ requirements.txt
├── java/     src/ tests/ pom.xml
├── csharp/   src/ tests/ Soporte.sln
├── documentos/                 análisis comparativo, manual técnico, diagramas
└── evidencias/                 capturas/ y videos/
```

Los tres lenguajes comparten los mismos módulos: modelos, validación, reglas, despachador (concurrencia), estadísticas, persistencia (JSON) e interfaz gráfica.

## Datos de ejemplo

| Archivo | Técnicos | Solicitudes | Registros inválidos (a propósito) |
|---------|---------:|------------:|----------------------------------:|
| `solicitudes_pequenas.json` | 3  | 12  | 0 |
| `solicitudes_medias.json`   | 6  | 60  | 2 |
| `solicitudes_grandes.json`  | 12 | 600 | 5 |

Formato de entrada:

```json
{
  "tecnicos":    [{"id": "T01", "nombre": "Ana Torres", "especialidades": ["hardware"], "capacidad": 2}],
  "solicitudes": [{"id": "S0001", "titulo": "Sin acceso a internet", "descripcion": "…",
                   "categoria": "redes", "prioridad": "media", "fecha_creacion": "2026-11-02T08:03:00"}]
}
```

## Cómo ejecutar

Todos los comandos se escriben **desde la carpeta de cada lenguaje**. La interfaz tiene el botón **Cargar JSON…** (se abre en `datos/`); también se puede pasar la ruta como argumento.

### Python (3.10 o superior)

```bash
cd python
pip install -r requirements.txt     # solo instala pytest
python src/main.py ../datos/solicitudes_medias.json   # interfaz (tkinter)
python -m pytest                    # pruebas
```

`tkinter` viene con Python en Windows y macOS; en Linux: `sudo apt install python3-tk`.

### Java (17 o superior, Maven)

```bash
cd java
mvn test                                                # pruebas (JUnit 4)
mvn compile exec:java -Dexec.args="../datos/solicitudes_medias.json"   # interfaz (Swing)
```

### C# (.NET 8 SDK)

```bash
cd csharp
dotnet test tests/Soporte.Tests                          # pruebas (xUnit)
dotnet run --project src/Soporte.App -- ../datos/solicitudes_medias.json   # interfaz (Windows Forms)
```

La interfaz de C# usa Windows Forms y **solo se ejecuta en Windows**; el núcleo y las pruebas funcionan en cualquier sistema.

## Pruebas

Cada lenguaje tiene pruebas automatizadas equivalentes que cubren: validación de entradas, orden de prioridad (críticas primero y antigüedad), cola cuando no hay técnico, reasignación al resolver, elección del técnico, tiempo estimado, plazos, concurrencia sin sobrepasar la capacidad ni duplicar asignaciones, estadísticas, lectura y escritura de JSON, archivos corruptos y los tres datasets incluidos.

## Cómo se cumplen los requisitos de programación

| Requisito | Dónde |
|-----------|-------|
| Funciones y estructuras de control | todos los módulos |
| Organización modular | un archivo por responsabilidad |
| Clases (POO) | `Solicitud`, `Tecnico`, `Despachador` |
| Listas, diccionarios y colas | cola de prioridad, `dict`/`Map`/`Dictionary` por id, listas |
| Validación de datos | módulo de validación |
| Manejo de excepciones | `ErrorSoporte` → `ErrorValidacion` / `ErrorArchivo` |
| Orden superior / lambdas / comprensiones | conteo por clave en estadísticas, filtros y orden de la tabla |
| JSON de lectura y escritura | módulo de persistencia |

## Seguridad y fuentes

El proyecto no contiene contraseñas, claves ni datos personales: los nombres de los técnicos y las solicitudes son inventados. El código fue escrito para este proyecto; las bibliotecas externas son Gson (Java), xUnit (C#) y pytest (Python), y deben citarse en las referencias del documento junto con la documentación oficial de cada lenguaje.

## Control de versiones

```bash
git log --oneline
```

Sugerencia para el equipo: una rama por lenguaje (`python`, `java`, `csharp`) y *pull requests* hacia `main`.
