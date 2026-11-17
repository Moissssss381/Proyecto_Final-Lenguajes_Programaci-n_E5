"""Pruebas automatizadas de modelos.py y logica.py.

Ejecutar desde la carpeta del proyecto:
    python -m unittest test_logica -v
"""
import unittest

from logica import generar_resumen, procesar_cola_solicitudes, resolver_solicitud
from modelos import PRIORIDADES, Solicitud, Tecnico


def sol(id_, prioridad="media", categoria="software", fecha="2025-01-01T10:00:00"):
    return Solicitud({
        "id": id_, "titulo": f"Solicitud {id_}", "categoria": categoria,
        "prioridad": prioridad, "fecha_creacion": fecha,
    })


def tec(id_, especialidades=None, capacidad=2, carga=0):
    return Tecnico({
        "id": id_, "nombre": f"Tecnico {id_}",
        "especialidades": especialidades or [], "capacidad": capacidad, "carga": carga,
    })


def como_dict(*solicitudes):
    return {s.id: s for s in solicitudes}


class TestModelos(unittest.TestCase):
    def test_plazos_por_prioridad(self):
        horas = {n: p.horas_max for n, p in PRIORIDADES.items()}
        self.assertEqual(horas, {"critica": 1, "alta": 2, "media": 8, "baja": 24})

    def test_solicitud_normaliza_datos_y_prioridad_invalida(self):
        s = Solicitud({"id": "S1", "categoria": "HARDWARE", "prioridad": "URGENTE"})
        self.assertEqual(s.categoria, "hardware")
        self.assertEqual(s.prioridad.nombre, "baja")
        self.assertEqual(s.estado, "pendiente")

    def test_cumple_plazo(self):
        s = sol("S1", "alta")
        self.assertIsNone(s.cumple_plazo())
        s.tiempo_estimado = 2.0
        self.assertTrue(s.cumple_plazo())
        s.tiempo_estimado = 2.01
        self.assertFalse(s.cumple_plazo())

    def test_tecnico_puede_atender_y_disponibilidad(self):
        especialista = tec("T1", ["redes"], capacidad=1)
        generalista = tec("T2")
        self.assertTrue(especialista.puede_atender("redes"))
        self.assertFalse(especialista.puede_atender("hardware"))
        self.assertTrue(generalista.puede_atender("hardware"))
        self.assertTrue(especialista.esta_disponible())
        especialista.carga = 1
        self.assertFalse(especialista.esta_disponible())


class TestProcesarCola(unittest.TestCase):
    def test_criticas_se_asignan_primero(self):
        baja = sol("S1", "baja", fecha="2025-01-01T08:00:00")
        critica = sol("S2", "critica", fecha="2025-01-01T09:00:00")
        procesar_cola_solicitudes(como_dict(baja, critica), [tec("T1", capacidad=1)])
        self.assertEqual(critica.estado, "asignada")
        self.assertEqual(baja.estado, "pendiente")

    def test_sin_tecnico_disponible_permanece_en_cola(self):
        s = sol("S1", "alta")
        asignadas = procesar_cola_solicitudes(como_dict(s), [tec("T1", capacidad=1, carga=1)])
        self.assertEqual(asignadas, 0)
        self.assertEqual(s.estado, "pendiente")
        self.assertIsNone(s.tecnico_id)
        self.assertIsNone(s.tiempo_estimado)

    def test_respeta_especialidad(self):
        s = sol("S1", categoria="redes")
        procesar_cola_solicitudes(como_dict(s), [tec("T1", ["hardware"]), tec("T2", ["redes"])])
        self.assertEqual(s.tecnico_id, "T2")

    def test_formula_de_tiempo_con_carga(self):
        # redes: base 2.0 h; tiempo = base * (1 + 0.25 * carga)
        s1 = sol("S1", "alta", "redes", "2025-01-01T08:00:00")
        s2 = sol("S2", "alta", "redes", "2025-01-01T09:00:00")
        procesar_cola_solicitudes(como_dict(s1, s2), [tec("T1", capacidad=2)])
        self.assertEqual(s1.tiempo_estimado, 2.0)
        self.assertEqual(s2.tiempo_estimado, 2.5)
        self.assertTrue(s1.cumple_plazo())
        self.assertFalse(s2.cumple_plazo())

    def test_balancea_carga_entre_tecnicos(self):
        s1, s2 = sol("S1"), sol("S2", fecha="2025-01-01T11:00:00")
        t1, t2 = tec("T1"), tec("T2")
        procesar_cola_solicitudes(como_dict(s1, s2), [t1, t2])
        self.assertEqual({s1.tecnico_id, s2.tecnico_id}, {"T1", "T2"})
        self.assertEqual((t1.carga, t2.carga), (1, 1))

    def test_asignacion_incrementa_carga_y_registra_solicitud(self):
        s, t = sol("S1"), tec("T1")
        asignadas = procesar_cola_solicitudes(como_dict(s), [t])
        self.assertEqual(asignadas, 1)
        self.assertEqual(t.carga, 1)
        self.assertEqual(t.solicitudes_activas, ["S1"])

    def test_no_reasigna_solicitudes_ya_asignadas(self):
        s = sol("S1")
        s.estado = "asignada"
        asignadas = procesar_cola_solicitudes(como_dict(s), [tec("T1")])
        self.assertEqual(asignadas, 0)


class TestResolver(unittest.TestCase):
    def test_resolver_libera_carga_del_tecnico(self):
        s, t = sol("S1"), tec("T1")
        procesar_cola_solicitudes(como_dict(s), [t])
        self.assertTrue(resolver_solicitud(s, [t]))
        self.assertEqual(s.estado, "resuelta")
        self.assertEqual(t.carga, 0)
        self.assertNotIn("S1", t.solicitudes_activas)

    def test_no_resuelve_solicitudes_pendientes(self):
        s = sol("S1")
        self.assertFalse(resolver_solicitud(s, []))
        self.assertEqual(s.estado, "pendiente")


class TestResumen(unittest.TestCase):
    def test_resumen_estadistico(self):
        s1 = sol("S1", "critica", "redes")
        s2 = sol("S2", "baja", "software", "2025-01-02T10:00:00")
        solicitudes = como_dict(s1, s2)
        procesar_cola_solicitudes(solicitudes, [tec("T1", capacidad=1)])  # S1 asignada, S2 en cola

        resumen = generar_resumen(solicitudes)
        self.assertEqual(resumen["total"], 2)
        self.assertEqual(resumen["en_cola"], 1)
        self.assertEqual(resumen["por_estado"], {"asignada": 1, "pendiente": 1})
        self.assertEqual(resumen["por_prioridad"], {"critica": 1, "baja": 1})
        self.assertEqual(resumen["tiempo_promedio_horas"], 2.0)
        self.assertEqual(resumen["cumplimiento_plazo_pct"], 0.0)  # 2 h > 1 h (crítica)

    def test_resumen_sin_solicitudes(self):
        resumen = generar_resumen({})
        self.assertEqual(resumen["total"], 0)
        self.assertEqual(resumen["tiempo_promedio_horas"], 0.0)
        self.assertEqual(resumen["cumplimiento_plazo_pct"], 0.0)


if __name__ == "__main__":
    unittest.main()
