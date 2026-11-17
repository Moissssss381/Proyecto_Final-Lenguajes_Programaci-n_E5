package com.policode.soporte;

import java.util.ArrayList;
import java.util.List;

/**
 * Técnico con capacidad máxima de solicitudes activas.
 * Sin especialidades significa generalista: puede atender cualquier categoría.
 */
public class Tecnico {
    private final String id;
    private final String nombre;
    private final List<Categoria> especialidades;
    private final int capacidad;
    private final List<String> activas = new ArrayList<>();

    public Tecnico(String id, String nombre, List<Categoria> especialidades, int capacidad) {
        this.id = id;
        this.nombre = nombre;
        this.especialidades = List.copyOf(especialidades);
        this.capacidad = capacidad;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public List<Categoria> getEspecialidades() { return especialidades; }
    public int getCapacidad() { return capacidad; }
    public int getCarga() { return activas.size(); }
    public List<String> getSolicitudesActivas() { return List.copyOf(activas); }
    public boolean estaDisponible() { return getCarga() < capacidad; }
    public boolean esGeneralista() { return especialidades.isEmpty(); }

    public boolean puedeAtender(Categoria categoria) {
        return esGeneralista() || especialidades.contains(categoria);
    }

    public void asignar(String idSolicitud) {
        if (!estaDisponible()) {
            throw new ErrorSoporte("El técnico " + id + " ya alcanzó su capacidad máxima");
        }
        activas.add(idSolicitud);
    }

    public void liberar(String idSolicitud) {
        if (!activas.remove(idSolicitud)) {
            throw new ErrorSoporte("El técnico " + id + " no tiene asignada la solicitud " + idSolicitud);
        }
    }
}
