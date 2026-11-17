using System.Globalization;
using System.Text.Json;

namespace Soporte.App;

/// <summary>Fila que se muestra en la tabla de solicitudes.</summary>
internal sealed record FilaSolicitud(string Id, string Titulo, string Categoria, string Prioridad,
                                     string Estado, string Tecnico, string Tiempo, string Plazo);

internal sealed record FilaTecnico(string Tecnico, string Carga);

/// <summary>Interfaz gráfica (Windows Forms): tabla con filtros y orden, resumen y tablero por prioridad.</summary>
public sealed class VentanaPrincipal : Form
{
    private const string Todas = "Todas";
    private static readonly Color ColorPendiente = Color.FromArgb(0xFF, 0xF6, 0xD6);
    private static readonly Color ColorIncumple = Color.FromArgb(0xF9, 0xD6, 0xD5);

    // (nombre de columna, encabezado, propiedad de FilaSolicitud, ancho)
    private static readonly (string Nombre, string Titulo, string Propiedad, int Ancho)[] Columnas =
    {
        ("id", "Id", "Id", 70), ("titulo", "Título", "Titulo", 230), ("categoria", "Categoría", "Categoria", 85),
        ("prioridad", "Prioridad", "Prioridad", 80), ("estado", "Estado", "Estado", 80),
        ("tecnico", "Técnico", "Tecnico", 70), ("tiempo", "Tiempo est. (h)", "Tiempo", 100),
        ("plazo", "Plazo", "Plazo", 70),
    };

    // Cómo se ordena la tabla al pulsar el encabezado de cada columna.
    private static readonly Dictionary<string, Func<Solicitud, IComparable>> ClavesOrden = new()
    {
        ["id"] = s => s.Id,
        ["titulo"] = s => s.Titulo.ToLowerInvariant(),
        ["categoria"] = s => s.Categoria.Etiqueta(),
        ["prioridad"] = s => (int)s.Prioridad,
        ["estado"] = s => s.Estado.Etiqueta(),
        ["tecnico"] = s => s.TecnicoId ?? "~",
        ["tiempo"] = s => s.TiempoEstimadoHoras ?? double.PositiveInfinity,
        ["plazo"] = s => s.CumplePlazo?.ToString() ?? "~",
    };

    private readonly DataGridView _tabla = new();
    private readonly DataGridView _tablaTecnicos = new();
    private readonly ComboBox _filtroCategoria;
    private readonly ComboBox _filtroPrioridad;
    private readonly ComboBox _filtroEstado;
    private readonly TextBox _busqueda = new() { Width = 160 };
    private readonly NumericUpDown _hilos = new() { Minimum = 1, Maximum = 16, Value = 4, Width = 50 };
    private readonly TextBox _textoResumen = new() { Multiline = true, ReadOnly = true, Dock = DockStyle.Fill, BackColor = Color.White };
    private readonly PanelBarras _barras = new();
    private readonly Label _estado = new()
    {
        Dock = DockStyle.Fill, AutoSize = true, BorderStyle = BorderStyle.Fixed3D,
        Text = "Carga un archivo JSON para comenzar.", Padding = new Padding(3),
    };

    private Despachador? _despachador;
    private List<Solicitud> _visibles = new();
    private string _columnaOrden = "prioridad";
    private bool _descendente;

    public VentanaPrincipal(string? rutaInicial)
    {
        Text = "Gestión y priorización de solicitudes de soporte técnico";
        Size = new Size(1200, 700);
        MinimumSize = new Size(1000, 560);
        StartPosition = FormStartPosition.CenterScreen;

        _filtroCategoria = CrearCombo(Enum.GetValues<Categoria>().Select(c => c.Etiqueta()));
        _filtroPrioridad = CrearCombo(Enum.GetValues<Prioridad>().Select(p => p.Etiqueta()));
        _filtroEstado = CrearCombo(Enum.GetValues<Estado>().Select(e => e.Etiqueta()));
        _busqueda.TextChanged += (_, _) => Refrescar();

        ConfigurarTablaSolicitudes();
        ConfigurarTablaTecnicos();

        var raiz = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 4 };
        raiz.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        raiz.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        raiz.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        raiz.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        raiz.Controls.Add(ConstruirBarraHerramientas(), 0, 0);
        raiz.Controls.Add(ConstruirFiltros(), 0, 1);
        raiz.Controls.Add(ConstruirCuerpo(), 0, 2);
        raiz.Controls.Add(_estado, 0, 3);
        Controls.Add(raiz);

        // Se carga al mostrar la ventana para que los avisos aparezcan encima de ella.
        Shown += (_, _) =>
        {
            if (rutaInicial is not null)
            {
                Cargar(rutaInicial);
            }
        };
    }

    // --- construcción de la interfaz ----------------------------------------------------------
    private ComboBox CrearCombo(IEnumerable<string> valores)
    {
        var combo = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 100 };
        combo.Items.Add(Todas);
        combo.Items.AddRange(valores.Cast<object>().ToArray());
        combo.SelectedIndex = 0;
        combo.SelectedIndexChanged += (_, _) => Refrescar();
        return combo;
    }

    private static Button Boton(string texto, Action accion)
    {
        var boton = new Button { Text = texto, AutoSize = true, Padding = new Padding(6, 2, 6, 2) };
        boton.Click += (_, _) => accion();
        return boton;
    }

    private static Label Etiqueta(string texto, bool negrita = false) => new()
    {
        Text = texto,
        AutoSize = true,
        Anchor = AnchorStyles.Left,
        Font = negrita ? new Font(SystemFonts.DefaultFont, FontStyle.Bold) : SystemFonts.DefaultFont,
        Margin = new Padding(6, 6, 3, 3),
    };

    private FlowLayoutPanel ConstruirBarraHerramientas()
    {
        var barra = new FlowLayoutPanel { Dock = DockStyle.Fill, AutoSize = true, Padding = new Padding(4) };
        barra.Controls.Add(Boton("Cargar JSON…", ElegirArchivo));
        barra.Controls.Add(Boton("Nueva solicitud…", NuevaSolicitud));
        barra.Controls.Add(Boton("Procesar solicitudes", Procesar));
        barra.Controls.Add(Boton("Resolver seleccionadas", Resolver));
        barra.Controls.Add(Boton("Guardar resultados…", Guardar));
        barra.Controls.Add(Etiqueta("Hilos:"));
        barra.Controls.Add(_hilos);
        return barra;
    }

    private GroupBox ConstruirFiltros()
    {
        var flujo = new FlowLayoutPanel { Dock = DockStyle.Top, AutoSize = true };
        flujo.Controls.AddRange(new Control[]
        {
            Etiqueta("Categoría:"), _filtroCategoria, Etiqueta("Prioridad:"), _filtroPrioridad,
            Etiqueta("Estado:"), _filtroEstado, Etiqueta("Buscar:"), _busqueda,
        });
        var grupo = new GroupBox { Text = "Filtros", Dock = DockStyle.Fill, AutoSize = true };
        grupo.Controls.Add(flujo);
        return grupo;
    }

    private TableLayoutPanel ConstruirCuerpo()
    {
        var derecha = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 6 };
        derecha.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        derecha.RowStyles.Add(new RowStyle(SizeType.Absolute, 200));
        derecha.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        derecha.RowStyles.Add(new RowStyle(SizeType.Absolute, 170));
        derecha.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        derecha.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        derecha.Controls.Add(Etiqueta("Resumen estadístico", negrita: true), 0, 0);
        derecha.Controls.Add(_textoResumen, 0, 1);
        derecha.Controls.Add(Etiqueta("Solicitudes por prioridad", negrita: true), 0, 2);
        derecha.Controls.Add(_barras, 0, 3);
        derecha.Controls.Add(Etiqueta("Carga de técnicos", negrita: true), 0, 4);
        derecha.Controls.Add(_tablaTecnicos, 0, 5);

        var cuerpo = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 1 };
        cuerpo.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 64));
        cuerpo.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 36));
        cuerpo.Controls.Add(_tabla, 0, 0);
        cuerpo.Controls.Add(derecha, 1, 0);
        return cuerpo;
    }

    private void ConfigurarTablaSolicitudes()
    {
        ConfigurarRejilla(_tabla);
        _tabla.AutoGenerateColumns = false;
        _tabla.MultiSelect = true;
        foreach (var (nombre, titulo, propiedad, ancho) in Columnas)
        {
            _tabla.Columns.Add(new DataGridViewTextBoxColumn
            {
                Name = nombre, HeaderText = titulo, DataPropertyName = propiedad, Width = ancho,
                SortMode = DataGridViewColumnSortMode.Programmatic,
            });
        }
        _tabla.Columns["titulo"]!.AutoSizeMode = DataGridViewAutoSizeColumnMode.Fill;
        _tabla.ColumnHeaderMouseClick += (_, e) => OrdenarPor(_tabla.Columns[e.ColumnIndex].Name);
    }

    private void ConfigurarTablaTecnicos()
    {
        ConfigurarRejilla(_tablaTecnicos);
        _tablaTecnicos.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
    }

    private static void ConfigurarRejilla(DataGridView rejilla)
    {
        rejilla.Dock = DockStyle.Fill;
        rejilla.ReadOnly = true;
        rejilla.AllowUserToAddRows = false;
        rejilla.AllowUserToDeleteRows = false;
        rejilla.RowHeadersVisible = false;
        rejilla.SelectionMode = DataGridViewSelectionMode.FullRowSelect;
        rejilla.BackgroundColor = Color.White;
    }

    // --- acciones -----------------------------------------------------------------------------
    private void ElegirArchivo()
    {
        using var dialogo = new OpenFileDialog
        {
            Title = "Seleccionar archivo de solicitudes",
            Filter = "JSON (*.json)|*.json|Todos los archivos (*.*)|*.*",
            InitialDirectory = DirectorioDatos(),
        };
        if (dialogo.ShowDialog(this) == DialogResult.OK)
        {
            Cargar(dialogo.FileName);
        }
    }

    /// <summary>Busca la carpeta "datos" del repositorio subiendo desde la carpeta del ejecutable.</summary>
    private static string DirectorioDatos()
    {
        for (var carpeta = new DirectoryInfo(AppContext.BaseDirectory); carpeta is not null; carpeta = carpeta.Parent)
        {
            string candidata = Path.Combine(carpeta.FullName, "datos");
            if (Directory.Exists(candidata))
            {
                return candidata;
            }
        }
        return Environment.CurrentDirectory;
    }

    private void Cargar(string ruta)
    {
        try
        {
            DatosCargados datos = Persistencia.CargarDatos(ruta);
            var nuevo = new Despachador(datos.Tecnicos, (int)_hilos.Value);
            foreach (Solicitud solicitud in datos.Solicitudes)
            {
                nuevo.Registrar(solicitud);
            }
            _despachador = nuevo;
            Refrescar();
            _estado.Text = $"Cargadas {datos.Solicitudes.Count} solicitudes y {datos.Tecnicos.Count} técnicos " +
                           $"desde {Path.GetFileName(ruta)}.";
            if (datos.Errores.Count > 0)
            {
                string detalle = string.Join("\n", datos.Errores.Take(15)) + (datos.Errores.Count > 15 ? "\n…" : "");
                MessageBox.Show(this, $"Se omitieron {datos.Errores.Count} registros inválidos:\n\n{detalle}",
                    "Registros omitidos", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }
        catch (Exception e) when (e is ErrorSoporte or ArgumentOutOfRangeException)
        {
            MessageBox.Show(this, e.Message, "No se pudo cargar el archivo", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private bool HayDatos()
    {
        if (_despachador is null)
        {
            MessageBox.Show(this, "Primero carga un archivo JSON.", "Sin datos",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return false;
        }
        return true;
    }

    /// <summary>Formulario para registrar una solicitud a mano; el id y la fecha se generan solos.</summary>
    private void NuevaSolicitud()
    {
        if (!HayDatos())
        {
            return;
        }

        using var dialogo = new Form
        {
            Text = "Nueva solicitud",
            FormBorderStyle = FormBorderStyle.FixedDialog,
            StartPosition = FormStartPosition.CenterParent,
            MaximizeBox = false,
            MinimizeBox = false,
            ShowInTaskbar = false,
            ClientSize = new Size(460, 230),
        };
        var titulo = new TextBox { Width = 300 };
        var descripcion = new TextBox { Width = 300 };
        ComboBox categoria = CrearComboSimple(Enum.GetValues<Categoria>().Select(c => c.Etiqueta()), "software");
        ComboBox prioridad = CrearComboSimple(Enum.GetValues<Prioridad>().Select(p => p.Etiqueta()), "media");

        var rejilla = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, Padding = new Padding(12) };
        rejilla.Controls.Add(Etiqueta("Título:"), 0, 0);
        rejilla.Controls.Add(titulo, 1, 0);
        rejilla.Controls.Add(Etiqueta("Descripción:"), 0, 1);
        rejilla.Controls.Add(descripcion, 1, 1);
        rejilla.Controls.Add(Etiqueta("Categoría:"), 0, 2);
        rejilla.Controls.Add(categoria, 1, 2);
        rejilla.Controls.Add(Etiqueta("Prioridad:"), 0, 3);
        rejilla.Controls.Add(prioridad, 1, 3);

        var registrar = new Button { Text = "Registrar", AutoSize = true };
        var cancelar = new Button { Text = "Cancelar", AutoSize = true, DialogResult = DialogResult.Cancel };
        var botones = new FlowLayoutPanel { FlowDirection = FlowDirection.RightToLeft, AutoSize = true };
        botones.Controls.Add(cancelar);
        botones.Controls.Add(registrar);
        rejilla.Controls.Add(botones, 1, 4);
        dialogo.Controls.Add(rejilla);
        dialogo.CancelButton = cancelar;

        Solicitud? registrada = null;
        registrar.Click += (_, _) =>
        {
            try
            {
                var datos = new Dictionary<string, object?>
                {
                    ["id"] = _despachador!.SiguienteId(),
                    ["titulo"] = titulo.Text,
                    ["descripcion"] = descripcion.Text,
                    ["categoria"] = categoria.SelectedItem?.ToString(),
                    ["prioridad"] = prioridad.SelectedItem?.ToString(),
                    ["fecha_creacion"] = DateTime.Now.ToString("yyyy-MM-ddTHH:mm:ss", CultureInfo.InvariantCulture),
                };
                registrada = Validador.ValidarSolicitud(JsonSerializer.SerializeToElement(datos));
                _despachador.Registrar(registrada);
                dialogo.DialogResult = DialogResult.OK;  // cierra el formulario
            }
            catch (ErrorSoporte e)
            {
                MessageBox.Show(dialogo, e.Message, "Solicitud inválida", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        };
        dialogo.AcceptButton = registrar;

        if (dialogo.ShowDialog(this) == DialogResult.OK && registrada is not null)
        {
            Refrescar();
            _estado.Text = $"Solicitud {registrada.Id} registrada. Pulsa «Procesar solicitudes» para asignarla.";
        }
    }

    private static ComboBox CrearComboSimple(IEnumerable<string> valores, string seleccionado)
    {
        var combo = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 140 };
        combo.Items.AddRange(valores.Cast<object>().ToArray());
        combo.SelectedItem = seleccionado;
        return combo;
    }

    private void Procesar()
    {
        if (!HayDatos())
        {
            return;
        }
        try
        {
            _despachador!.NumHilos = (int)_hilos.Value;
            int asignadas = _despachador.ProcesarPendientes();
            Refrescar();
            _estado.Text = $"Procesamiento terminado: {asignadas} solicitudes asignadas.";
        }
        catch (Exception e) when (e is ErrorSoporte or ArgumentOutOfRangeException)
        {
            MessageBox.Show(this, e.Message, "Error al procesar", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private void Resolver()
    {
        if (!HayDatos())
        {
            return;
        }
        List<string> ids = _tabla.SelectedRows.Cast<DataGridViewRow>().Select(fila => _visibles[fila.Index].Id).ToList();
        if (ids.Count == 0)
        {
            MessageBox.Show(this, "Selecciona una o más solicitudes asignadas.", "Sin selección",
                MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        int resueltas = 0;
        var fallos = new List<string>();
        foreach (string id in ids)
        {
            try
            {
                _despachador!.Resolver(id);
                resueltas++;
            }
            catch (ErrorSoporte e)
            {
                fallos.Add(e.Message);
            }
        }
        Refrescar();
        _estado.Text = $"{resueltas} solicitudes resueltas; la cola en espera se reasignó.";
        if (fallos.Count > 0)
        {
            MessageBox.Show(this, string.Join("\n", fallos.Take(10)), "Algunas no se pudieron resolver",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private void Guardar()
    {
        if (!HayDatos())
        {
            return;
        }
        using var dialogo = new SaveFileDialog
        {
            Title = "Guardar resultados",
            FileName = "resultados.json",
            Filter = "JSON (*.json)|*.json",
            DefaultExt = "json",
            AddExtension = true,
            InitialDirectory = DirectorioDatos(),
        };
        if (dialogo.ShowDialog(this) != DialogResult.OK)
        {
            return;
        }
        try
        {
            IReadOnlyList<Solicitud> solicitudes = _despachador!.Solicitudes;
            Persistencia.GuardarResultados(dialogo.FileName, solicitudes, _despachador.Tecnicos,
                Estadisticas.GenerarResumen(solicitudes, _despachador.Tecnicos));
            _estado.Text = $"Resultados guardados en {Path.GetFileName(dialogo.FileName)}.";
        }
        catch (ErrorSoporte e)
        {
            MessageBox.Show(this, e.Message, "No se pudo guardar", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    // --- tabla, filtros y tablero -------------------------------------------------------------
    private void OrdenarPor(string columna)
    {
        _descendente = columna == _columnaOrden && !_descendente;
        _columnaOrden = columna;
        Refrescar();
    }

    private List<Solicitud> SolicitudesVisibles(IEnumerable<Solicitud> todas)
    {
        string categoria = (string)_filtroCategoria.SelectedItem!;
        string prioridad = (string)_filtroPrioridad.SelectedItem!;
        string estado = (string)_filtroEstado.SelectedItem!;
        string texto = _busqueda.Text.Trim().ToLowerInvariant();

        IEnumerable<Solicitud> filtradas = todas.Where(s =>
            (categoria == Todas || s.Categoria.Etiqueta() == categoria)
            && (prioridad == Todas || s.Prioridad.Etiqueta() == prioridad)
            && (estado == Todas || s.Estado.Etiqueta() == estado)
            && (texto.Length == 0 || s.Titulo.ToLowerInvariant().Contains(texto) || s.Id.ToLowerInvariant().Contains(texto)));

        Func<Solicitud, IComparable> clave = ClavesOrden[_columnaOrden];
        IOrderedEnumerable<Solicitud> ordenadas = _descendente ? filtradas.OrderByDescending(clave) : filtradas.OrderBy(clave);
        return ordenadas.ThenBy(s => s, Solicitud.ComparadorUrgencia).ToList();
    }

    private static FilaSolicitud AFila(Solicitud s) => new(
        s.Id, s.Titulo, s.Categoria.Etiqueta(), s.Prioridad.Etiqueta(), s.Estado.Etiqueta(),
        s.TecnicoId ?? "—",
        s.TiempoEstimadoHoras?.ToString("0.00", CultureInfo.InvariantCulture) ?? "—",
        s.CumplePlazo switch { true => "Cumple", false => "Excede", null => "—" });

    private void Refrescar()
    {
        if (_despachador is null)
        {
            return;
        }
        IReadOnlyList<Solicitud> todas = _despachador.Solicitudes;
        _visibles = SolicitudesVisibles(todas);
        _tabla.DataSource = _visibles.Select(AFila).ToList();
        PintarFilas();

        Resumen resumen = Estadisticas.GenerarResumen(todas, _despachador.Tecnicos);
        _textoResumen.Text = Estadisticas.Formatear(resumen).Replace("\n", Environment.NewLine);
        _tablaTecnicos.DataSource = resumen.CargaTecnicos
            .Select(t => new FilaTecnico(t.Nombre, $"{t.Carga} / {t.Capacidad}")).ToList();
        if (_tablaTecnicos.Columns["Carga"] is DataGridViewColumn columnaCarga)
        {
            columnaCarga.HeaderText = "Carga / capacidad";
        }
        _barras.Datos = resumen.PorPrioridad;
    }

    private void PintarFilas()
    {
        foreach (DataGridViewColumn columna in _tabla.Columns)
        {
            columna.HeaderCell.SortGlyphDirection = SortOrder.None;
        }
        _tabla.Columns[_columnaOrden]!.HeaderCell.SortGlyphDirection =
            _descendente ? SortOrder.Descending : SortOrder.Ascending;

        for (int i = 0; i < _tabla.Rows.Count && i < _visibles.Count; i++)
        {
            Solicitud s = _visibles[i];
            DataGridViewCellStyle estilo = _tabla.Rows[i].DefaultCellStyle;
            if (s.CumplePlazo == false)
            {
                estilo.BackColor = ColorIncumple;
            }
            else if (s.Estado == Estado.Pendiente)
            {
                estilo.BackColor = ColorPendiente;
            }
            if (s.Estado == Estado.Resuelta)
            {
                estilo.ForeColor = Color.Gray;
            }
        }
        _tabla.ClearSelection();
    }
}
