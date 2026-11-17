namespace Soporte;

public enum Categoria { Hardware, Software, Redes, Accesos, Otro }

/// <summary>El orden de declaración es el orden de atención (crítica primero).</summary>
public enum Prioridad { Critica, Alta, Media, Baja }

public enum Estado { Pendiente, Asignada, Resuelta }

public static class EnumeracionesExtensiones
{
    public static string Etiqueta(this Categoria categoria) => categoria.ToString().ToLowerInvariant();

    public static string Etiqueta(this Prioridad prioridad) => prioridad.ToString().ToLowerInvariant();

    public static string Etiqueta(this Estado estado) => estado.ToString().ToLowerInvariant();

    /// <summary>Tiempo máximo de respuesta en horas según la prioridad.</summary>
    public static int HorasMaximas(this Prioridad prioridad) => prioridad switch
    {
        Prioridad.Critica => 1,
        Prioridad.Alta => 2,
        Prioridad.Media => 8,
        Prioridad.Baja => 24,
        _ => throw new ArgumentOutOfRangeException(nameof(prioridad)),
    };

    public static Categoria CategoriaDesdeTexto(string? texto)
    {
        string normalizado = texto?.Trim().ToLowerInvariant() ?? "";
        foreach (Categoria categoria in Enum.GetValues<Categoria>())
        {
            if (categoria.Etiqueta() == normalizado)
            {
                return categoria;
            }
        }
        throw new ErrorValidacion($"Categoría inválida: {texto}");
    }

    public static Prioridad PrioridadDesdeTexto(string? texto)
    {
        string normalizado = texto?.Trim().ToLowerInvariant() ?? "";
        foreach (Prioridad prioridad in Enum.GetValues<Prioridad>())
        {
            if (prioridad.Etiqueta() == normalizado)
            {
                return prioridad;
            }
        }
        throw new ErrorValidacion($"Prioridad inválida: {texto}");
    }
}
