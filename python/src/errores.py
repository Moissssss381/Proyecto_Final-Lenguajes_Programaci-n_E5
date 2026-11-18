"""Excepciones propias de la aplicación."""


class ErrorSoporte(Exception):
    """Error base de la aplicación."""


class ErrorValidacion(ErrorSoporte):
    """Un dato de entrada no cumple las reglas del negocio."""


class ErrorArchivo(ErrorSoporte):
    """No se pudo leer o escribir un archivo de datos."""
