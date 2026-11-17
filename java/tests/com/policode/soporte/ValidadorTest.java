package com.policode.soporte;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ValidadorTest {
    private static JsonObject datosValidos() {
        return JsonParser.parseString("""
                {"id":"S1","titulo":"Sin internet","descripcion":"Oficina 3","categoria":"redes",
                 "prioridad":"ALTA","fecha_creacion":"2026-11-02T08:30:00"}""").getAsJsonObject();
    }

    @Test
    public void solicitudValidaSeConvierteAObjeto() {
        Solicitud solicitud = Validador.validarSolicitud(datosValidos());
        assertEquals(Categoria.REDES, solicitud.getCategoria());
        assertEquals(Prioridad.ALTA, solicitud.getPrioridad());  // no distingue mayúsculas
    }

    @Test
    public void solicitudConDatoInvalidoLanzaErrorValidacion() {
        String[][] casos = {{"titulo", "   "}, {"prioridad", "urgentisima"}, {"categoria", "cafeteria"},
                {"fecha_creacion", "ayer"}};
        for (String[] caso : casos) {
            JsonObject datos = datosValidos();
            datos.addProperty(caso[0], caso[1]);
            assertThrows("campo " + caso[0], ErrorValidacion.class, () -> Validador.validarSolicitud(datos));
        }
        JsonObject sinId = datosValidos();
        sinId.remove("id");
        assertThrows(ErrorValidacion.class, () -> Validador.validarSolicitud(sinId));
    }

    @Test
    public void solicitudQueNoEsObjetoLanzaError() {
        assertThrows(ErrorValidacion.class, () -> Validador.validarSolicitud(JsonParser.parseString("\"texto\"")));
    }

    @Test
    public void tecnicoValidoYCapacidadInvalida() {
        Tecnico tecnico = Validador.validarTecnico(JsonParser.parseString(
                "{\"id\":\"T1\",\"nombre\":\"Ana\",\"especialidades\":[\"redes\"],\"capacidad\":3}"));
        assertEquals(3, tecnico.getCapacidad());
        assertEquals(java.util.List.of(Categoria.REDES), tecnico.getEspecialidades());
        for (String capacidad : new String[]{"0", "11", "true", "\"2\"", "2.5"}) {
            assertThrows(capacidad, ErrorValidacion.class, () -> Validador.validarTecnico(JsonParser.parseString(
                    "{\"id\":\"T1\",\"nombre\":\"Ana\",\"capacidad\":" + capacidad + "}")));
        }
    }
}
