namespace Soporte;

/// <summary>Reglas de negocio: tiempo estimado de atención y elección del técnico.</summary>
public static class Reglas
{
    /// <summary>Horas base de atención por categoría (supuesto del proyecto, ajustable).</summary>
    private static readonly Dictionary<Categoria, double> HorasBase = new()
    {
        [Categoria.Accesos] = 0.5,
        [Categoria.Software] = 1.0,
        [Categoria.Otro] = 1.0,
        [Categoria.Hardware] = 1.5,
        [Categoria.Redes] = 2.0,
    };

    /// <summary>Cada solicitud activa del técnico aumenta el tiempo estimado en este porcentaje.</summary>
    private const double FactorPorCarga = 0.25;

    /// <summary>tiempo = horas_base * (1 + 0.25 * solicitudes_activas_del_tecnico).</summary>
    public static double CalcularTiempoEstimado(Categoria categoria, int cargaTecnico)
    {
        double horas = HorasBase[categoria] * (1 + FactorPorCarga * cargaTecnico);
        return Math.Round(horas, 2, MidpointRounding.AwayFromZero);
    }

    /// <summary>
    /// Entre los técnicos disponibles que atienden la categoría elige el de menor carga; a igual carga
    /// prefiere al especialista sobre el generalista y el id desempata. Devuelve null si no hay ninguno.
    /// </summary>
    public static Tecnico? ElegirTecnico(IEnumerable<Tecnico> tecnicos, Categoria categoria) =>
        tecnicos
            .Where(t => t.Disponible && t.PuedeAtender(categoria))
            .OrderBy(t => t.Carga)
            .ThenBy(t => t.EsGeneralista)
            .ThenBy(t => t.Id, StringComparer.Ordinal)
            .FirstOrDefault();
}
