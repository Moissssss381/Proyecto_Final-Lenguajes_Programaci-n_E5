import json
import datetime
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from modelos import Solicitud, Tecnico, PRIORIDADES, CATEGORIAS, ESTADOS
from logica import procesar_cola_solicitudes, resolver_solicitud, generar_resumen

class VentanaPrincipal(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Gestión y priorización de solicitudes de soporte técnico")
        self.geometry("1180x640")
        
        self.solicitudes = {}
        self.tecnicos = []
        self.filtros = {"categoria": "Todas", "prioridad": "Todas", "estado": "Todas", "busqueda": ""}
        
        self.orden_col = None
        self.orden_asc = True
        
        self.crear_widgets()

    def crear_widgets(self):
        frame_botones = tk.Frame(self)
        frame_botones.pack(fill=tk.X, padx=5, pady=5)
        
        tk.Button(frame_botones, text="Cargar JSON...", command=self.cargar_json).pack(side=tk.LEFT, padx=2)
        tk.Button(frame_botones, text="Nueva solicitud...", command=self.nueva_solicitud).pack(side=tk.LEFT, padx=2)
        tk.Button(frame_botones, text="Procesar solicitudes", command=self.procesar_solicitudes).pack(side=tk.LEFT, padx=2)
        tk.Button(frame_botones, text="Resolver seleccionadas", command=self.resolver_seleccionadas).pack(side=tk.LEFT, padx=2)
        tk.Button(frame_botones, text="Guardar resultados...", command=self.guardar_resultados).pack(side=tk.LEFT, padx=2)
        
        tk.Label(frame_botones, text="Hilos:").pack(side=tk.LEFT, padx=(15, 2))
        self.spin_hilos = tk.Spinbox(frame_botones, from_=1, to=16, width=3)
        self.spin_hilos.delete(0, "end")
        self.spin_hilos.insert(0, "4")
        self.spin_hilos.pack(side=tk.LEFT)

        frame_filtros = tk.LabelFrame(self, text="Filtros", padx=5, pady=5)
        frame_filtros.pack(fill=tk.X, padx=5, pady=0)
        
        self.combo_cat = self.crear_combo(frame_filtros, "Categoría:", ["Todas"] + CATEGORIAS)
        self.combo_pri = self.crear_combo(frame_filtros, "Prioridad:", ["Todas"] + list(PRIORIDADES.keys()))
        self.combo_est = self.crear_combo(frame_filtros, "Estado:", ["Todas"] + ESTADOS)
        
        tk.Label(frame_filtros, text="Buscar:").pack(side=tk.LEFT, padx=(10, 2))
        self.entry_buscar = tk.Entry(frame_filtros, width=20)
        self.entry_buscar.pack(side=tk.LEFT)
        self.entry_buscar.bind("<KeyRelease>", lambda e: self.aplicar_filtros())

        self.paned = tk.PanedWindow(self, orient=tk.HORIZONTAL, sashwidth=5)
        self.paned.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        frame_izq = tk.Frame(self.paned)
        self.paned.add(frame_izq, stretch="always", width=780)
        
        columnas = ("Id", "Título", "Categoría", "Prioridad", "Estado", "Técnico", "Tiempo est.", "Plazo")
        self.tabla_sol = ttk.Treeview(frame_izq, columns=columnas, show="headings", selectmode="extended")
        
        for col in columnas:
            self.tabla_sol.heading(col, text=col, command=lambda c=col: self.ordenar_tabla(c))
            self.tabla_sol.column(col, width=80 if col not in ("Título",) else 200)
            
        self.tabla_sol.tag_configure("pendiente", background="#FFF6D6")
        self.tabla_sol.tag_configure("incumple", background="#F9D6D5")
        self.tabla_sol.tag_configure("resuelta", foreground="gray")
        
        scroll_izq = ttk.Scrollbar(frame_izq, orient="vertical", command=self.tabla_sol.yview)
        self.tabla_sol.configure(yscrollcommand=scroll_izq.set)
        
        self.tabla_sol.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll_izq.pack(side=tk.RIGHT, fill=tk.Y)

        frame_der = tk.Frame(self.paned)
        self.paned.add(frame_der, stretch="never", width=380)
        
        tk.Label(frame_der, text="Resumen estadístico", font=("TkDefaultFont", 9, "bold"), anchor="w").pack(fill=tk.X)
        self.txt_resumen = tk.Text(frame_der, height=10, width=40, state=tk.DISABLED, wrap=tk.WORD)
        self.txt_resumen.pack(fill=tk.X, pady=(0, 10))
        
        tk.Label(frame_der, text="Solicitudes por prioridad", font=("TkDefaultFont", 9, "bold"), anchor="w").pack(fill=tk.X)
        self.canvas_barras = tk.Canvas(frame_der, bg="white", height=150, highlightthickness=1, highlightbackground="#CCCCCC")
        self.canvas_barras.pack(fill=tk.X, pady=(0, 10))
        
        tk.Label(frame_der, text="Carga de técnicos", font=("TkDefaultFont", 9, "bold"), anchor="w").pack(fill=tk.X)
        self.tabla_tec = ttk.Treeview(frame_der, columns=("Técnico", "Carga / capacidad"), show="headings", height=5)
        self.tabla_tec.heading("Técnico", text="Técnico")
        self.tabla_tec.heading("Carga / capacidad", text="Carga / capacidad")
        self.tabla_tec.column("Técnico", width=200)
        self.tabla_tec.pack(fill=tk.X)

        self.lbl_estado = tk.Label(self, text=" Carga un archivo JSON para comenzar.", bd=1, relief=tk.SUNKEN, anchor="w")
        self.lbl_estado.pack(side=tk.BOTTOM, fill=tk.X)

    def crear_combo(self, parent, label, valores):
        tk.Label(parent, text=label).pack(side=tk.LEFT, padx=(0, 2))
        combo = ttk.Combobox(parent, values=valores, state="readonly", width=10)
        combo.set("Todas")
        combo.pack(side=tk.LEFT, padx=(0, 10))
        combo.bind("<<ComboboxSelected>>", lambda e: self.aplicar_filtros())
        return combo

    def cargar_json(self):
        ruta = filedialog.askopenfilename(title="Seleccionar archivo JSON", filetypes=[("JSON", "*.json")])
        if not ruta: 
            return
        
        try:
            with open(ruta, 'r', encoding='utf-8') as f:
                datos = json.load(f)
                
            self.solicitudes.clear()
            self.tecnicos.clear()
            
            for t_data in datos.get("tecnicos", []):
                self.tecnicos.append(Tecnico(t_data))
                
            for s_data in datos.get("solicitudes", []):
                sol = Solicitud(s_data)
                self.solicitudes[sol.id] = sol
                
            self.refrescar_todo()
            self.lbl_estado.config(text=f" Cargadas {len(self.solicitudes)} solicitudes y {len(self.tecnicos)} técnicos desde {ruta.split('/')[-1]}.")
        except Exception as e:
            messagebox.showerror("Error", f"No se pudo cargar el archivo:\n{e}")

    def nueva_solicitud(self):
        if not self.tecnicos and not self.solicitudes:
            if not messagebox.askyesno("Sin datos", "No hay datos cargados. ¿Deseas crear una solicitud de todos modos?"):
                return

        top = tk.Toplevel(self)
        top.title("Nueva solicitud")
        top.geometry("300x250")
        top.transient(self)
        top.grab_set()

        tk.Label(top, text="Título:").pack(anchor="w", padx=10, pady=(10,0))
        entry_titulo = tk.Entry(top, width=35)
        entry_titulo.pack(padx=10)

        tk.Label(top, text="Descripción:").pack(anchor="w", padx=10, pady=(10,0))
        entry_desc = tk.Entry(top, width=35)
        entry_desc.pack(padx=10)

        tk.Label(top, text="Categoría:").pack(anchor="w", padx=10, pady=(10,0))
        combo_cat = ttk.Combobox(top, values=CATEGORIAS, state="readonly")
        combo_cat.current(1)
        combo_cat.pack(padx=10)

        tk.Label(top, text="Prioridad:").pack(anchor="w", padx=10, pady=(10,0))
        combo_pri = ttk.Combobox(top, values=list(PRIORIDADES.keys()), state="readonly")
        combo_pri.current(2)
        combo_pri.pack(padx=10)

        def guardar():
            titulo = entry_titulo.get().strip()
            if not titulo:
                messagebox.showerror("Error", "El título es obligatorio", parent=top)
                return
            
            max_id = 0
            for sol_id in self.solicitudes.keys():
                if sol_id.startswith("S") and sol_id[1:].isdigit():
                    max_id = max(max_id, int(sol_id[1:]))
            nuevo_id = f"S{max_id + 1:04d}"

            nueva_sol = Solicitud({
                "id": nuevo_id,
                "titulo": titulo,
                "descripcion": entry_desc.get().strip(),
                "categoria": combo_cat.get(),
                "prioridad": combo_pri.get(),
                "fecha_creacion": datetime.datetime.now().isoformat()
            })
            
            self.solicitudes[nuevo_id] = nueva_sol
            self.refrescar_todo()
            self.lbl_estado.config(text=f" Solicitud {nuevo_id} registrada.")
            top.destroy()

        tk.Button(top, text="Guardar", command=guardar).pack(pady=15)

    def procesar_solicitudes(self):
        if not self.solicitudes: 
            return
        asignadas = procesar_cola_solicitudes(self.solicitudes, self.tecnicos)
        self.refrescar_todo()
        self.lbl_estado.config(text=f" Procesamiento terminado: {asignadas} solicitudes asignadas.")

    def resolver_seleccionadas(self):
        seleccion = self.tabla_sol.selection()
        if not seleccion: 
            return
        
        resueltas = 0
        for item in seleccion:
            sol_id = self.tabla_sol.item(item, "values")[0]
            sol = self.solicitudes.get(sol_id)
            if sol and resolver_solicitud(sol, self.tecnicos):
                resueltas += 1
                
        self.refrescar_todo()
        self.lbl_estado.config(text=f" {resueltas} solicitudes resueltas.")

    def guardar_resultados(self):
        if not self.solicitudes:
            messagebox.showinfo("Sin datos", "No hay solicitudes para guardar.")
            return
            
        ruta = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JSON", "*.json")], initialfile="resultados.json")
        if not ruta: 
            return
        
        try:
            resumen = generar_resumen(self.solicitudes)
            solicitudes_ordenadas = sorted(self.solicitudes.values(), key=lambda s: (s.prioridad.orden, s.fecha_creacion, s.id))
            
            json_sols = []
            for s in solicitudes_ordenadas:
                json_sols.append({
                    "id": s.id, "titulo": s.titulo, "descripcion": s.descripcion,
                    "categoria": s.categoria, "prioridad": s.prioridad_str,
                    "fecha_creacion": s.fecha_creacion, "estado": s.estado,
                    "tecnico_id": s.tecnico_id, "tiempo_estimado_horas": s.tiempo_estimado,
                    "cumple_plazo": s.cumple_plazo()
                })
                
            json_tecs = [{"id": t.id, "nombre": t.nombre, "especialidades": t.especialidades, "capacidad": t.capacidad, "carga": t.carga} for t in self.tecnicos]
            salida = {"resumen": resumen, "tecnicos": json_tecs, "solicitudes": json_sols}
            
            with open(ruta, 'w', encoding='utf-8') as f:
                json.dump(salida, f, indent=2, ensure_ascii=False)
                
            self.lbl_estado.config(text=f" Resultados guardados en {ruta.split('/')[-1]}.")
        except Exception as e:
            messagebox.showerror("Error", f"No se pudo guardar el archivo:\n{e}")

    def ordenar_tabla(self, columna):
        if self.orden_col == columna:
            self.orden_asc = not self.orden_asc
        else:
            self.orden_col = columna
            self.orden_asc = True
        self.refrescar_tabla()

    def aplicar_filtros(self):
        self.filtros["categoria"] = self.combo_cat.get()
        self.filtros["prioridad"] = self.combo_pri.get()
        self.filtros["estado"] = self.combo_est.get()
        self.filtros["busqueda"] = self.entry_buscar.get().lower()
        self.refrescar_tabla()

    def refrescar_todo(self):
        self.refrescar_tabla()
        self.refrescar_estadisticas()

    def refrescar_tabla(self):
        for item in self.tabla_sol.get_children():
            self.tabla_sol.delete(item)
            
        lista_mostrar = list(self.solicitudes.values())
        
        if self.filtros["categoria"] != "Todas":
            lista_mostrar = [s for s in lista_mostrar if s.categoria == self.filtros["categoria"]]
        if self.filtros["prioridad"] != "Todas":
            lista_mostrar = [s for s in lista_mostrar if s.prioridad_str == self.filtros["prioridad"]]
        if self.filtros["estado"] != "Todas":
            lista_mostrar = [s for s in lista_mostrar if s.estado == self.filtros["estado"]]
        if self.filtros["busqueda"]:
            lista_mostrar = [s for s in lista_mostrar if self.filtros["busqueda"] in s.titulo.lower() or self.filtros["busqueda"] in s.id.lower()]
            
        if self.orden_col:
            col_idx = self.tabla_sol["columns"].index(self.orden_col)
            def get_sort_key(sol):
                if col_idx == 0: return sol.id
                if col_idx == 1: return sol.titulo.lower()
                if col_idx == 2: return sol.categoria
                if col_idx == 3: return sol.prioridad.orden
                if col_idx == 4: return sol.estado
                if col_idx == 5: return sol.tecnico_id or ""
                if col_idx == 6: return sol.tiempo_estimado if sol.tiempo_estimado is not None else -1
                if col_idx == 7: 
                    c = sol.cumple_plazo()
                    return 0 if c is None else (1 if c else 2)
                return ""
            lista_mostrar.sort(key=get_sort_key, reverse=not self.orden_asc)

        for sol in lista_mostrar:
            tag = ""
            if sol.cumple_plazo() is False: tag = "incumple"
            elif sol.estado == "pendiente": tag = "pendiente"
            elif sol.estado == "resuelta": tag = "resuelta"
                
            plazo = "—"
            if sol.cumple_plazo() is not None:
                plazo = "Cumple" if sol.cumple_plazo() else "Excede"
                
            tiempo = f"{sol.tiempo_estimado:.2f}" if sol.tiempo_estimado is not None else "—"
            tecnico = sol.tecnico_id if sol.tecnico_id else "—"
            
            self.tabla_sol.insert("", tk.END, values=(sol.id, sol.titulo, sol.categoria, sol.prioridad_str, 
                                                      sol.estado, tecnico, tiempo, plazo), tags=(tag,))

    def refrescar_estadisticas(self):
        resumen = generar_resumen(self.solicitudes)
        por_prioridad = resumen["por_prioridad"]
            
        resumen_txt = f"Total de solicitudes: {resumen['total']}\n"
        resumen_txt += f"En cola (sin técnico): {resumen['en_cola']}\n"
        resumen_txt += f"Por prioridad: critica={por_prioridad.get('critica', 0)}, alta={por_prioridad.get('alta', 0)}, "
        resumen_txt += f"media={por_prioridad.get('media', 0)}, baja={por_prioridad.get('baja', 0)}\n"
        
        self.txt_resumen.config(state=tk.NORMAL)
        self.txt_resumen.delete(1.0, tk.END)
        self.txt_resumen.insert(tk.END, resumen_txt)
        self.txt_resumen.config(state=tk.DISABLED)
        
        self.dibujar_barras(por_prioridad)
        
        for item in self.tabla_tec.get_children():
            self.tabla_tec.delete(item)
        for t in self.tecnicos:
            self.tabla_tec.insert("", tk.END, values=(t.nombre, f"{t.carga} / {t.capacidad}"))

    def dibujar_barras(self, datos):
        self.canvas_barras.delete("all")
        if not datos: 
            return
        
        w = self.canvas_barras.winfo_width()
        h = self.canvas_barras.winfo_height()
        if w <= 1: w = 380
        
        colores = {"critica": "#D9534F", "alta": "#F0AD4E", "media": "#5BC0DE", "baja": "#5CB85C"}
        max_val = max(datos.values()) if datos.values() else 1
        if max_val == 0: max_val = 1
        
        ancho_col = w / 4
        base_y = h - 25
        
        for i, prio in enumerate(["critica", "alta", "media", "baja"]):
            cant = datos.get(prio, 0)
            x0 = i * ancho_col + 10
            x1 = (i + 1) * ancho_col - 10
            altura_barra = (base_y - 20) * (cant / max_val)
            y0 = base_y - altura_barra
            
            self.canvas_barras.create_rectangle(x0, y0, x1, base_y, fill=colores[prio], outline="")
            self.canvas_barras.create_text((x0+x1)/2, y0-10, text=str(cant), font=("Arial", 9))
            self.canvas_barras.create_text((x0+x1)/2, h-10, text=prio, font=("Arial", 8))