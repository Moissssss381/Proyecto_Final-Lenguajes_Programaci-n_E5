using System.Diagnostics;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using Soporte;

// Benchmark: mide carga y procesamiento con distintos volúmenes y números de hilos.
// Uso: dotnet run -c Release --project src/Soporte.Benchmark -- archivo.json [hilos 1,2,4,8] [repeticiones 5]
// Protocolo común a los tres lenguajes: 1 calentamiento + N repeticiones, se reporta la mediana en ms.

if (args.Length < 1)
{
    Console.Error.WriteLine("Uso: Soporte.Benchmark <archivo.json> [hilos] [repeticiones]");
    return 1;
}

string ruta = args[0];
int[] listaHilos = (args.Length > 1 ? args[1] : "1,2,4,8").Split(',').Select(h => int.Parse(h.Trim())).ToArray();
int repeticiones = args.Length > 2 ? int.Parse(args[2]) : 5;
string dataset = Path.GetFileNameWithoutExtension(ruta);

foreach (int hilos in listaHilos)
{
    UnaEjecucion(ruta, hilos);  // calentamiento (JIT)
    List<Medida> medidas = Enumerable.Range(0, repeticiones).Select(_ => UnaEjecucion(ruta, hilos)).ToList();
    Medida primera = medidas[0];
    string linea = string.Create(CultureInfo.InvariantCulture,
        $"{{\"lenguaje\": \"csharp\", \"dataset\": \"{dataset}\", \"solicitudes\": {primera.Solicitudes}, " +
        $"\"invalidos\": {primera.Invalidos}, \"hilos\": {hilos}, " +
        $"\"carga_ms\": {Mediana(medidas.Select(m => m.CargaMs)):F3}, \"proceso_ms\": {Mediana(medidas.Select(m => m.ProcesoMs)):F3}, " +
        $"\"asignadas\": {primera.Asignadas}, \"huella\": \"{primera.Huella}\"}}");
    Console.WriteLine(linea);
}
return 0;

static Medida UnaEjecucion(string ruta, int hilos)
{
    var reloj = Stopwatch.StartNew();
    DatosCargados datos = Persistencia.CargarDatos(ruta);
    double cargaMs = reloj.Elapsed.TotalMilliseconds;

    reloj.Restart();
    var despachador = new Despachador(datos.Tecnicos, hilos);
    foreach (Solicitud solicitud in datos.Solicitudes)
    {
        despachador.Registrar(solicitud);
    }
    int asignadas = despachador.ProcesarPendientes();
    double procesoMs = reloj.Elapsed.TotalMilliseconds;

    return new Medida(cargaMs, procesoMs, asignadas, datos.Solicitudes.Count, datos.Errores.Count,
        Huella(despachador.Solicitudes));
}

// SHA-256 de 'id|técnico|tiempo|estado' ordenado por id: comprueba que los 3 lenguajes dan lo mismo.
static string Huella(IReadOnlyList<Solicitud> solicitudes)
{
    List<string> lineas = solicitudes
        .Select(s => string.Create(CultureInfo.InvariantCulture,
            $"{s.Id}|{s.TecnicoId ?? ""}|{(s.TiempoEstimadoHoras.HasValue ? s.TiempoEstimadoHoras.Value.ToString("F2", CultureInfo.InvariantCulture) : "")}|{s.Estado.Etiqueta()}"))
        .ToList();
    lineas.Sort(StringComparer.Ordinal);
    byte[] hash = SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("\n", lineas)));
    return Convert.ToHexString(hash).ToLowerInvariant();
}

static double Mediana(IEnumerable<double> valores)
{
    double[] ordenados = valores.OrderBy(v => v).ToArray();
    int n = ordenados.Length;
    return n % 2 == 1 ? ordenados[n / 2] : (ordenados[n / 2 - 1] + ordenados[n / 2]) / 2;
}

internal sealed record Medida(double CargaMs, double ProcesoMs, int Asignadas, int Solicitudes, int Invalidos, string Huella);
