namespace Soporte.Tests;

/// <summary>Crea objetos de prueba; 'minutos' desplaza la fecha para controlar la antigüedad.</summary>
internal static class Fabrica
{
    private static readonly DateTime Base = new(2026, 11, 2, 8, 0, 0);

    public static Solicitud NuevaSolicitud(string id, Prioridad prioridad = Prioridad.Media,
                                           Categoria categoria = Categoria.Software, int minutos = 0) =>
        new(id, $"Solicitud {id}", "", categoria, prioridad, Base.AddMinutes(minutos));

    public static Tecnico NuevoTecnico(string id, int capacidad = 2, params Categoria[] especialidades) =>
        new(id, $"Técnico {id}", especialidades, capacidad);
}
