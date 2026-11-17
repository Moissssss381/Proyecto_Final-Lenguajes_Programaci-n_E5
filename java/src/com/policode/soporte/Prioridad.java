package com.policode.soporte;

import java.util.Locale;

/** Cada prioridad guarda su tiempo máximo de respuesta. El orden de declaración es el orden de atención. */
public enum Prioridad {
    CRITICA("critica", 0, 1),
    ALTA("alta", 1, 2),
    MEDIA("media", 2, 8),
    BAJA("baja", 3, 24);

    private final String etiqueta;
    private final int orden;
    private final int horasMaximas;

    Prioridad(String etiqueta, int orden, int horasMaximas) {
        this.etiqueta = etiqueta;
        this.orden = orden;
        this.horasMaximas = horasMaximas;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public int getOrden() {
        return orden;
    }

    public int getHorasMaximas() {
        return horasMaximas;
    }

    public static Prioridad desdeTexto(String texto) {
        if (texto != null) {
            String normalizado = texto.strip().toLowerCase(Locale.ROOT);
            for (Prioridad prioridad : values()) {
                if (prioridad.etiqueta.equals(normalizado)) {
                    return prioridad;
                }
            }
        }
        throw new ErrorValidacion("Prioridad inválida: " + texto);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
