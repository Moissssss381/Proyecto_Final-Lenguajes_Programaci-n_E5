package com.policode.soporte;

/** Un dato de entrada no cumple las reglas del negocio. */
public class ErrorValidacion extends ErrorSoporte {
    public ErrorValidacion(String mensaje) {
        super(mensaje);
    }
}
