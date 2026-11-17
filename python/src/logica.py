from collections import defaultdict

def procesar_cola_solicitudes(solicitudes_dict, tecnicos_list):
    pendientes = [s for s in solicitudes_dict.values() if s.estado == "pendiente"]
    pendientes.sort(key=lambda s: (s.prioridad.orden, s.fecha_creacion, s.id))
    
    asignadas = 0
    for sol in pendientes:
        tecnicos_disponibles = [t for t in tecnicos_list if t.esta_disponible() and t.puede_atender(sol.categoria)]
        if not tecnicos_disponibles: 
            continue
        
        tecnicos_disponibles.sort(key=lambda t: (t.carga, 1 if not t.especialidades else 0, t.id))
        mejor_tec = tecnicos_disponibles[0]
        
        horas_base = {"accesos": 0.5, "software": 1.0, "otro": 1.0, "hardware": 1.5, "redes": 2.0}
        base = horas_base.get(sol.categoria, 1.0)
        sol.tiempo_estimado = round(base * (1 + 0.25 * mejor_tec.carga), 2)
        
        sol.tecnico_id = mejor_tec.id
        sol.estado = "asignada"
        mejor_tec.carga += 1
        mejor_tec.solicitudes_activas.append(sol.id)
        asignadas += 1
        
    return asignadas

def resolver_solicitud(solicitud, tecnicos_list):
    """Marca una solicitud asignada como resuelta y libera la carga del técnico.

    Devuelve True si se resolvió, False si no estaba en estado 'asignada'.
    """
    if solicitud.estado != "asignada":
        return False
    solicitud.estado = "resuelta"
    tec = next((t for t in tecnicos_list if t.id == solicitud.tecnico_id), None)
    if tec:
        tec.carga -= 1
        if solicitud.id in tec.solicitudes_activas:
            tec.solicitudes_activas.remove(solicitud.id)
    return True


def generar_resumen(solicitudes_dict):
    total = len(solicitudes_dict)
    en_cola = sum(1 for s in solicitudes_dict.values() if s.estado == "pendiente")
    por_estado = defaultdict(int)
    por_prioridad = defaultdict(int)
    por_categoria = defaultdict(int)
    for s in solicitudes_dict.values():
        por_estado[s.estado] += 1
        por_prioridad[s.prioridad_str] += 1
        por_categoria[s.categoria] += 1
        
    asignadas = [s for s in solicitudes_dict.values() if s.tiempo_estimado is not None]
    tiempo_promedio = sum(s.tiempo_estimado for s in asignadas) / len(asignadas) if asignadas else 0.0
    cumplen = sum(1 for s in asignadas if s.cumple_plazo())
    pct_cumplimiento = (cumplen / len(asignadas) * 100) if asignadas else 0.0
    
    return {
        "total": total,
        "en_cola": en_cola,
        "por_estado": dict(por_estado),
        "por_categoria": dict(por_categoria),
        "por_prioridad": dict(por_prioridad),
        "tiempo_promedio_horas": round(tiempo_promedio, 2),
        "cumplimiento_plazo_pct": round(pct_cumplimiento, 1)
    }