package com.policode.soporte;

public enum Estado {
    PENDIENTE("pendiente"),
    ASIGNADA("asignada"),
    RESUELTA("resuelta");

    private final String etiqueta;

    Estado(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
