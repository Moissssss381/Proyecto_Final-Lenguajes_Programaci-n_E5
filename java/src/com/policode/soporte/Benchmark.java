package com.policode.soporte;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Benchmark: mide carga y procesamiento con distintos volúmenes y números de hilos.
 * Uso: java -cp ... com.policode.soporte.Benchmark archivo.json [hilos(1,2,4,8)] [repeticiones(5)]
 * Protocolo común a los tres lenguajes: 1 calentamiento + N repeticiones, se reporta la mediana en ms.
 */
public final class Benchmark {
    private Benchmark() {
    }

    private record Medida(double cargaMs, double procesoMs, int asignadas, int solicitudes, int invalidos, String huella) {
    }

    public static void main(String[] args) {
        Path ruta = Path.of(args[0]);
        String[] hilos = (args.length > 1 ? args[1] : "1,2,4,8").split(",");
        int repeticiones = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        for (String h : hilos) {
            int numHilos = Integer.parseInt(h.trim());
            unaEjecucion(ruta, numHilos);  // calentamiento (JIT)
            List<Medida> medidas = new ArrayList<>();
            for (int i = 0; i < repeticiones; i++) {
                medidas.add(unaEjecucion(ruta, numHilos));
            }
            Medida primera = medidas.get(0);
            System.out.println(String.format(Locale.ROOT,
                    "{\"lenguaje\": \"java\", \"dataset\": \"%s\", \"solicitudes\": %d, \"invalidos\": %d, \"hilos\": %d, "
                            + "\"carga_ms\": %.3f, \"proceso_ms\": %.3f, \"asignadas\": %d, \"huella\": \"%s\"}",
                    nombreSinExtension(ruta), primera.solicitudes(), primera.invalidos(), numHilos,
                    mediana(medidas.stream().map(Medida::cargaMs).collect(Collectors.toList())),
                    mediana(medidas.stream().map(Medida::procesoMs).collect(Collectors.toList())),
                    primera.asignadas(), primera.huella()));
        }
    }

    private static Medida unaEjecucion(Path ruta, int hilos) {
        long t0 = System.nanoTime();
        Persistencia.DatosCargados datos = Persistencia.cargarDatos(ruta);
        long t1 = System.nanoTime();
        Despachador despachador = new Despachador(datos.tecnicos(), hilos);
        datos.solicitudes().forEach(despachador::registrar);
        int asignadas = despachador.procesarPendientes();
        long t2 = System.nanoTime();
        return new Medida((t1 - t0) / 1e6, (t2 - t1) / 1e6, asignadas, datos.solicitudes().size(),
                datos.errores().size(), huella(despachador.getSolicitudes()));
    }

    /** SHA-256 de 'id|técnico|tiempo|estado' ordenado por id: comprueba que los 3 lenguajes dan lo mismo. */
    private static String huella(List<Solicitud> solicitudes) {
        List<String> lineas = new ArrayList<>();
        for (Solicitud s : solicitudes) {
            lineas.add(s.getId() + "|" + (s.getTecnicoId() == null ? "" : s.getTecnicoId()) + "|"
                    + (s.getTiempoEstimadoHoras() == null ? "" : String.format(Locale.ROOT, "%.2f", s.getTiempoEstimadoHoras()))
                    + "|" + s.getEstado().getEtiqueta());
        }
        Collections.sort(lineas);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(String.join("\n", lineas).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ErrorSoporte("SHA-256 no disponible");
        }
    }

    private static double mediana(List<Double> valores) {
        List<Double> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        int n = ordenados.size();
        return n % 2 == 1 ? ordenados.get(n / 2) : (ordenados.get(n / 2 - 1) + ordenados.get(n / 2)) / 2;
    }

    private static String nombreSinExtension(Path ruta) {
        String nombre = ruta.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? nombre : nombre.substring(0, punto);
    }
}
