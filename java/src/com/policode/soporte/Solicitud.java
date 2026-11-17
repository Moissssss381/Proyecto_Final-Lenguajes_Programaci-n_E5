package com.policode.soporte;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

public class Solicitud {
    /** Más urgente primero; a igual prioridad, la más antigua; el id desempata. */
    public static final Comparator<Solicitud> POR_URGENCIA = Comparator
            .comparingInt((Solicitud s) -> s.getPrioridad().getOrden())
            .thenComparing(Solicitud::getFechaCreacion)
            .thenComparing(Solicitud::getId);

    private final String id;
    private final String titulo;
    private final String descripcion;
    private final Categoria categoria;
    private final Prioridad prioridad;
    private final LocalDateTime fechaCreacion;
    private Estado estado = Estado.PENDIENTE;
    private String tecnicoId;
    private Double tiempoEstimadoHoras;

    public Solicitud(String id, String titulo, String descripcion, Categoria categoria,
                     Prioridad prioridad, LocalDateTime fechaCreacion) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.prioridad = prioridad;
        this.fechaCreacion = fechaCreacion;
    }

    public String getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public Categoria getCategoria() { return categoria; }
    public Prioridad getPrioridad() { return prioridad; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public Estado getEstado() { return estado; }
    public String getTecnicoId() { return tecnicoId; }
    public Double getTiempoEstimadoHoras() { return tiempoEstimadoHoras; }

    public void setEstado(Estado estado) { this.estado = estado; }
    public void setTecnicoId(String tecnicoId) { this.tecnicoId = tecnicoId; }
    public void setTiempoEstimadoHoras(Double horas) { this.tiempoEstimadoHoras = horas; }

    /** Vacío mientras no tenga tiempo estimado (no está asignada). */
    public Optional<Boolean> cumplePlazo() {
        if (tiempoEstimadoHoras == null) {
            return Optional.empty();
        }
        return Optional.of(tiempoEstimadoHoras <= prioridad.getHorasMaximas());
    }
}
