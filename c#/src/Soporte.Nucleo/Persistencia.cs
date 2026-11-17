using System.Globalization;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Soporte;

/// <summary>Los registros inválidos no detienen la carga: se omiten y se reportan en <see cref="Errores"/>.</summary>
public sealed record DatosCargados(
    IReadOnlyList<Tecnico> Tecnicos,
    IReadOnlyList<Solicitud> Solicitudes,
    IReadOnlyList<string> Errores);

/// <summary>Lectura y escritura de archivos JSON.</summary>
public static class Persistencia
{
    private static readonly JsonSerializerOptions OpcionesEscritura = new()
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,  // conserva tildes y ñ
    };

    public static DatosCargados CargarDatos(string ruta)
    {
        string texto;
        try
        {
            texto = File.ReadAllText(ruta, Encoding.UTF8);
        }
        catch (Exception e) when (e is FileNotFoundException or DirectoryNotFoundException)
        {
            throw new ErrorArchivo($"No se encontró el archivo: {ruta}");
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            throw new ErrorArchivo($"No se pudo leer {ruta}: {e.Message}");
        }

        JsonDocument documento;
        try
        {
            documento = JsonDocument.Parse(texto);
        }
        catch (JsonException e)
        {
            throw new ErrorArchivo($"JSON mal formado en {ruta}: {e.Message}");
        }

        using (documento)
        {
            JsonElement raiz = documento.RootElement;
            if (raiz.ValueKind != JsonValueKind.Object
                || !raiz.TryGetProperty("tecnicos", out JsonElement listaTecnicos)
                || listaTecnicos.ValueKind != JsonValueKind.Array
                || !raiz.TryGetProperty("solicitudes", out JsonElement listaSolicitudes)
                || listaSolicitudes.ValueKind != JsonValueKind.Array)
            {
                throw new ErrorArchivo("El archivo debe contener las listas 'tecnicos' y 'solicitudes'");
            }

            var errores = new List<string>();
            var tecnicos = new List<Tecnico>();
            var solicitudes = new List<Solicitud>();
            var idsTecnicos = new HashSet<string>();
            var idsSolicitudes = new HashSet<string>();

            int posicion = 0;
            foreach (JsonElement datos in listaTecnicos.EnumerateArray())
            {
                posicion++;
                try
                {
                    Tecnico tecnico = Validador.ValidarTecnico(datos);
                    if (!idsTecnicos.Add(tecnico.Id))
                    {
                        throw new ErrorValidacion($"Id de técnico repetido: {tecnico.Id}");
                    }
                    tecnicos.Add(tecnico);
                }
                catch (ErrorValidacion e)
                {
                    errores.Add($"Técnico #{posicion}: {e.Message}");
                }
            }

            posicion = 0;
            foreach (JsonElement datos in listaSolicitudes.EnumerateArray())
            {
                posicion++;
                try
                {
                    Solicitud solicitud = Validador.ValidarSolicitud(datos);
                    if (!idsSolicitudes.Add(solicitud.Id))
                    {
                        throw new ErrorValidacion($"Id de solicitud repetido: {solicitud.Id}");
                    }
                    solicitudes.Add(solicitud);
                }
                catch (ErrorValidacion e)
                {
                    errores.Add($"Solicitud #{posicion}: {e.Message}");
                }
            }
            return new DatosCargados(tecnicos, solicitudes, errores);
        }
    }

    public static void GuardarResultados(string ruta, IReadOnlyList<Solicitud> solicitudes,
                                         IReadOnlyList<Tecnico> tecnicos, Resumen resumen)
    {
        var resultado = new JsonObject
        {
            ["resumen"] = ResumenAJson(resumen),
            ["tecnicos"] = new JsonArray(tecnicos.Select(t => (JsonNode?)TecnicoAJson(t)).ToArray()),
            ["solicitudes"] = new JsonArray(solicitudes
                .OrderBy(s => s, Solicitud.ComparadorUrgencia)
                .Select(s => (JsonNode?)SolicitudAJson(s))
                .ToArray()),
        };
        try
        {
            File.WriteAllText(ruta, resultado.ToJsonString(OpcionesEscritura), Encoding.UTF8);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            throw new ErrorArchivo($"No se pudo escribir {ruta}: {e.Message}");
        }
    }

    private static JsonObject SolicitudAJson(Solicitud s) => new()
    {
        ["id"] = s.Id,
        ["titulo"] = s.Titulo,
        ["descripcion"] = s.Descripcion,
        ["categoria"] = s.Categoria.Etiqueta(),
        ["prioridad"] = s.Prioridad.Etiqueta(),
        ["fecha_creacion"] = s.FechaCreacion.ToString("yyyy-MM-ddTHH:mm:ss", CultureInfo.InvariantCulture),
        ["estado"] = s.Estado.Etiqueta(),
        ["tecnico_id"] = JsonValue.Create(s.TecnicoId),
        ["tiempo_estimado_horas"] = JsonValue.Create(s.TiempoEstimadoHoras),
        ["cumple_plazo"] = JsonValue.Create(s.CumplePlazo),
    };

    private static JsonObject TecnicoAJson(Tecnico t) => new()
    {
        ["id"] = t.Id,
        ["nombre"] = t.Nombre,
        ["especialidades"] = new JsonArray(t.Especialidades.Select(c => (JsonNode?)JsonValue.Create(c.Etiqueta())).ToArray()),
        ["capacidad"] = t.Capacidad,
        ["carga"] = t.Carga,
    };

    private static JsonObject ResumenAJson(Resumen r) => new()
    {
        ["total"] = r.Total,
        ["en_cola"] = r.EnCola,
        ["por_estado"] = MapaAJson(r.PorEstado),
        ["por_categoria"] = MapaAJson(r.PorCategoria),
        ["por_prioridad"] = MapaAJson(r.PorPrioridad),
        ["tiempo_promedio_horas"] = r.TiempoPromedioHoras,
        ["cumplimiento_plazo_pct"] = r.CumplimientoPlazoPct,
        ["carga_tecnicos"] = new JsonArray(r.CargaTecnicos.Select(c => (JsonNode?)new JsonObject
        {
            ["id"] = c.Id,
            ["nombre"] = c.Nombre,
            ["carga"] = c.Carga,
            ["capacidad"] = c.Capacidad,
        }).ToArray()),
    };

    private static JsonObject MapaAJson(IReadOnlyDictionary<string, int> mapa)
    {
        var json = new JsonObject();
        foreach ((string clave, int valor) in mapa)
        {
            json[clave] = valor;
        }
        return json;
    }
}
