package com.policode.soporte;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

/** Punto de entrada: mvn exec:java  (o java -cp ... com.policode.soporte.Principal [ruta.json]) */
public final class Principal {
    private Principal() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // si no hay apariencia del sistema se usa la predeterminada de Swing
        }
        Path rutaInicial = args.length > 0 ? Path.of(args[0]) : null;
        SwingUtilities.invokeLater(() -> new VentanaPrincipal(rutaInicial).setVisible(true));
    }
}
