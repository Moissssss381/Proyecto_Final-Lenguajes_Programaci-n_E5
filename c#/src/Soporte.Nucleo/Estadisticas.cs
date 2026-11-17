using System.Globalization;

namespace Soporte;

public sealed record CargaTecnico(string Id, string Nombre, int Carga, int Capacidad);

public sealed record Resumen(
    int Total,
    int EnCola,
    IReadOnlyDictionary<string, int> PorEstado,
    IReadOnlyDictionary<string, int> PorCategoria,
    IReadOnlyDictionary<string, int> PorPrioridad,
    double TiempoPromedioHoras,
    double CumplimientoPlazoPct,
    IReadOnlyList<CargaTecnico> CargaTecnicos);

/// <summary>Resumen estadístico de solicitudes y técnicos.</summary>
public static class Estadisticas
{
    public static Resumen GenerarResumen(IReadOnlyList<Solicitud> solicitudes, IReadOnlyList<Tecnico> tecnicos)
    {
        List<Solicitud> asignadas = solicitudes.Where(s => s.TiempoEstimadoHoras.HasValue).ToList();
        double promedio = asignadas.Count == 0 ? 0.0 : asignadas.Average(s => s.TiempoEstimadoHoras!.Value);
        int cumplen = asignadas.Count(s => s.CumplePlazo == true);
        double porcentaje = asignadas.Count == 0 ? 0.0 : 100.0 * cumplen / asignadas.Count;

        return new Resumen(
            Total: solicitudes.Count,
            EnCola: solicitudes.Count(s => s.Estado == Estado.Pendiente),
            PorEstado: ContarPor(solicitudes, s => s.Estado.Etiqueta(),
                Enum.GetValues<Estado>().Select(e => e.Etiqueta())),
            PorCategoria: ContarPor(solicitudes, s => s.Categoria.Etiqueta(),
                Enum.GetValues<Categoria>().Select(c => c.Etiqueta())),
            PorPrioridad: ContarPor(solicitudes, s => s.Prioridad.Etiqueta(),
                Enum.GetValues<Prioridad>().Select(p => p.Etiqueta())),
            TiempoPromedioHoras: Math.Round(promedio, 2),
            CumplimientoPlazoPct: Math.Round(porcentaje, 1),
            CargaTecnicos: tecnicos.Select(t => new CargaTecnico(t.Id, t.Nombre, t.Carga, t.Capacidad)).ToList());
    }

    /// <summary>Cuenta solicitudes según 'clave' (función de orden superior), incluyendo los ceros.</summary>
    private static Dictionary<string, int> ContarPor(IEnumerable<Solicitud> solicitudes,
                                                     Func<Solicitud, string> clave,
                                                     IEnumerable<string> valoresPosibles)
    {
        Dictionary<string, int> conteo = solicitudes.GroupBy(clave).ToDictionary(g => g.Key, g => g.Count());
        var resultado = new Dictionary<string, int>();
        foreach (string valor in valoresPosibles)
        {
            resultado[valor] = conteo.TryGetValue(valor, out int cantidad) ? cantidad : 0;
        }
        return resultado;
    }

    /// <summary>Texto legible del resumen, para la interfaz o la consola.</summary>
    public static string Formatear(Resumen resumen)
    {
        CultureInfo cultura = CultureInfo.InvariantCulture;
        return string.Join("\n",
            $"Total de solicitudes: {resumen.Total}",
            $"En cola (sin técnico): {resumen.EnCola}",
            Linea("Por estado", resumen.PorEstado),
            Linea("Por prioridad", resumen.PorPrioridad),
            Linea("Por categoría", resumen.PorCategoria),
            $"Tiempo estimado promedio: {resumen.TiempoPromedioHoras.ToString(cultura)} h",
            $"Cumplimiento del plazo (asignadas): {resumen.CumplimientoPlazoPct.ToString(cultura)} %");
    }

    private static string Linea(string titulo, IReadOnlyDictionary<string, int> datos) =>
        $"{titulo}: " + string.Join(", ", datos.Select(par => $"{par.Key}={par.Value}"));
}
