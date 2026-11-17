package com.policode.soporte;

import java.time.LocalDateTime;
import java.util.List;

/** Crea objetos de prueba; 'minutos' desplaza la fecha para controlar la antigüedad. */
final class Fabrica {
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 2, 8, 0);

    private Fabrica() {
    }

    static Solicitud solicitud(String id, Prioridad prioridad, Categoria categoria, int minutos) {
        return new Solicitud(id, "Solicitud " + id, "", categoria, prioridad, BASE.plusMinutes(minutos));
    }

    static Solicitud solicitud(String id, Prioridad prioridad, int minutos) {
        return solicitud(id, prioridad, Categoria.SOFTWARE, minutos);
    }

    static Tecnico tecnico(String id, int capacidad, Categoria... especialidades) {
        return new Tecnico(id, "Técnico " + id, List.of(especialidades), capacidad);
    }
}
