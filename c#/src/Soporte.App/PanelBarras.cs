using System.Drawing.Drawing2D;

namespace Soporte.App;

/// <summary>Gráfico de barras sencillo: solicitudes por prioridad.</summary>
internal sealed class PanelBarras : Panel
{
    private static readonly Dictionary<string, Color> Colores = new()
    {
        ["critica"] = Color.FromArgb(0xD9, 0x53, 0x4F),
        ["alta"] = Color.FromArgb(0xF0, 0xAD, 0x4E),
        ["media"] = Color.FromArgb(0x5B, 0xC0, 0xDE),
        ["baja"] = Color.FromArgb(0x5C, 0xB8, 0x5C),
    };

    private IReadOnlyDictionary<string, int> _datos = new Dictionary<string, int>();

    public PanelBarras()
    {
        DoubleBuffered = true;
        ResizeRedraw = true;
        BackColor = Color.White;
        BorderStyle = BorderStyle.FixedSingle;
        Dock = DockStyle.Fill;
    }

    public IReadOnlyDictionary<string, int> Datos
    {
        set
        {
            _datos = value;
            Invalidate();
        }
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        if (_datos.Count == 0)
        {
            return;
        }

        Graphics g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        int maximo = Math.Max(1, _datos.Values.Max());
        int baseY = Height - 24;
        float anchoColumna = (Width - 20f) / _datos.Count;
        int posicion = 0;

        foreach (KeyValuePair<string, int> entrada in _datos)
        {
            float x0 = 10 + posicion * anchoColumna + 8;
            float x1 = 10 + (posicion + 1) * anchoColumna - 8;
            float y0 = baseY - (baseY - 26f) * entrada.Value / maximo;

            using (var pincel = new SolidBrush(Colores.GetValueOrDefault(entrada.Key, Color.Gray)))
            {
                g.FillRectangle(pincel, x0, y0, x1 - x0, baseY - y0);
            }
            DibujarCentrado(g, entrada.Value.ToString(), (x0 + x1) / 2, y0 - 16);
            DibujarCentrado(g, entrada.Key, (x0 + x1) / 2, Height - 20);
            posicion++;
        }
    }

    private void DibujarCentrado(Graphics g, string texto, float centroX, float y)
    {
        SizeF tamano = g.MeasureString(texto, Font);
        g.DrawString(texto, Font, Brushes.Black, centroX - tamano.Width / 2, y);
    }
}
