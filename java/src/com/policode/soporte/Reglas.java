package com.policode.soporte;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Reglas de negocio: tiempo estimado de atención y elección del técnico. */
public final class Reglas {
    /** Horas base de atención por categoría (supuesto del proyecto, ajustable). */
    private static final Map<Categoria, Double> HORAS_BASE = new EnumMap<>(Categoria.class);

    /** Cada solicitud activa del técnico aumenta el tiempo estimado en este porcentaje. */
    private static final double FACTOR_POR_CARGA = 0.25;

    static {
        HORAS_BASE.put(Categoria.ACCESOS, 0.5);
        HORAS_BASE.put(Categoria.SOFTWARE, 1.0);
        HORAS_BASE.put(Categoria.OTRO, 1.0);
        HORAS_BASE.put(Categoria.HARDWARE, 1.5);
        HORAS_BASE.put(Categoria.REDES, 2.0);
    }

    private Reglas() {
    }

    /** tiempo = horas_base * (1 + 0.25 * solicitudes_activas_del_tecnico). */
    public static double calcularTiempoEstimado(Categoria categoria, int cargaTecnico) {
        double horas = HORAS_BASE.get(categoria) * (1 + FACTOR_POR_CARGA * cargaTecnico);
        return Math.round(horas * 100.0) / 100.0;
    }

    /**
     * Entre los técnicos disponibles que atienden la categoría elige el de menor carga; a igual carga
     * prefiere al especialista sobre el generalista y el id desempata.
     */
    public static Optional<Tecnico> elegirTecnico(Collection<Tecnico> tecnicos, Categoria categoria) {
        return tecnicos.stream()
                .filter(t -> t.estaDisponible() && t.puedeAtender(categoria))
                .min(Comparator.comparingInt(Tecnico::getCarga)
                        .thenComparing(Tecnico::esGeneralista)
                        .thenComparing(Tecnico::getId));
    }
}
