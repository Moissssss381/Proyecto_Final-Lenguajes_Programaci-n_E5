"""Despachador: ordena las solicitudes por prioridad y las asigna con varios hilos.

La cola es segura para hilos y la asignación (leer la carga de los técnicos y modificarla) se hace
dentro de un candado, de modo que dos hilos nunca ocupan el mismo cupo de un técnico y las
solicitudes críticas se atienden primero.
"""
import re
import threading
from concurrent.futures import ThreadPoolExecutor
from queue import Empty, PriorityQueue

from errores import ErrorSoporte, ErrorValidacion
from modelos import Estado
from reglas import calcular_tiempo_estimado, elegir_tecnico


class Despachador:
    def __init__(self, tecnicos, num_hilos=4):
        self.tecnicos = list(tecnicos)
        self._solicitudes = {}
        self._cola = PriorityQueue()
        self._en_espera = []
        self._candado = threading.RLock()
        self._num_hilos = 1
        self.num_hilos = num_hilos

    @property
    def num_hilos(self):
        return self._num_hilos

    @num_hilos.setter
    def num_hilos(self, valor):
        if valor < 1:
            raise ValueError("num_hilos debe ser al menos 1")
        self._num_hilos = valor

    @property
    def solicitudes(self):
        with self._candado:
            return list(self._solicitudes.values())

    def registrar(self, solicitud):
        with self._candado:
            if solicitud.id in self._solicitudes:
                raise ErrorValidacion(f"Ya existe una solicitud con id {solicitud.id}")
            self._solicitudes[solicitud.id] = solicitud
            self._encolar(solicitud)

    def siguiente_id(self):
        """Id para una solicitud nueva: continúa la numeración S0001, S0002… ya registrada."""
        with self._candado:
            numeros = [int(i[1:]) for i in self._solicitudes if re.fullmatch(r"S\d+", i)]
            return f"S{max(numeros, default=0) + 1:04d}"

    def procesar_pendientes(self):
        """Asigna con un pool de hilos todo lo que esté en cola y devuelve cuántas se asignaron.

        Las solicitudes sin técnico disponible quedan en espera y se reintentan en la siguiente llamada.
        """
        with self._candado:
            for solicitud in self._en_espera:
                self._encolar(solicitud)
            self._en_espera.clear()

        hilos = self._num_hilos
        try:
            with ThreadPoolExecutor(max_workers=hilos) as pool:
                resultados = [pool.submit(self._trabajador) for _ in range(hilos)]
                return sum(r.result() for r in resultados)
        except ErrorSoporte:
            raise
        except Exception as error:
            raise ErrorSoporte(f"Falló un hilo de trabajo: {error}") from error

    def resolver(self, id_solicitud):
        """Marca la solicitud como resuelta, libera al técnico y reasigna la cola en espera."""
        with self._candado:
            solicitud = self._solicitudes.get(id_solicitud)
            if solicitud is None:
                raise ErrorValidacion(f"No existe la solicitud {id_solicitud}")
            if solicitud.estado != Estado.ASIGNADA:
                raise ErrorValidacion(f"La solicitud {id_solicitud} no está asignada")
            tecnico = next((t for t in self.tecnicos if t.id == solicitud.tecnico_id), None)
            if tecnico is None:
                raise ErrorSoporte(f"Técnico no encontrado: {solicitud.tecnico_id}")
            tecnico.liberar(id_solicitud)
            solicitud.estado = Estado.RESUELTA
        return self.procesar_pendientes()

    def _encolar(self, solicitud):
        self._cola.put((solicitud.clave_urgencia(), solicitud))

    def _trabajador(self):
        asignadas = 0
        while True:
            with self._candado:
                try:
                    _, solicitud = self._cola.get_nowait()
                except Empty:
                    return asignadas
                if self._asignar(solicitud):
                    asignadas += 1
                else:
                    self._en_espera.append(solicitud)

    def _asignar(self, solicitud):
        """Se llama con el candado tomado."""
        tecnico = elegir_tecnico(self.tecnicos, solicitud.categoria)
        if tecnico is None:
            return False
        solicitud.tiempo_estimado_horas = calcular_tiempo_estimado(solicitud.categoria, tecnico.carga)
        tecnico.asignar(solicitud.id)
        solicitud.tecnico_id = tecnico.id
        solicitud.estado = Estado.ASIGNADA
        return True
