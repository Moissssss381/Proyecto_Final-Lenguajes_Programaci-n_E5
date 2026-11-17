package com.policode.soporte;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Validación de los datos de entrada leídos desde JSON. */
public final class Validador {
    private static final int LONGITUD_MAXIMA_TITULO = 120;
    private static final int CAPACIDAD_MAXIMA = 10;

    private Validador() {
    }

    public static Solicitud validarSolicitud(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonObject()) {
            throw new ErrorValidacion("La solicitud debe ser un objeto JSON");
        }
        JsonObject datos = elemento.getAsJsonObject();
        String descripcion = textoONulo(datos, "descripcion");
        return new Solicitud(
                textoObligatorio(datos, "id", Integer.MAX_VALUE),
                textoObligatorio(datos, "titulo", LONGITUD_MAXIMA_TITULO),
                descripcion == null ? "" : descripcion.strip(),
                Categoria.desdeTexto(textoONulo(datos, "categoria")),
                Prioridad.desdeTexto(textoONulo(datos, "prioridad")),
                fechaIso(textoONulo(datos, "fecha_creacion")));
    }

    public static Tecnico validarTecnico(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonObject()) {
            throw new ErrorValidacion("El técnico debe ser un objeto JSON");
        }
        JsonObject datos = elemento.getAsJsonObject();
        return new Tecnico(
                textoObligatorio(datos, "id", Integer.MAX_VALUE),
                textoObligatorio(datos, "nombre", Integer.MAX_VALUE),
                especialidades(datos),
                capacidad(datos));
    }

    private static String textoONulo(JsonObject datos, String campo) {
        JsonElement valor = datos.get(campo);
        if (valor == null || valor.isJsonNull()) {
            return null;
        }
        return valor.isJsonPrimitive() ? valor.getAsString() : valor.toString();
    }

    private static String textoObligatorio(JsonObject datos, String campo, int longitudMaxima) {
        JsonElement valor = datos.get(campo);
        boolean esTexto = valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString();
        if (!esTexto || valor.getAsString().isBlank()) {
            throw new ErrorValidacion("El campo '" + campo + "' es obligatorio y debe ser texto");
        }
        String texto = valor.getAsString().strip();
        if (texto.length() > longitudMaxima) {
            throw new ErrorValidacion("El campo '" + campo + "' excede " + longitudMaxima + " caracteres");
        }
        return texto;
    }

    private static LocalDateTime fechaIso(String texto) {
        if (texto == null) {
            throw new ErrorValidacion("El campo 'fecha_creacion' es obligatorio (formato ISO 8601)");
        }
        try {
            return LocalDateTime.parse(texto);
        } catch (DateTimeParseException sinZona) {
            try {  // con zona horaria: se normaliza a hora local sin zona para poder comparar
                return OffsetDateTime.parse(texto).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException conZona) {
                throw new ErrorValidacion("Fecha inválida: " + texto);
            }
        }
    }

    private static int capacidad(JsonObject datos) {
        JsonElement valor = datos.get("capacidad");
        if (valor == null) {
            return 2;
        }
        boolean esNumero = valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isNumber();
        if (esNumero) {
            BigDecimal numero = valor.getAsBigDecimal();
            boolean esEntero = numero.stripTrailingZeros().scale() <= 0;
            if (esEntero && numero.intValue() >= 1 && numero.intValue() <= CAPACIDAD_MAXIMA) {
                return numero.intValue();
            }
        }
        throw new ErrorValidacion("La capacidad debe ser un entero entre 1 y " + CAPACIDAD_MAXIMA);
    }

    private static List<Categoria> especialidades(JsonObject datos) {
        JsonElement valor = datos.get("especialidades");
        List<Categoria> resultado = new ArrayList<>();
        if (valor == null || valor.isJsonNull()) {
            return resultado;
        }
        if (!valor.isJsonArray()) {
            throw new ErrorValidacion("'especialidades' debe ser una lista");
        }
        JsonArray lista = valor.getAsJsonArray();
        for (JsonElement elemento : lista) {
            JsonPrimitive primitivo = elemento.isJsonPrimitive() ? elemento.getAsJsonPrimitive() : null;
            resultado.add(Categoria.desdeTexto(primitivo == null ? null : primitivo.getAsString()));
        }
        return resultado;
    }
}
