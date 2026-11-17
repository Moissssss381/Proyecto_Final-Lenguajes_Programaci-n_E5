using System.Globalization;
using System.Text.Json;

namespace Soporte;

/// <summary>Validación de los datos de entrada leídos desde JSON.</summary>
public static class Validador
{
    private const int LongitudMaximaTitulo = 120;
    private const int CapacidadMaxima = 10;

    private static readonly string[] FormatosFecha =
    {
        "yyyy-MM-ddTHH:mm:ss", "yyyy-MM-ddTHH:mm", "yyyy-MM-ddTHH:mm:ssK",
        "yyyy-MM-ddTHH:mm:ss.FFFFFFF", "yyyy-MM-ddTHH:mm:ss.FFFFFFFK",  // igual que Python y Java: aceptan fracciones
    };

    public static Solicitud ValidarSolicitud(JsonElement datos)
    {
        if (datos.ValueKind != JsonValueKind.Object)
        {
            throw new ErrorValidacion("La solicitud debe ser un objeto JSON");
        }
        return new Solicitud(
            id: TextoObligatorio(datos, "id", int.MaxValue),
            titulo: TextoObligatorio(datos, "titulo", LongitudMaximaTitulo),
            descripcion: TextoONulo(datos, "descripcion")?.Trim() ?? "",
            categoria: EnumeracionesExtensiones.CategoriaDesdeTexto(TextoONulo(datos, "categoria")),
            prioridad: EnumeracionesExtensiones.PrioridadDesdeTexto(TextoONulo(datos, "prioridad")),
            fechaCreacion: FechaIso(TextoONulo(datos, "fecha_creacion")));
    }

    public static Tecnico ValidarTecnico(JsonElement datos)
    {
        if (datos.ValueKind != JsonValueKind.Object)
        {
            throw new ErrorValidacion("El técnico debe ser un objeto JSON");
        }
        return new Tecnico(
            id: TextoObligatorio(datos, "id", int.MaxValue),
            nombre: TextoObligatorio(datos, "nombre", int.MaxValue),
            especialidades: Especialidades(datos),
            capacidad: Capacidad(datos));
    }

    private static string? TextoONulo(JsonElement datos, string campo)
    {
        if (!datos.TryGetProperty(campo, out JsonElement valor))
        {
            return null;
        }
        return valor.ValueKind switch
        {
            JsonValueKind.Null => null,
            JsonValueKind.String => valor.GetString(),
            _ => valor.ToString(),
        };
    }

    private static string TextoObligatorio(JsonElement datos, string campo, int longitudMaxima)
    {
        if (!datos.TryGetProperty(campo, out JsonElement valor)
            || valor.ValueKind != JsonValueKind.String
            || string.IsNullOrWhiteSpace(valor.GetString()))
        {
            throw new ErrorValidacion($"El campo '{campo}' es obligatorio y debe ser texto");
        }
        string texto = valor.GetString()!.Trim();
        if (texto.Length > longitudMaxima)
        {
            throw new ErrorValidacion($"El campo '{campo}' excede {longitudMaxima} caracteres");
        }
        return texto;
    }

    private static DateTime FechaIso(string? texto)
    {
        if (texto is null)
        {
            throw new ErrorValidacion("El campo 'fecha_creacion' es obligatorio (formato ISO 8601)");
        }
        if (!DateTime.TryParseExact(texto, FormatosFecha, CultureInfo.InvariantCulture,
                                    DateTimeStyles.RoundtripKind, out DateTime fecha))
        {
            throw new ErrorValidacion($"Fecha inválida: {texto}");
        }
        // Con zona horaria se normaliza a hora local sin zona para poder comparar fechas.
        if (fecha.Kind == DateTimeKind.Utc)
        {
            fecha = fecha.ToLocalTime();
        }
        return DateTime.SpecifyKind(fecha, DateTimeKind.Unspecified);
    }

    private static int Capacidad(JsonElement datos)
    {
        if (!datos.TryGetProperty("capacidad", out JsonElement valor))
        {
            return 2;
        }
        if (valor.ValueKind == JsonValueKind.Number && valor.TryGetInt32(out int capacidad)
            && capacidad >= 1 && capacidad <= CapacidadMaxima)
        {
            return capacidad;
        }
        throw new ErrorValidacion($"La capacidad debe ser un entero entre 1 y {CapacidadMaxima}");
    }

    private static List<Categoria> Especialidades(JsonElement datos)
    {
        var resultado = new List<Categoria>();
        if (!datos.TryGetProperty("especialidades", out JsonElement valor) || valor.ValueKind == JsonValueKind.Null)
        {
            return resultado;
        }
        if (valor.ValueKind != JsonValueKind.Array)
        {
            throw new ErrorValidacion("'especialidades' debe ser una lista");
        }
        foreach (JsonElement elemento in valor.EnumerateArray())
        {
            string? texto = elemento.ValueKind == JsonValueKind.String ? elemento.GetString() : null;
            resultado.Add(EnumeracionesExtensiones.CategoriaDesdeTexto(texto));
        }
        return resultado;
    }
}
