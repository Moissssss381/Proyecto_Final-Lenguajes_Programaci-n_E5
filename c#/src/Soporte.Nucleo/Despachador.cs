namespace Soporte;

/// <summary>
/// Recibe solicitudes, las ordena por prioridad y las asigna con varios hilos.
/// La cola y la asignación (leer la carga de los técnicos y modificarla) se protegen con un candado,
/// de modo que dos hilos nunca ocupan el mismo cupo de un técnico y las críticas se atienden primero.
/// </summary>
public class Despachador
{
    private readonly object _candado = new();
    private readonly IReadOnlyList<Tecnico> _tecnicos;
    private readonly List<Solicitud> _registro = new();
    private readonly Dictionary<string, Solicitud> _porId = new();
    private readonly PriorityQueue<Solicitud, Solicitud> _cola = new(Solicitud.ComparadorUrgencia);
    private readonly List<Solicitud> _enEspera = new();
    private int _numHilos;

    public Despachador(IEnumerable<Tecnico> tecnicos, int numHilos = 4)
    {
        _tecnicos = tecnicos.ToList();
        NumHilos = numHilos;
    }

    public int NumHilos
    {
        get => _numHilos;
        set
        {
            if (value < 1)
            {
                throw new ArgumentOutOfRangeException(nameof(value), "NumHilos debe ser al menos 1");
            }
            _numHilos = value;
        }
    }

    public IReadOnlyList<Tecnico> Tecnicos => _tecnicos;

    public IReadOnlyList<Solicitud> Solicitudes
    {
        get
        {
            lock (_candado)
            {
                return _registro.ToList();
            }
        }
    }

    public void Registrar(Solicitud solicitud)
    {
        lock (_candado)
        {
            if (!_porId.TryAdd(solicitud.Id, solicitud))
            {
                throw new ErrorValidacion($"Ya existe una solicitud con id {solicitud.Id}");
            }
            _registro.Add(solicitud);
            _cola.Enqueue(solicitud, solicitud);
        }
    }

    /// <summary>Id para una solicitud nueva: continúa la numeración S0001, S0002… ya registrada.</summary>
    public string SiguienteId()
    {
        lock (_candado)
        {
            int maximo = _porId.Keys
                .Where(id => id.Length > 1 && id[0] == 'S' && id.Skip(1).All(char.IsAsciiDigit))
                .Select(id => int.TryParse(id.AsSpan(1), out int numero) ? numero : 0)
                .DefaultIfEmpty(0)
                .Max();
            return $"S{maximo + 1:0000}";
        }
    }

    /// <summary>
    /// Asigna con varios hilos todo lo que esté en cola y devuelve cuántas se asignaron.
    /// Las solicitudes sin técnico disponible quedan en espera y se reintentan en la siguiente llamada.
    /// </summary>
    public int ProcesarPendientes()
    {
        lock (_candado)
        {
            foreach (Solicitud solicitud in _enEspera)
            {
                _cola.Enqueue(solicitud, solicitud);
            }
            _enEspera.Clear();
        }

        Task<int>[] trabajos = Enumerable.Range(0, NumHilos)
            .Select(_ => Task.Run(() => Trabajador()))
            .ToArray();
        try
        {
            Task.WaitAll(trabajos);
        }
        catch (AggregateException e)
        {
            throw new ErrorSoporte($"Falló un hilo de trabajo: {e.InnerException?.Message}");
        }
        return trabajos.Sum(trabajo => trabajo.Result);
    }

    /// <summary>Marca la solicitud como resuelta, libera al técnico y reasigna la cola en espera.</summary>
    public int Resolver(string idSolicitud)
    {
        lock (_candado)
        {
            if (!_porId.TryGetValue(idSolicitud, out Solicitud? solicitud))
            {
                throw new ErrorValidacion($"No existe la solicitud {idSolicitud}");
            }
            if (solicitud.Estado != Estado.Asignada)
            {
                throw new ErrorValidacion($"La solicitud {idSolicitud} no está asignada");
            }
            Tecnico tecnico = _tecnicos.First(t => t.Id == solicitud.TecnicoId);
            tecnico.Liberar(idSolicitud);
            solicitud.Estado = Estado.Resuelta;
        }
        return ProcesarPendientes();
    }

    private int Trabajador()
    {
        int asignadas = 0;
        while (true)
        {
            lock (_candado)
            {
                if (!_cola.TryDequeue(out Solicitud? solicitud, out _))
                {
                    return asignadas;
                }
                if (Asignar(solicitud))
                {
                    asignadas++;
                }
                else
                {
                    _enEspera.Add(solicitud);
                }
            }
        }
    }

    /// <summary>Se llama con el candado tomado.</summary>
    private bool Asignar(Solicitud solicitud)
    {
        Tecnico? tecnico = Reglas.ElegirTecnico(_tecnicos, solicitud.Categoria);
        if (tecnico is null)
        {
            return false;
        }
        solicitud.TiempoEstimadoHoras = Reglas.CalcularTiempoEstimado(solicitud.Categoria, tecnico.Carga);
        tecnico.Asignar(solicitud.Id);
        solicitud.TecnicoId = tecnico.Id;
        solicitud.Estado = Estado.Asignada;
        return true;
    }
}
