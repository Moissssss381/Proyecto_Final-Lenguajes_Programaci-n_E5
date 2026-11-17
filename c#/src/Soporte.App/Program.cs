namespace Soporte.App;

internal static class Program
{
    /// <summary>Punto de entrada: dotnet run --project src/Soporte.App [-- ruta.json]</summary>
    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new VentanaPrincipal(args.Length > 0 ? args[0] : null));
    }
}
