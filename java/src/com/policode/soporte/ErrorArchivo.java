package com.policode.soporte;

/** No se pudo leer o escribir un archivo de datos. */
public class ErrorArchivo extends ErrorSoporte {
    public ErrorArchivo(String mensaje) {
        super(mensaje);
    }
}
