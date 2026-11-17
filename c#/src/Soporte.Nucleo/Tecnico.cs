namespace Soporte;

/// <summary>
/// Técnico con capacidad máxima de solicitudes activas.
/// Sin especialidades significa generalista: puede atender cualquier categoría.
/// </summary>
public class Tecnico
{
    private readonly List<string> _activas = new();

    public Tecnico(string id, string nombre, IEnumerable<Categoria> especialidades, int capacidad)
    {
        Id = id;
        Nombre = nombre;
        Especialidades = especialidades.ToList();
        Capacidad = capacidad;
    }

    public string Id { get; }
    public string Nombre { get; }
    public IReadOnlyList<Categoria> Especialidades { get; }
    public int Capacidad { get; }
    public int Carga => _activas.Count;
    public IReadOnlyList<string> SolicitudesActivas => _activas.ToList();
    public bool Disponible => Carga < Capacidad;
    public bool EsGeneralista => Especialidades.Count == 0;

    public bool PuedeAtender(Categoria categoria) => EsGeneralista || Especialidades.Contains(categoria);

    public void Asignar(string idSolicitud)
    {
        if (!Disponible)
        {
            throw new ErrorSoporte($"El técnico {Id} ya alcanzó su capacidad máxima");
        }
        _activas.Add(idSolicitud);
    }

    public void Liberar(string idSolicitud)
    {
        if (!_activas.Remove(idSolicitud))
        {
            throw new ErrorSoporte($"El técnico {Id} no tiene asignada la solicitud {idSolicitud}");
        }
    }
}
