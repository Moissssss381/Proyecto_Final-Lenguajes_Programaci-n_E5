package com.policode.soporte;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resumen estadístico de solicitudes y técnicos. */
public final class Estadisticas {
    private Estadisticas() {
    }

    public record CargaTecnico(String id, String nombre, int carga, int capacidad) {
    }

    public record Resumen(int total, int enCola, Map<String, Integer> porEstado,
                          Map<String, Integer> porCategoria, Map<String, Integer> porPrioridad,
                          double tiempoPromedioHoras, double cumplimientoPlazoPct,
                          List<CargaTecnico> cargaTecnicos) {
    }

    public static Resumen generarResumen(List<Solicitud> solicitudes, List<Tecnico> tecnicos) {
        List<Solicitud> asignadas = solicitudes.stream()
                .filter(s -> s.getTiempoEstimadoHoras() != null)
                .toList();
        double promedio = asignadas.stream().mapToDouble(Solicitud::getTiempoEstimadoHoras).average().orElse(0.0);
        long cumplen = asignadas.stream().filter(s -> s.cumplePlazo().orElse(false)).count();
        double porcentaje = asignadas.isEmpty() ? 0.0 : 100.0 * cumplen / asignadas.size();

        return new Resumen(
                solicitudes.size(),
                (int) solicitudes.stream().filter(s -> s.getEstado() == Estado.PENDIENTE).count(),
                contarPor(solicitudes, s -> s.getEstado().getEtiqueta(),
                        Arrays.stream(Estado.values()).map(Estado::getEtiqueta).toList()),
                contarPor(solicitudes, s -> s.getCategoria().getEtiqueta(),
                        Arrays.stream(Categoria.values()).map(Categoria::getEtiqueta).toList()),
                contarPor(solicitudes, s -> s.getPrioridad().getEtiqueta(),
                        Arrays.stream(Prioridad.values()).map(Prioridad::getEtiqueta).toList()),
                redondear(promedio, 100.0),
                redondear(porcentaje, 10.0),
                tecnicos.stream()
                        .map(t -> new CargaTecnico(t.getId(), t.getNombre(), t.getCarga(), t.getCapacidad()))
                        .toList());
    }

    /** Cuenta solicitudes según 'clave' (función de orden superior), incluyendo los valores en cero. */
    private static Map<String, Integer> contarPor(List<Solicitud> solicitudes, Function<Solicitud, String> clave,
                                                  List<String> valoresPosibles) {
        Map<String, Long> conteo = solicitudes.stream().collect(Collectors.groupingBy(clave, Collectors.counting()));
        Map<String, Integer> resultado = new LinkedHashMap<>();
        for (String valor : valoresPosibles) {
            resultado.put(valor, conteo.getOrDefault(valor, 0L).intValue());
        }
        return resultado;
    }

    private static double redondear(double valor, double factor) {
        return Math.round(valor * factor) / factor;
    }

    /** Texto legible del resumen, para la interfaz o la consola. */
    public static String formatear(Resumen resumen) {
        return String.join("\n",
                "Total de solicitudes: " + resumen.total(),
                "En cola (sin técnico): " + resumen.enCola(),
                linea("Por estado", resumen.porEstado()),
                linea("Por prioridad", resumen.porPrioridad()),
                linea("Por categoría", resumen.porCategoria()),
                "Tiempo estimado promedio: " + resumen.tiempoPromedioHoras() + " h",
                "Cumplimiento del plazo (asignadas): " + resumen.cumplimientoPlazoPct() + " %");
    }

    private static String linea(String titulo, Map<String, Integer> datos) {
        return titulo + ": " + datos.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
