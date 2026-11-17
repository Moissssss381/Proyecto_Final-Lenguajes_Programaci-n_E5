package com.policode.soporte;

import java.util.Locale;

public enum Categoria {
    HARDWARE("hardware"),
    SOFTWARE("software"),
    REDES("redes"),
    ACCESOS("accesos"),
    OTRO("otro");

    private final String etiqueta;

    Categoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static Categoria desdeTexto(String texto) {
        if (texto != null) {
            String normalizado = texto.strip().toLowerCase(Locale.ROOT);
            for (Categoria categoria : values()) {
                if (categoria.etiqueta.equals(normalizado)) {
                    return categoria;
                }
            }
        }
        throw new ErrorValidacion("Categoría inválida: " + texto);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
