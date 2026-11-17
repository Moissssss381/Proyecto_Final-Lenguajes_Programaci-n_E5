using System.Text.Json;

namespace Soporte.Tests;

public class ValidadorTests
{
    private static Dictionary<string, object?> DatosValidos() => new()
    {
        ["id"] = "S1",
        ["titulo"] = "Sin internet",
        ["descripcion"] = "Oficina 3",
        ["categoria"] = "redes",
        ["prioridad"] = "ALTA",
        ["fecha_creacion"] = "2026-11-02T08:30:00",
    };

    private static JsonElement ComoJson(Dictionary<string, object?> datos) => JsonSerializer.SerializeToElement(datos);

    private static JsonElement Json(string texto) => JsonDocument.Parse(texto).RootElement.Clone();

    [Fact]
    public void SolicitudValidaSeConvierteAObjeto()
    {
        Solicitud solicitud = Validador.ValidarSolicitud(ComoJson(DatosValidos()));
        Assert.Equal(Categoria.Redes, solicitud.Categoria);
        Assert.Equal(Prioridad.Alta, solicitud.Prioridad);  // no distingue mayúsculas
    }

    [Theory]
    [InlineData("titulo", "   ")]
    [InlineData("prioridad", "urgentisima")]
    [InlineData("categoria", "cafeteria")]
    [InlineData("fecha_creacion", "ayer")]
    public void SolicitudConDatoInvalidoLanzaErrorValidacion(string campo, string valor)
    {
        Dictionary<string, object?> datos = DatosValidos();
        datos[campo] = valor;
        Assert.Throws<ErrorValidacion>(() => Validador.ValidarSolicitud(ComoJson(datos)));
    }

    [Fact]
    public void SolicitudSinIdLanzaErrorValidacion()
    {
        Dictionary<string, object?> datos = DatosValidos();
        datos.Remove("id");
        Assert.Throws<ErrorValidacion>(() => Validador.ValidarSolicitud(ComoJson(datos)));
    }

    [Fact]
    public void SolicitudQueNoEsObjetoLanzaError()
    {
        Assert.Throws<ErrorValidacion>(() => Validador.ValidarSolicitud(Json("\"texto\"")));
    }

    [Fact]
    public void TecnicoValidoSeConvierteAObjeto()
    {
        Tecnico tecnico = Validador.ValidarTecnico(Json(
            "{\"id\":\"T1\",\"nombre\":\"Ana\",\"especialidades\":[\"redes\"],\"capacidad\":3}"));
        Assert.Equal(3, tecnico.Capacidad);
        Assert.Equal(new[] { Categoria.Redes }, tecnico.Especialidades);
    }

    [Theory]
    [InlineData("0")]
    [InlineData("11")]
    [InlineData("true")]
    [InlineData("\"2\"")]
    [InlineData("2.5")]
    public void TecnicoConCapacidadInvalidaLanzaErrorValidacion(string capacidad)
    {
        string json = "{\"id\":\"T1\",\"nombre\":\"Ana\",\"capacidad\":" + capacidad + "}";
        Assert.Throws<ErrorValidacion>(() => Validador.ValidarTecnico(Json(json)));
    }
}
