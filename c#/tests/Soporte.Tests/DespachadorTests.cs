namespace Soporte.Tests;

public class DespachadorTests
{
    [Fact]
    public void LasCriticasSeAsignanPrimero()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1", capacidad: 1) }, numHilos: 4);
        Solicitud baja = Fabrica.NuevaSolicitud("BAJA", Prioridad.Baja, minutos: 0);        // llegó antes
        Solicitud critica = Fabrica.NuevaSolicitud("CRIT", Prioridad.Critica, minutos: 60);  // llegó después
        despachador.Registrar(baja);
        despachador.Registrar(critica);
        despachador.ProcesarPendientes();
        Assert.Equal(Estado.Asignada, critica.Estado);
        Assert.Equal(Estado.Pendiente, baja.Estado);
    }

    [Fact]
    public void AIgualPrioridadSeAtiendeLaMasAntigua()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1", capacidad: 1) }, numHilos: 2);
        Solicitud nueva = Fabrica.NuevaSolicitud("NUEVA", minutos: 30);
        Solicitud vieja = Fabrica.NuevaSolicitud("VIEJA", minutos: 0);
        despachador.Registrar(nueva);
        despachador.Registrar(vieja);
        despachador.ProcesarPendientes();
        Assert.Equal(Estado.Asignada, vieja.Estado);
        Assert.Equal(Estado.Pendiente, nueva.Estado);
    }

    [Fact]
    public void SinTecnicoDisponibleLaSolicitudPermaneceEnCola()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1", 2, Categoria.Hardware) }, numHilos: 2);
        Solicitud solicitud = Fabrica.NuevaSolicitud("S1", categoria: Categoria.Redes);  // nadie atiende redes
        despachador.Registrar(solicitud);
        Assert.Equal(0, despachador.ProcesarPendientes());
        Assert.Equal(Estado.Pendiente, solicitud.Estado);
        Assert.Null(solicitud.TecnicoId);
    }

    [Fact]
    public void ResolverLiberaAlTecnicoYReasignaLaCola()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1", capacidad: 1) }, numHilos: 2);
        Solicitud primera = Fabrica.NuevaSolicitud("S1", minutos: 0);
        Solicitud segunda = Fabrica.NuevaSolicitud("S2", minutos: 1);
        despachador.Registrar(primera);
        despachador.Registrar(segunda);
        despachador.ProcesarPendientes();
        Assert.Equal(Estado.Pendiente, segunda.Estado);
        despachador.Resolver("S1");
        Assert.Equal(Estado.Resuelta, primera.Estado);
        Assert.Equal(Estado.Asignada, segunda.Estado);
    }

    [Fact]
    public void NoSePuedeRegistrarNiResolverLoInvalido()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1") }, numHilos: 2);
        despachador.Registrar(Fabrica.NuevaSolicitud("S1"));
        Assert.Throws<ErrorValidacion>(() => despachador.Registrar(Fabrica.NuevaSolicitud("S1")));  // id repetido
        Assert.Throws<ErrorValidacion>(() => despachador.Resolver("NO-EXISTE"));
        Assert.Throws<ErrorValidacion>(() => despachador.Resolver("S1"));  // todavía no está asignada
    }

    [Fact]
    public void SeEligeAlTecnicoDeMenorCarga()
    {
        Tecnico ocupado = Fabrica.NuevoTecnico("T1", capacidad: 3);
        Tecnico libre = Fabrica.NuevoTecnico("T2", capacidad: 3);
        ocupado.Asignar("X");
        Assert.Same(libre, Reglas.ElegirTecnico(new[] { ocupado, libre }, Categoria.Software));
    }

    [Fact]
    public void SePrefiereEspecialistaSobreGeneralistaConIgualCarga()
    {
        Tecnico generalista = Fabrica.NuevoTecnico("T1");
        Tecnico especialista = Fabrica.NuevoTecnico("T2", 2, Categoria.Redes);
        Assert.Same(especialista, Reglas.ElegirTecnico(new[] { generalista, especialista }, Categoria.Redes));
    }

    [Fact]
    public void TiempoEstimadoCreceConLaCargaDelTecnico()
    {
        Assert.Equal(2.0, Reglas.CalcularTiempoEstimado(Categoria.Redes, 0), precision: 9);
        Assert.Equal(3.0, Reglas.CalcularTiempoEstimado(Categoria.Redes, 2), precision: 9);
    }

    [Fact]
    public void SiguienteIdContinuaLaNumeracion()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1") }, numHilos: 2);
        Assert.Equal("S0001", despachador.SiguienteId());
        despachador.Registrar(Fabrica.NuevaSolicitud("S0007"));
        despachador.Registrar(Fabrica.NuevaSolicitud("X1", minutos: 1));  // ids ajenos a S#### se ignoran
        Assert.Equal("S0008", despachador.SiguienteId());
    }

    [Fact]
    public void PlazosPorPrioridad()
    {
        Assert.Equal(1, Prioridad.Critica.HorasMaximas());
        Assert.Equal(2, Prioridad.Alta.HorasMaximas());
        Assert.Equal(8, Prioridad.Media.HorasMaximas());
        Assert.Equal(24, Prioridad.Baja.HorasMaximas());
    }

    [Fact]
    public void ConcurrenciaNuncaSobrepasaLaCapacidadNiDuplicaAsignaciones()
    {
        List<Tecnico> tecnicos = Enumerable.Range(0, 5).Select(i => Fabrica.NuevoTecnico($"T{i}", capacidad: 3)).ToList();  // 15 cupos
        var despachador = new Despachador(tecnicos, numHilos: 8);
        List<Solicitud> solicitudes = Enumerable.Range(0, 200)
            .Select(i => Fabrica.NuevaSolicitud($"S{i:000}", minutos: i)).ToList();
        solicitudes.ForEach(despachador.Registrar);

        Assert.Equal(15, despachador.ProcesarPendientes());
        Assert.All(tecnicos, t => Assert.True(t.Carga <= t.Capacidad));
        List<string> activos = tecnicos.SelectMany(t => t.SolicitudesActivas).ToList();
        Assert.Equal(15, activos.Count);
        Assert.Equal(15, activos.Distinct().Count());
        Assert.Equal(185, solicitudes.Count(s => s.Estado == Estado.Pendiente));
    }
}
