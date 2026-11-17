package com.policode.soporte;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Recibe solicitudes, las ordena por prioridad y las asigna con varios hilos.
 *
 * <p>La cola es segura para hilos y la asignación (leer la carga de los técnicos y modificarla) se hace
 * dentro de un candado, de modo que dos hilos nunca ocupan el mismo cupo de un técnico y las
 * solicitudes críticas se atienden primero.
 */
public class Despachador {
    private final List<Tecnico> tecnicos;
    private final Map<String, Solicitud> solicitudes = new LinkedHashMap<>();
    private final PriorityBlockingQueue<Solicitud> cola = new PriorityBlockingQueue<>(64, Solicitud.POR_URGENCIA);
    private final List<Solicitud> enEspera = new ArrayList<>();
    private final ReentrantLock candado = new ReentrantLock();
    private volatile int numHilos;

    public Despachador(List<Tecnico> tecnicos, int numHilos) {
        this.tecnicos = List.copyOf(tecnicos);
        setNumHilos(numHilos);
    }

    public int getNumHilos() {
        return numHilos;
    }

    public void setNumHilos(int numHilos) {
        if (numHilos < 1) {
            throw new IllegalArgumentException("numHilos debe ser al menos 1");
        }
        this.numHilos = numHilos;
    }

    public List<Tecnico> getTecnicos() {
        return tecnicos;
    }

    public List<Solicitud> getSolicitudes() {
        candado.lock();
        try {
            return new ArrayList<>(solicitudes.values());
        } finally {
            candado.unlock();
        }
    }

    public void registrar(Solicitud solicitud) {
        candado.lock();
        try {
            if (solicitudes.containsKey(solicitud.getId())) {
                throw new ErrorValidacion("Ya existe una solicitud con id " + solicitud.getId());
            }
            solicitudes.put(solicitud.getId(), solicitud);
            cola.add(solicitud);
        } finally {
            candado.unlock();
        }
    }

    /** Id para una solicitud nueva: continúa la numeración S0001, S0002… ya registrada. */
    public String siguienteId() {
        candado.lock();
        try {
            int maximo = solicitudes.keySet().stream()
                    .filter(id -> id.matches("S\\d+"))
                    .mapToInt(id -> Integer.parseInt(id.substring(1)))
                    .max()
                    .orElse(0);
            return String.format("S%04d", maximo + 1);
        } finally {
            candado.unlock();
        }
    }

    /**
     * Asigna con un pool de hilos todo lo que esté en cola y devuelve cuántas se asignaron.
     * Las solicitudes sin técnico disponible quedan en espera y se reintentan en la siguiente llamada.
     */
    public int procesarPendientes() {
        candado.lock();
        try {
            cola.addAll(enEspera);
            enEspera.clear();
        } finally {
            candado.unlock();
        }

        int hilos = numHilos;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            Callable<Integer> trabajo = this::trabajador;
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(trabajo));
            }
            int total = 0;
            for (Future<Integer> resultado : resultados) {
                total += resultado.get();
            }
            return total;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorSoporte("El procesamiento fue interrumpido");
        } catch (ExecutionException e) {
            throw new ErrorSoporte("Falló un hilo de trabajo: " + e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    /** Marca la solicitud como resuelta, libera al técnico y reasigna la cola en espera. */
    public int resolver(String idSolicitud) {
        candado.lock();
        try {
            Solicitud solicitud = solicitudes.get(idSolicitud);
            if (solicitud == null) {
                throw new ErrorValidacion("No existe la solicitud " + idSolicitud);
            }
            if (solicitud.getEstado() != Estado.ASIGNADA) {
                throw new ErrorValidacion("La solicitud " + idSolicitud + " no está asignada");
            }
            tecnicos.stream()
                    .filter(t -> t.getId().equals(solicitud.getTecnicoId()))
                    .findFirst()
                    .orElseThrow(() -> new ErrorSoporte("Técnico no encontrado: " + solicitud.getTecnicoId()))
                    .liberar(idSolicitud);
            solicitud.setEstado(Estado.RESUELTA);
        } finally {
            candado.unlock();
        }
        return procesarPendientes();
    }

    private int trabajador() {
        int asignadas = 0;
        while (true) {
            candado.lock();
            try {
                Solicitud solicitud = cola.poll();
                if (solicitud == null) {
                    return asignadas;
                }
                if (asignar(solicitud)) {
                    asignadas++;
                } else {
                    enEspera.add(solicitud);
                }
            } finally {
                candado.unlock();
            }
        }
    }

    /** Se llama con el candado tomado. */
    private boolean asignar(Solicitud solicitud) {
        Optional<Tecnico> elegido = Reglas.elegirTecnico(tecnicos, solicitud.getCategoria());
        if (elegido.isEmpty()) {
            return false;
        }
        Tecnico tecnico = elegido.get();
        solicitud.setTiempoEstimadoHoras(Reglas.calcularTiempoEstimado(solicitud.getCategoria(), tecnico.getCarga()));
        tecnico.asignar(solicitud.getId());
        solicitud.setTecnicoId(tecnico.getId());
        solicitud.setEstado(Estado.ASIGNADA);
        return true;
    }
}
