package com.policode.soporte;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DespachadorTest {

    @Test
    public void lasCriticasSeAsignanPrimero() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 1)), 4);
        Solicitud baja = Fabrica.solicitud("BAJA", Prioridad.BAJA, 0);       // llegó antes
        Solicitud critica = Fabrica.solicitud("CRIT", Prioridad.CRITICA, 60);  // llegó después
        despachador.registrar(baja);
        despachador.registrar(critica);
        despachador.procesarPendientes();
        assertEquals(Estado.ASIGNADA, critica.getEstado());
        assertEquals(Estado.PENDIENTE, baja.getEstado());
    }

    @Test
    public void aIgualPrioridadSeAtiendeLaMasAntigua() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 1)), 2);
        Solicitud nueva = Fabrica.solicitud("NUEVA", Prioridad.MEDIA, 30);
        Solicitud vieja = Fabrica.solicitud("VIEJA", Prioridad.MEDIA, 0);
        despachador.registrar(nueva);
        despachador.registrar(vieja);
        despachador.procesarPendientes();
        assertEquals(Estado.ASIGNADA, vieja.getEstado());
        assertEquals(Estado.PENDIENTE, nueva.getEstado());
    }

    @Test
    public void sinTecnicoDisponibleLaSolicitudPermaneceEnCola() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 2, Categoria.HARDWARE)), 2);
        Solicitud solicitud = Fabrica.solicitud("S1", Prioridad.MEDIA, Categoria.REDES, 0);  // nadie atiende redes
        despachador.registrar(solicitud);
        assertEquals(0, despachador.procesarPendientes());
        assertEquals(Estado.PENDIENTE, solicitud.getEstado());
        assertNull(solicitud.getTecnicoId());
    }

    @Test
    public void resolverLiberaAlTecnicoYReasignaLaCola() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 1)), 2);
        Solicitud primera = Fabrica.solicitud("S1", Prioridad.MEDIA, 0);
        Solicitud segunda = Fabrica.solicitud("S2", Prioridad.MEDIA, 1);
        despachador.registrar(primera);
        despachador.registrar(segunda);
        despachador.procesarPendientes();
        assertEquals(Estado.PENDIENTE, segunda.getEstado());
        despachador.resolver("S1");
        assertEquals(Estado.RESUELTA, primera.getEstado());
        assertEquals(Estado.ASIGNADA, segunda.getEstado());
    }

    @Test
    public void noSePuedeRegistrarNiResolverLoInvalido() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 2)), 2);
        despachador.registrar(Fabrica.solicitud("S1", Prioridad.MEDIA, 0));
        assertThrows(ErrorValidacion.class, () -> despachador.registrar(Fabrica.solicitud("S1", Prioridad.MEDIA, 5)));
        assertThrows(ErrorValidacion.class, () -> despachador.resolver("NO-EXISTE"));
        assertThrows(ErrorValidacion.class, () -> despachador.resolver("S1"));  // todavía no está asignada
    }

    @Test
    public void seEligeAlTecnicoDeMenorCarga() {
        Tecnico ocupado = Fabrica.tecnico("T1", 3);
        Tecnico libre = Fabrica.tecnico("T2", 3);
        ocupado.asignar("X");
        assertSame(libre, Reglas.elegirTecnico(List.of(ocupado, libre), Categoria.SOFTWARE).orElseThrow());
    }

    @Test
    public void sePrefiereEspecialistaSobreGeneralistaConIgualCarga() {
        Tecnico generalista = Fabrica.tecnico("T1", 2);
        Tecnico especialista = Fabrica.tecnico("T2", 2, Categoria.REDES);
        assertSame(especialista, Reglas.elegirTecnico(List.of(generalista, especialista), Categoria.REDES).orElseThrow());
    }

    @Test
    public void tiempoEstimadoCreceConLaCargaDelTecnico() {
        assertEquals(2.0, Reglas.calcularTiempoEstimado(Categoria.REDES, 0), 1e-9);
        assertEquals(3.0, Reglas.calcularTiempoEstimado(Categoria.REDES, 2), 1e-9);
    }

    @Test
    public void siguienteIdContinuaLaNumeracion() {
        Despachador despachador = new Despachador(List.of(Fabrica.tecnico("T1", 2)), 2);
        assertEquals("S0001", despachador.siguienteId());
        despachador.registrar(Fabrica.solicitud("S0007", Prioridad.MEDIA, 0));
        despachador.registrar(Fabrica.solicitud("X1", Prioridad.MEDIA, 1));  // ids ajenos a S#### se ignoran
        assertEquals("S0008", despachador.siguienteId());
    }

    @Test
    public void plazosPorPrioridad() {
        assertEquals(1, Prioridad.CRITICA.getHorasMaximas());
        assertEquals(2, Prioridad.ALTA.getHorasMaximas());
        assertEquals(8, Prioridad.MEDIA.getHorasMaximas());
        assertEquals(24, Prioridad.BAJA.getHorasMaximas());
    }

    @Test
    public void concurrenciaNuncaSobrepasaLaCapacidadNiDuplicaAsignaciones() {
        List<Tecnico> tecnicos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tecnicos.add(Fabrica.tecnico("T" + i, 3));  // 15 cupos en total
        }
        Despachador despachador = new Despachador(tecnicos, 8);
        List<Solicitud> solicitudes = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Solicitud s = Fabrica.solicitud(String.format("S%03d", i), Prioridad.MEDIA, i);
            solicitudes.add(s);
            despachador.registrar(s);
        }
        assertEquals(15, despachador.procesarPendientes());
        assertTrue(tecnicos.stream().allMatch(t -> t.getCarga() <= t.getCapacidad()));
        List<String> activos = tecnicos.stream().flatMap(t -> t.getSolicitudesActivas().stream()).toList();
        Set<String> sinRepetir = new HashSet<>(activos);
        assertEquals(15, activos.size());
        assertEquals(15, sinRepetir.size());
        assertEquals(185, solicitudes.stream().filter(s -> s.getEstado() == Estado.PENDIENTE).count());
    }
}
