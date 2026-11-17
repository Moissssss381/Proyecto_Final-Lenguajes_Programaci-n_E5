namespace Soporte;

/// <summary>Error base de la aplicación.</summary>
public class ErrorSoporte : Exception
{
    public ErrorSoporte(string mensaje) : base(mensaje)
    {
    }
}

/// <summary>Un dato de entrada no cumple las reglas del negocio.</summary>
public class ErrorValidacion : ErrorSoporte
{
    public ErrorValidacion(string mensaje) : base(mensaje)
    {
    }
}

/// <summary>No se pudo leer o escribir un archivo de datos.</summary>
public class ErrorArchivo : ErrorSoporte
{
    public ErrorArchivo(string mensaje) : base(mensaje)
    {
    }
}
