package com.policode.soporte;

import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Interfaz gráfica (Swing): tabla con filtros y orden, resumen y tablero por prioridad. */
public class VentanaPrincipal extends JFrame {
    private static final String TODAS = "Todas";
    private static final Color COLOR_PENDIENTE = new Color(0xFFF6D6);
    private static final Color COLOR_INCUMPLE = new Color(0xF9D6D5);

    private final ModeloSolicitudes modelo = new ModeloSolicitudes();
    private final JTable tabla = new JTable(modelo);
    private final TableRowSorter<ModeloSolicitudes> ordenador = new TableRowSorter<>(modelo);
    private final JComboBox<String> filtroCategoria = crearCombo(
            Arrays.stream(Categoria.values()).map(Categoria::getEtiqueta));
    private final JComboBox<String> filtroPrioridad = crearCombo(
            Arrays.stream(Prioridad.values()).map(Prioridad::getEtiqueta));
    private final JComboBox<String> filtroEstado = crearCombo(
            Arrays.stream(Estado.values()).map(Estado::getEtiqueta));
    private final JTextField busqueda = new JTextField(16);
    private final JSpinner hilos = new JSpinner(new SpinnerNumberModel(4, 1, 16, 1));
    private final JTextArea textoResumen = new JTextArea(13, 40);
    private final PanelBarras panelBarras = new PanelBarras();
    private final DefaultTableModel modeloTecnicos = new DefaultTableModel(
            new Object[]{"Técnico", "Carga / capacidad"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JLabel estado = new JLabel(" Carga un archivo JSON para comenzar.");
    private Despachador despachador;

    public VentanaPrincipal(Path rutaInicial) {
        super("Gestión y priorización de solicitudes de soporte técnico");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 640);
        setLocationRelativeTo(null);

        configurarTabla();
        add(construirBarraHerramientas(), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
        estado.setBorder(BorderFactory.createEtchedBorder());
        add(estado, BorderLayout.SOUTH);

        if (rutaInicial != null) {
            cargar(rutaInicial);
        }
    }

    // --- construcción de la interfaz ----------------------------------------------------------
    private static JComboBox<String> crearCombo(Stream<String> valores) {
        return new JComboBox<>(Stream.concat(Stream.of(TODAS), valores).toArray(String[]::new));
    }

    private JPanel construirBarraHerramientas() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        barra.add(boton("Cargar JSON…", this::elegirArchivo));
        barra.add(boton("Nueva solicitud…", this::nuevaSolicitud));
        barra.add(boton("Procesar solicitudes", this::procesar));
        barra.add(boton("Resolver seleccionadas", this::resolver));
        barra.add(boton("Guardar resultados…", this::guardar));
        barra.add(Box.createHorizontalStrut(12));
        barra.add(new JLabel("Hilos:"));
        barra.add(hilos);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.setBorder(BorderFactory.createTitledBorder("Filtros"));
        filtros.add(new JLabel("Categoría:"));
        filtros.add(filtroCategoria);
        filtros.add(new JLabel("Prioridad:"));
        filtros.add(filtroPrioridad);
        filtros.add(new JLabel("Estado:"));
        filtros.add(filtroEstado);
        filtros.add(new JLabel("Buscar:"));
        filtros.add(busqueda);
        filtroCategoria.addActionListener(e -> aplicarFiltros());
        filtroPrioridad.addActionListener(e -> aplicarFiltros());
        filtroEstado.addActionListener(e -> aplicarFiltros());
        busqueda.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { aplicarFiltros(); }
            @Override public void removeUpdate(DocumentEvent e) { aplicarFiltros(); }
            @Override public void changedUpdate(DocumentEvent e) { aplicarFiltros(); }
        });

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(barra, BorderLayout.NORTH);
        arriba.add(filtros, BorderLayout.SOUTH);
        return arriba;
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton boton = new JButton(texto);
        boton.addActionListener(e -> accion.run());
        return boton;
    }

    private void configurarTabla() {
        tabla.setRowSorter(ordenador);
        ordenador.setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.ASCENDING)));
        tabla.setAutoCreateColumnsFromModel(true);
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(22);
        RenderizadorFilas renderizador = new RenderizadorFilas();
        tabla.setDefaultRenderer(Object.class, renderizador);
        tabla.setDefaultRenderer(Double.class, renderizador);
        int[] anchos = {70, 230, 85, 85, 90, 70, 115, 70};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
    }

    private JSplitPane construirCuerpo() {
        JPanel derecha = new JPanel();
        derecha.setLayout(new javax.swing.BoxLayout(derecha, javax.swing.BoxLayout.Y_AXIS));
        derecha.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        textoResumen.setEditable(false);
        textoResumen.setLineWrap(true);
        textoResumen.setWrapStyleWord(true);
        JTable tablaTecnicos = new JTable(modeloTecnicos);
        tablaTecnicos.setFillsViewportHeight(true);

        JScrollPane desplazableResumen = new JScrollPane(textoResumen);
        JScrollPane desplazableTecnicos = new JScrollPane(tablaTecnicos);
        desplazableResumen.setAlignmentX(Component.LEFT_ALIGNMENT);
        desplazableTecnicos.setAlignmentX(Component.LEFT_ALIGNMENT);

        derecha.add(titulo("Resumen estadístico"));
        derecha.add(desplazableResumen);
        derecha.add(Box.createVerticalStrut(8));
        derecha.add(titulo("Solicitudes por prioridad"));
        derecha.add(panelBarras);
        derecha.add(Box.createVerticalStrut(8));
        derecha.add(titulo("Carga de técnicos"));
        derecha.add(desplazableTecnicos);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tabla), derecha);
        division.setResizeWeight(0.68);
        division.setDividerLocation(780);
        return division;
    }

    private static JLabel titulo(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD));
        etiqueta.setAlignmentX(Component.LEFT_ALIGNMENT);
        return etiqueta;
    }

    // --- acciones -----------------------------------------------------------------------------
    private void elegirArchivo() {
        JFileChooser selector = new JFileChooser(directorioDatos());
        selector.setDialogTitle("Seleccionar archivo de solicitudes");
        selector.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            cargar(selector.getSelectedFile().toPath());
        }
    }

    private static File directorioDatos() {
        for (String candidato : new String[]{"../datos", "datos"}) {
            Path ruta = Path.of(candidato);
            if (Files.isDirectory(ruta)) {
                return ruta.toFile();
            }
        }
        return new File(".");
    }

    private void cargar(Path ruta) {
        try {
            Persistencia.DatosCargados datos = Persistencia.cargarDatos(ruta);
            Despachador nuevo = new Despachador(datos.tecnicos(), (Integer) hilos.getValue());
            for (Solicitud solicitud : datos.solicitudes()) {
                nuevo.registrar(solicitud);
            }
            despachador = nuevo;
            refrescar();
            estado.setText(" Cargadas " + datos.solicitudes().size() + " solicitudes y "
                    + datos.tecnicos().size() + " técnicos desde " + ruta.getFileName() + ".");
            if (!datos.errores().isEmpty()) {
                List<String> primeros = datos.errores().subList(0, Math.min(15, datos.errores().size()));
                JOptionPane.showMessageDialog(this,
                        "Se omitieron " + datos.errores().size() + " registros inválidos:\n\n"
                                + String.join("\n", primeros) + (datos.errores().size() > 15 ? "\n…" : ""),
                        "Registros omitidos", JOptionPane.WARNING_MESSAGE);
            }
        } catch (ErrorSoporte | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo cargar el archivo",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean hayDatos() {
        if (despachador == null) {
            JOptionPane.showMessageDialog(this, "Primero carga un archivo JSON.", "Sin datos",
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    /** Formulario para registrar una solicitud a mano; el id y la fecha se generan solos. */
    private void nuevaSolicitud() {
        if (!hayDatos()) {
            return;
        }
        JTextField titulo = new JTextField(26);
        JTextField descripcion = new JTextField(26);
        JComboBox<String> categoria = new JComboBox<>(
                Arrays.stream(Categoria.values()).map(Categoria::getEtiqueta).toArray(String[]::new));
        JComboBox<String> prioridad = new JComboBox<>(
                Arrays.stream(Prioridad.values()).map(Prioridad::getEtiqueta).toArray(String[]::new));
        categoria.setSelectedItem(Categoria.SOFTWARE.getEtiqueta());
        prioridad.setSelectedItem(Prioridad.MEDIA.getEtiqueta());

        JPanel formulario = new JPanel(new GridLayout(0, 2, 6, 6));
        formulario.add(new JLabel("Título:"));
        formulario.add(titulo);
        formulario.add(new JLabel("Descripción:"));
        formulario.add(descripcion);
        formulario.add(new JLabel("Categoría:"));
        formulario.add(categoria);
        formulario.add(new JLabel("Prioridad:"));
        formulario.add(prioridad);

        while (true) {  // se repite mientras los datos sean inválidos, hasta que el usuario cancele
            int opcion = JOptionPane.showConfirmDialog(this, formulario, "Nueva solicitud",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcion != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                JsonObject datos = new JsonObject();
                datos.addProperty("id", despachador.siguienteId());
                datos.addProperty("titulo", titulo.getText());
                datos.addProperty("descripcion", descripcion.getText());
                datos.addProperty("categoria", (String) categoria.getSelectedItem());
                datos.addProperty("prioridad", (String) prioridad.getSelectedItem());
                datos.addProperty("fecha_creacion", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
                Solicitud solicitud = Validador.validarSolicitud(datos);
                despachador.registrar(solicitud);
                refrescar();
                estado.setText(" Solicitud " + solicitud.getId()
                        + " registrada. Pulsa «Procesar solicitudes» para asignarla.");
                return;
            } catch (ErrorSoporte e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Solicitud inválida", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void procesar() {
        if (!hayDatos()) {
            return;
        }
        try {
            despachador.setNumHilos((Integer) hilos.getValue());
            int asignadas = despachador.procesarPendientes();
            refrescar();
            estado.setText(" Procesamiento terminado: " + asignadas + " solicitudes asignadas.");
        } catch (ErrorSoporte | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error al procesar", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resolver() {
        if (!hayDatos()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (int fila : tabla.getSelectedRows()) {
            ids.add(modelo.obtener(tabla.convertRowIndexToModel(fila)).getId());
        }
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selecciona una o más solicitudes asignadas.", "Sin selección",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int resueltas = 0;
        List<String> fallos = new ArrayList<>();
        for (String id : ids) {
            try {
                despachador.resolver(id);
                resueltas++;
            } catch (ErrorSoporte e) {
                fallos.add(e.getMessage());
            }
        }
        refrescar();
        estado.setText(" " + resueltas + " solicitudes resueltas; la cola en espera se reasignó.");
        if (!fallos.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", fallos.subList(0, Math.min(10, fallos.size()))),
                    "Algunas no se pudieron resolver", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void guardar() {
        if (!hayDatos()) {
            return;
        }
        JFileChooser selector = new JFileChooser(directorioDatos());
        selector.setSelectedFile(new File("resultados.json"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path ruta = selector.getSelectedFile().toPath();
        if (!ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            ruta = ruta.resolveSibling(ruta.getFileName() + ".json");
        }
        try {
            List<Solicitud> solicitudes = despachador.getSolicitudes();
            Persistencia.guardarResultados(ruta, solicitudes, despachador.getTecnicos(),
                    Estadisticas.generarResumen(solicitudes, despachador.getTecnicos()));
            estado.setText(" Resultados guardados en " + ruta.getFileName() + ".");
        } catch (ErrorSoporte e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo guardar", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- tabla, filtros y tablero -------------------------------------------------------------
    private void aplicarFiltros() {
        String categoria = (String) filtroCategoria.getSelectedItem();
        String prioridad = (String) filtroPrioridad.getSelectedItem();
        String estadoElegido = (String) filtroEstado.getSelectedItem();
        String texto = busqueda.getText().strip().toLowerCase(Locale.ROOT);
        ordenador.setRowFilter(new RowFilter<ModeloSolicitudes, Integer>() {
            @Override
            public boolean include(Entry<? extends ModeloSolicitudes, ? extends Integer> entrada) {
                Solicitud s = modelo.obtener(entrada.getIdentifier());
                return (TODAS.equals(categoria) || s.getCategoria().getEtiqueta().equals(categoria))
                        && (TODAS.equals(prioridad) || s.getPrioridad().getEtiqueta().equals(prioridad))
                        && (TODAS.equals(estadoElegido) || s.getEstado().getEtiqueta().equals(estadoElegido))
                        && (texto.isEmpty() || s.getTitulo().toLowerCase(Locale.ROOT).contains(texto)
                        || s.getId().toLowerCase(Locale.ROOT).contains(texto));
            }
        });
    }

    private void refrescar() {
        List<Solicitud> solicitudes = despachador.getSolicitudes();
        modelo.setSolicitudes(solicitudes);
        Estadisticas.Resumen resumen = Estadisticas.generarResumen(solicitudes, despachador.getTecnicos());
        textoResumen.setText(Estadisticas.formatear(resumen));
        modeloTecnicos.setRowCount(0);
        for (Estadisticas.CargaTecnico t : resumen.cargaTecnicos()) {
            modeloTecnicos.addRow(new Object[]{t.nombre(), t.carga() + " / " + t.capacidad()});
        }
        panelBarras.setDatos(resumen.porPrioridad());
    }

    // --- componentes internos -----------------------------------------------------------------
    private static class ModeloSolicitudes extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Id", "Título", "Categoría", "Prioridad", "Estado",
                "Técnico", "Tiempo est. (h)", "Plazo"};
        private List<Solicitud> filas = new ArrayList<>();

        void setSolicitudes(List<Solicitud> solicitudes) {
            filas = new ArrayList<>(solicitudes);
            fireTableDataChanged();
        }

        Solicitud obtener(int fila) {
            return filas.get(fila);
        }

        @Override public int getRowCount() { return filas.size(); }
        @Override public int getColumnCount() { return COLUMNAS.length; }
        @Override public String getColumnName(int columna) { return COLUMNAS[columna]; }

        @Override
        public Class<?> getColumnClass(int columna) {
            return switch (columna) {
                case 3 -> Prioridad.class;   // se ordena por el orden de declaración (crítica primero)
                case 6 -> Double.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            Solicitud s = filas.get(fila);
            return switch (columna) {
                case 0 -> s.getId();
                case 1 -> s.getTitulo();
                case 2 -> s.getCategoria().getEtiqueta();
                case 3 -> s.getPrioridad();
                case 4 -> s.getEstado().getEtiqueta();
                case 5 -> s.getTecnicoId() == null ? "—" : s.getTecnicoId();
                case 6 -> s.getTiempoEstimadoHoras();
                default -> s.cumplePlazo().map(cumple -> cumple ? "Cumple" : "Excede").orElse("—");
            };
        }
    }

    private class RenderizadorFilas extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            String texto = valor == null ? "—"
                    : valor instanceof Double horas ? String.format(Locale.ROOT, "%.2f", horas)
                    : valor.toString();
            super.getTableCellRendererComponent(t, texto, seleccionada, foco, fila, columna);
            if (!seleccionada) {
                Solicitud s = modelo.obtener(t.convertRowIndexToModel(fila));
                if (s.cumplePlazo().filter(cumple -> !cumple).isPresent()) {
                    setBackground(COLOR_INCUMPLE);
                } else if (s.getEstado() == Estado.PENDIENTE) {
                    setBackground(COLOR_PENDIENTE);
                } else {
                    setBackground(Color.WHITE);
                }
                setForeground(s.getEstado() == Estado.RESUELTA ? Color.GRAY : Color.BLACK);
            }
            return this;
        }
    }

    private static class PanelBarras extends JPanel {
        private static final Map<String, Color> COLORES = Map.of(
                "critica", new Color(0xD9534F), "alta", new Color(0xF0AD4E),
                "media", new Color(0x5BC0DE), "baja", new Color(0x5CB85C));
        private Map<String, Integer> datos = new LinkedHashMap<>();

        PanelBarras() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC)));
            setPreferredSize(new Dimension(300, 150));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        void setDatos(Map<String, Integer> nuevos) {
            datos = new LinkedHashMap<>(nuevos);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (datos.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ancho = getWidth(), alto = getHeight(), base = alto - 22;
            int maximo = Math.max(1, datos.values().stream().mapToInt(Integer::intValue).max().orElse(1));
            double anchoColumna = (ancho - 20.0) / datos.size();
            FontMetrics metrica = g2.getFontMetrics();
            int posicion = 0;
            for (Map.Entry<String, Integer> entrada : datos.entrySet()) {
                int x0 = (int) (10 + posicion * anchoColumna + 8);
                int x1 = (int) (10 + (posicion + 1) * anchoColumna - 8);
                int y0 = (int) (base - (base - 24.0) * entrada.getValue() / maximo);
                g2.setColor(COLORES.getOrDefault(entrada.getKey(), Color.GRAY));
                g2.fillRect(x0, y0, x1 - x0, base - y0);
                g2.setColor(Color.BLACK);
                String valor = String.valueOf(entrada.getValue());
                g2.drawString(valor, (x0 + x1 - metrica.stringWidth(valor)) / 2, y0 - 4);
                g2.drawString(entrada.getKey(), (x0 + x1 - metrica.stringWidth(entrada.getKey())) / 2, alto - 6);
                posicion++;
            }
            g2.dispose();
        }
    }
}
