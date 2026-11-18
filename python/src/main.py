"""Punto de entrada: python src/main.py [ruta.json]"""
import sys

from interfaz import VentanaPrincipal

if __name__ == "__main__":
    ruta_inicial = sys.argv[1] if len(sys.argv) > 1 else None
    VentanaPrincipal(ruta_inicial).mainloop()
