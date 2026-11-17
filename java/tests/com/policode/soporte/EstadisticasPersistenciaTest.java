package com.policode.soporte;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EstadisticasPersistenciaTest {
    @Rule
    public TemporaryFolder carpeta = new TemporaryFolder();

    private Path escribir(String nombre, String contenido) throws IOException {
        Path ruta = carpeta.getRoot().toPath().resolve(nombre);
        Files.writeString(ruta, contenido, StandardCharsets.UTF_8);
        return ruta;
    }

    @Test
    public void resumenCuentaEstadosYCalculaPromedio() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 1)), 2);
        despachador.registrar(Fabrica.solicitud("S1", Prioridad.ALTA, Categoria.REDES, 0));
        despachador.registrar(Fabrica.solicitud("S2", Prioridad.BAJA, Categoria.REDES, 1));
        despachador.procesarPendientes();

        Estadisticas.Resumen resumen = Estadisticas.generarResumen(despachador.getSolicitudes(), despachador.getTecnicos());
        assertEquals(2, resumen.total());
        assertEquals(1, resumen.enCola());
        assertEquals(Map.of("pendiente", 1, "asignada", 1, "resuelta", 0), resumen.porEstado());
        assertEquals(1, (int) resumen.porPrioridad().get("alta"));
        assertEquals(0, (int) resumen.porPrioridad().get("critica"));
        assertEquals(2.0, resumen.tiempoPromedioHoras(), 1e-9);      // redes con técnico sin carga
        assertEquals(100.0, resumen.cumplimientoPlazoPct(), 1e-9);  // 2.0 h <= 2 h de prioridad alta
        assertEquals(1, resumen.cargaTecnicos().get(0).carga());
        assertTrue(Estadisticas.formatear(resumen).contains("Total de solicitudes: 2"));
    }

    @Test
    public void resumenVacioNoDivideEntreCero() {
        Estadisticas.Resumen resumen = Estadisticas.generarResumen(List.of(), List.of());
        assertEquals(0.0, resumen.tiempoPromedioHoras(), 1e-9);
        assertEquals(0.0, resumen.cumplimientoPlazoPct(), 1e-9);
    }

    @Test
    public void cargarOmiteRegistrosInvalidosYLosReporta() throws IOException {
        Path ruta = escribir("datos.json", """
                {"tecnicos":[{"id":"T1","nombre":"Ana","capacidad":2},{"id":"T1","nombre":"Repetido"}],
                 "solicitudes":[
                   {"id":"S1","titulo":"Ok","categoria":"redes","prioridad":"media","fecha_creacion":"2026-11-02T08:00:00"},
                   {"id":"S2","titulo":"","categoria":"redes","prioridad":"media","fecha_creacion":"2026-11-02T08:00:00"}]}""");
        Persistencia.DatosCargados datos = Persistencia.cargarDatos(ruta);
        assertEquals(1, datos.tecnicos().size());
        assertEquals(1, datos.solicitudes().size());
        assertEquals(2, datos.errores().size());
    }

    @Test
    public void cargarArchivoCorruptoLanzaErrorArchivo() throws IOException {
        for (String contenido : new String[]{"{no es json", "{\"tecnicos\": []}", "[]"}) {
            Path ruta = escribir("malo.json", contenido);
            assertThrows(contenido, ErrorArchivo.class, () -> Persistencia.cargarDatos(ruta));
        }
    }

    @Test
    public void cargarArchivoInexistenteLanzaErrorArchivo() {
        Path ruta = carpeta.getRoot().toPath().resolve("no-existe.json");
        assertThrows(ErrorArchivo.class, () -> Persistencia.cargarDatos(ruta));
    }

    @Test
    public void guardarResultadosGeneraJsonLegible() throws IOException {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 2)), 2);
        despachador.registrar(Fabrica.solicitud("S1", Prioridad.MEDIA, 0));
        despachador.procesarPendientes();
        Path ruta = carpeta.getRoot().toPath().resolve("salida.json");
        Persistencia.guardarResultados(ruta, despachador.getSolicitudes(), despachador.getTecnicos(),
                Estadisticas.generarResumen(despachador.getSolicitudes(), despachador.getTecnicos()));

        JsonObject contenido = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("asignada", contenido.getAsJsonArray("solicitudes").get(0).getAsJsonObject().get("estado").getAsString());
        assertEquals(1, contenido.getAsJsonObject("resumen").get("total").getAsInt());
    }

    @Test
    public void guardarEnRutaInexistenteLanzaErrorArchivo() {
        Path ruta = carpeta.getRoot().toPath().resolve("no-existe").resolve("x.json");
        Estadisticas.Resumen resumen = Estadisticas.generarResumen(List.of(), List.of());
        assertThrows(ErrorArchivo.class, () -> Persistencia.guardarResultados(ruta, List.of(), List.of(), resumen));
    }

    @Test
    public void losDatasetsIncluidosSeCargan() {
        Path datos = Path.of("..", "datos");  // las pruebas se ejecutan desde la carpeta java/
        String[] nombres = {"solicitudes_pequenas.json", "solicitudes_medias.json", "solicitudes_grandes.json"};
        int[] invalidosEsperados = {0, 2, 5};
        for (int i = 0; i < nombres.length; i++) {
            Persistencia.DatosCargados cargados = Persistencia.cargarDatos(datos.resolve(nombres[i]));
            assertFalse(cargados.solicitudes().isEmpty());
            assertEquals(nombres[i], invalidosEsperados[i], cargados.errores().size());
        }
    }
}
