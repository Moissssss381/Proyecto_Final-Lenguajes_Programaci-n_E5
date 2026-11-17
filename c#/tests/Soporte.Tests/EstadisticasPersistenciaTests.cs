using System.Text.Json;

namespace Soporte.Tests;

public sealed class EstadisticasPersistenciaTests : IDisposable
{
    private readonly string _carpeta =
        Directory.CreateDirectory(Path.Combine(Path.GetTempPath(), "policode-" + Guid.NewGuid().ToString("N"))).FullName;

    public void Dispose() => Directory.Delete(_carpeta, recursive: true);

    private string Escribir(string nombre, string contenido)
    {
        string ruta = Path.Combine(_carpeta, nombre);
        File.WriteAllText(ruta, contenido);
        return ruta;
    }

    /// <summary>Busca la carpeta "datos" del repositorio subiendo desde la carpeta de las pruebas.</summary>
    private static string RutaDatos()
    {
        for (var carpeta = new DirectoryInfo(AppContext.BaseDirectory); carpeta is not null; carpeta = carpeta.Parent)
        {
            string candidata = Path.Combine(carpeta.FullName, "datos");
            if (Directory.Exists(candidata))
            {
                return candidata;
            }
        }
        throw new DirectoryNotFoundException("No se encontró la carpeta 'datos' del repositorio");
    }

    [Fact]
    public void ResumenCuentaEstadosYCalculaPromedio()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1", capacidad: 1) }, numHilos: 2);
        despachador.Registrar(Fabrica.NuevaSolicitud("S1", Prioridad.Alta, Categoria.Redes, minutos: 0));
        despachador.Registrar(Fabrica.NuevaSolicitud("S2", Prioridad.Baja, Categoria.Redes, minutos: 1));
        despachador.ProcesarPendientes();

        Resumen resumen = Estadisticas.GenerarResumen(despachador.Solicitudes, despachador.Tecnicos);
        Assert.Equal(2, resumen.Total);
        Assert.Equal(1, resumen.EnCola);
        Assert.Equal(1, resumen.PorEstado["pendiente"]);
        Assert.Equal(1, resumen.PorEstado["asignada"]);
        Assert.Equal(0, resumen.PorEstado["resuelta"]);
        Assert.Equal(1, resumen.PorPrioridad["alta"]);
        Assert.Equal(0, resumen.PorPrioridad["critica"]);
        Assert.Equal(2.0, resumen.TiempoPromedioHoras, precision: 9);      // redes con técnico sin carga
        Assert.Equal(100.0, resumen.CumplimientoPlazoPct, precision: 9);   // 2.0 h <= 2 h de prioridad alta
        Assert.Equal(1, resumen.CargaTecnicos[0].Carga);
        Assert.Contains("Total de solicitudes: 2", Estadisticas.Formatear(resumen));
    }

    [Fact]
    public void ResumenVacioNoDivideEntreCero()
    {
        Resumen resumen = Estadisticas.GenerarResumen(new List<Solicitud>(), new List<Tecnico>());
        Assert.Equal(0.0, resumen.TiempoPromedioHoras);
        Assert.Equal(0.0, resumen.CumplimientoPlazoPct);
    }

    [Fact]
    public void CargarOmiteRegistrosInvalidosYLosReporta()
    {
        string ruta = Escribir("datos.json", """
            {"tecnicos":[{"id":"T1","nombre":"Ana","capacidad":2},{"id":"T1","nombre":"Repetido"}],
             "solicitudes":[
               {"id":"S1","titulo":"Ok","categoria":"redes","prioridad":"media","fecha_creacion":"2026-11-02T08:00:00"},
               {"id":"S2","titulo":"","categoria":"redes","prioridad":"media","fecha_creacion":"2026-11-02T08:00:00"}]}
            """);
        DatosCargados datos = Persistencia.CargarDatos(ruta);
        Assert.Single(datos.Tecnicos);
        Assert.Single(datos.Solicitudes);
        Assert.Equal(2, datos.Errores.Count);
    }

    [Theory]
    [InlineData("{no es json")]
    [InlineData("{\"tecnicos\": []}")]
    [InlineData("[]")]
    public void CargarArchivoCorruptoLanzaErrorArchivo(string contenido)
    {
        string ruta = Escribir("malo.json", contenido);
        Assert.Throws<ErrorArchivo>(() => Persistencia.CargarDatos(ruta));
    }

    [Fact]
    public void CargarArchivoInexistenteLanzaErrorArchivo()
    {
        Assert.Throws<ErrorArchivo>(() => Persistencia.CargarDatos(Path.Combine(_carpeta, "no-existe.json")));
    }

    [Fact]
    public void GuardarResultadosGeneraJsonLegible()
    {
        var despachador = new Despachador(new[] { Fabrica.NuevoTecnico("T1") }, numHilos: 2);
        despachador.Registrar(Fabrica.NuevaSolicitud("S1"));
        despachador.ProcesarPendientes();
        string ruta = Path.Combine(_carpeta, "salida.json");
        Persistencia.GuardarResultados(ruta, despachador.Solicitudes, despachador.Tecnicos,
            Estadisticas.GenerarResumen(despachador.Solicitudes, despachador.Tecnicos));

        using JsonDocument contenido = JsonDocument.Parse(File.ReadAllText(ruta));
        JsonElement raiz = contenido.RootElement;
        Assert.Equal("asignada", raiz.GetProperty("solicitudes")[0].GetProperty("estado").GetString());
        Assert.Equal(1, raiz.GetProperty("resumen").GetProperty("total").GetInt32());
    }

    [Fact]
    public void GuardarEnRutaInexistenteLanzaErrorArchivo()
    {
        string ruta = Path.Combine(_carpeta, "no-existe", "x.json");
        Resumen resumen = Estadisticas.GenerarResumen(new List<Solicitud>(), new List<Tecnico>());
        Assert.Throws<ErrorArchivo>(() =>
            Persistencia.GuardarResultados(ruta, new List<Solicitud>(), new List<Tecnico>(), resumen));
    }

    [Theory]
    [InlineData("solicitudes_pequenas.json", 0)]
    [InlineData("solicitudes_medias.json", 2)]
    [InlineData("solicitudes_grandes.json", 5)]
    public void LosDatasetsIncluidosSeCargan(string nombre, int invalidosEsperados)
    {
        DatosCargados datos = Persistencia.CargarDatos(Path.Combine(RutaDatos(), nombre));
        Assert.NotEmpty(datos.Solicitudes);
        Assert.Equal(invalidosEsperados, datos.Errores.Count);
    }
}
