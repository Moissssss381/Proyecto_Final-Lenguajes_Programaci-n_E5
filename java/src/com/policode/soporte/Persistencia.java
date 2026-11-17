package com.policode.soporte;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lectura y escritura de archivos JSON. */
public final class Persistencia {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Los registros inválidos no detienen la carga: se omiten y se reportan en {@code errores}. */
    public record DatosCargados(List<Tecnico> tecnicos, List<Solicitud> solicitudes, List<String> errores) {
    }

    private Persistencia() {
    }

    public static DatosCargados cargarDatos(Path ruta) {
        String texto;
        try {
            texto = Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new ErrorArchivo("No se encontró el archivo: " + ruta);
        } catch (IOException e) {
            throw new ErrorArchivo("No se pudo leer " + ruta + ": " + e.getMessage());
        }

        JsonElement raiz;
        try {
            raiz = JsonParser.parseString(texto);
        } catch (JsonParseException e) {
            throw new ErrorArchivo("JSON mal formado en " + ruta + ": " + e.getMessage());
        }
        if (!raiz.isJsonObject()
                || !esLista(raiz.getAsJsonObject().get("tecnicos"))
                || !esLista(raiz.getAsJsonObject().get("solicitudes"))) {
            throw new ErrorArchivo("El archivo debe contener las listas 'tecnicos' y 'solicitudes'");
        }
        JsonObject objeto = raiz.getAsJsonObject();

        List<String> errores = new ArrayList<>();
        List<Tecnico> tecnicos = new ArrayList<>();
        List<Solicitud> solicitudes = new ArrayList<>();
        Set<String> idsTecnicos = new HashSet<>();
        Set<String> idsSolicitudes = new HashSet<>();

        int posicion = 0;
        for (JsonElement datos : objeto.getAsJsonArray("tecnicos")) {
            posicion++;
            try {
                Tecnico tecnico = Validador.validarTecnico(datos);
                if (!idsTecnicos.add(tecnico.getId())) {
                    throw new ErrorValidacion("Id de técnico repetido: " + tecnico.getId());
                }
                tecnicos.add(tecnico);
            } catch (ErrorValidacion e) {
                errores.add("Técnico #" + posicion + ": " + e.getMessage());
            }
        }

        posicion = 0;
        for (JsonElement datos : objeto.getAsJsonArray("solicitudes")) {
            posicion++;
            try {
                Solicitud solicitud = Validador.validarSolicitud(datos);
                if (!idsSolicitudes.add(solicitud.getId())) {
                    throw new ErrorValidacion("Id de solicitud repetido: " + solicitud.getId());
                }
                solicitudes.add(solicitud);
            } catch (ErrorValidacion e) {
                errores.add("Solicitud #" + posicion + ": " + e.getMessage());
            }
        }
        return new DatosCargados(tecnicos, solicitudes, errores);
    }

    private static boolean esLista(JsonElement elemento) {
        return elemento != null && elemento.isJsonArray();
    }

    public static void guardarResultados(Path ruta, List<Solicitud> solicitudes, List<Tecnico> tecnicos,
                                         Estadisticas.Resumen resumen) {
        JsonObject resultado = new JsonObject();
        resultado.add("resumen", resumenAJson(resumen));

        JsonArray jsonTecnicos = new JsonArray();
        tecnicos.forEach(t -> jsonTecnicos.add(tecnicoAJson(t)));
        resultado.add("tecnicos", jsonTecnicos);

        JsonArray jsonSolicitudes = new JsonArray();
        solicitudes.stream()
                .sorted(Solicitud.POR_URGENCIA)
                .forEach(s -> jsonSolicitudes.add(solicitudAJson(s)));
        resultado.add("solicitudes", jsonSolicitudes);

        try {
            Files.writeString(ruta, GSON.toJson(resultado), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ErrorArchivo("No se pudo escribir " + ruta + ": " + e.getMessage());
        }
    }

    private static JsonObject solicitudAJson(Solicitud s) {
        JsonObject json = new JsonObject();
        json.addProperty("id", s.getId());
        json.addProperty("titulo", s.getTitulo());
        json.addProperty("descripcion", s.getDescripcion());
        json.addProperty("categoria", s.getCategoria().getEtiqueta());
        json.addProperty("prioridad", s.getPrioridad().getEtiqueta());
        json.addProperty("fecha_creacion", s.getFechaCreacion().toString());
        json.addProperty("estado", s.getEstado().getEtiqueta());
        json.addProperty("tecnico_id", s.getTecnicoId());
        json.addProperty("tiempo_estimado_horas", s.getTiempoEstimadoHoras());
        json.addProperty("cumple_plazo", s.cumplePlazo().orElse(null));
        return json;
    }

    private static JsonObject tecnicoAJson(Tecnico t) {
        JsonObject json = new JsonObject();
        json.addProperty("id", t.getId());
        json.addProperty("nombre", t.getNombre());
        JsonArray especialidades = new JsonArray();
        t.getEspecialidades().forEach(c -> especialidades.add(c.getEtiqueta()));
        json.add("especialidades", especialidades);
        json.addProperty("capacidad", t.getCapacidad());
        json.addProperty("carga", t.getCarga());
        return json;
    }

    private static JsonObject resumenAJson(Estadisticas.Resumen r) {
        JsonObject json = new JsonObject();
        json.addProperty("total", r.total());
        json.addProperty("en_cola", r.enCola());
        json.add("por_estado", mapaAJson(r.porEstado()));
        json.add("por_categoria", mapaAJson(r.porCategoria()));
        json.add("por_prioridad", mapaAJson(r.porPrioridad()));
        json.addProperty("tiempo_promedio_horas", r.tiempoPromedioHoras());
        json.addProperty("cumplimiento_plazo_pct", r.cumplimientoPlazoPct());
        JsonArray carga = new JsonArray();
        for (Estadisticas.CargaTecnico c : r.cargaTecnicos()) {
            JsonObject fila = new JsonObject();
            fila.addProperty("id", c.id());
            fila.addProperty("nombre", c.nombre());
            fila.addProperty("carga", c.carga());
            fila.addProperty("capacidad", c.capacidad());
            carga.add(fila);
        }
        json.add("carga_tecnicos", carga);
        return json;
    }

    private static JsonObject mapaAJson(Map<String, Integer> mapa) {
        JsonObject json = new JsonObject();
        mapa.forEach(json::addProperty);
        return json;
    }
}
