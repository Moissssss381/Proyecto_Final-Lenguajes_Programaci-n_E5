namespace Soporte;

public class Solicitud
{
    /// <summary>Más urgente primero; a igual prioridad, la más antigua; el id desempata.</summary>
    public static readonly IComparer<Solicitud> ComparadorUrgencia = Comparer<Solicitud>.Create((a, b) =>
    {
        int resultado = ((int)a.Prioridad).CompareTo((int)b.Prioridad);
        if (resultado != 0)
        {
            return resultado;
        }
        resultado = a.FechaCreacion.CompareTo(b.FechaCreacion);
        return resultado != 0 ? resultado : string.CompareOrdinal(a.Id, b.Id);
    });

    public Solicitud(string id, string titulo, string descripcion, Categoria categoria,
                     Prioridad prioridad, DateTime fechaCreacion)
    {
        Id = id;
        Titulo = titulo;
        Descripcion = descripcion;
        Categoria = categoria;
        Prioridad = prioridad;
        FechaCreacion = fechaCreacion;
    }

    public string Id { get; }
    public string Titulo { get; }
    public string Descripcion { get; }
    public Categoria Categoria { get; }
    public Prioridad Prioridad { get; }
    public DateTime FechaCreacion { get; }
    public Estado Estado { get; set; } = Estado.Pendiente;
    public string? TecnicoId { get; set; }
    public double? TiempoEstimadoHoras { get; set; }

    /// <summary>Null mientras no tenga tiempo estimado (no está asignada).</summary>
    public bool? CumplePlazo =>
        TiempoEstimadoHoras.HasValue ? TiempoEstimadoHoras.Value <= Prioridad.HorasMaximas() : (bool?)null;
}
