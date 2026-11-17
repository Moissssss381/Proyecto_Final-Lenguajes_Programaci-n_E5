package com.policode.soporte;

/** Error base de la aplicación (no verificada, para poder usarla dentro de lambdas y streams). */
public class ErrorSoporte extends RuntimeException {
    public ErrorSoporte(String mensaje) {
        super(mensaje);
    }
}
