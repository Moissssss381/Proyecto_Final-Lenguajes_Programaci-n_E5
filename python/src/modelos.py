import datetime

class Prioridad:
    def __init__(self, nombre, orden, horas_max):
        self.nombre = nombre
        self.orden = orden
        self.horas_max = horas_max

PRIORIDADES = {
    "critica": Prioridad("critica", 0, 1),
    "alta": Prioridad("alta", 1, 2),
    "media": Prioridad("media", 2, 8),
    "baja": Prioridad("baja", 3, 24)
}

CATEGORIAS = ["hardware", "software", "redes", "accesos", "otro"]
ESTADOS = ["pendiente", "asignada", "resuelta"]

class Solicitud:
    def __init__(self, data):
        self.id = data.get("id")
        self.titulo = data.get("titulo", "")
        self.descripcion = data.get("descripcion", "")
        self.categoria = data.get("categoria", "otro").lower()
        self.prioridad_str = data.get("prioridad", "baja").lower()
        self.prioridad = PRIORIDADES.get(self.prioridad_str, PRIORIDADES["baja"])
        self.fecha_creacion = data.get("fecha_creacion", datetime.datetime.now().isoformat())
        self.estado = data.get("estado", "pendiente")
        self.tecnico_id = data.get("tecnico_id", None)
        self.tiempo_estimado = data.get("tiempo_estimado_horas", None)

    def cumple_plazo(self):
        if self.tiempo_estimado is None:
            return None
        return self.tiempo_estimado <= self.prioridad.horas_max

class Tecnico:
    def __init__(self, data):
        self.id = data.get("id")
        self.nombre = data.get("nombre", "")
        self.especialidades = [c.lower() for c in data.get("especialidades", [])]
        self.capacidad = data.get("capacidad", 2)
        self.carga = data.get("carga", 0)
        self.solicitudes_activas = list(data.get("solicitudes_activas", []))

    def puede_atender(self, categoria):
        return not self.especialidades or categoria in self.especialidades

    def esta_disponible(self):
        return self.carga < self.capacidad