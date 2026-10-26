package com.autotaller.tests;

import com.autotaller.client.AutoTallerClient;
import com.autotaller.client.AutoTallerClient.RespuestaApi;
import com.autotaller.db.OrdenesRepository;
import com.autotaller.db.OrdenesRepository.ClienteVehiculoIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba del cambio agregado al Workflow 1: antes de insertar, busca si el
 * cliente (por telefono) o el vehiculo (por placa) ya existen y reutiliza su
 * id en vez de crear uno nuevo. Esta prueba manda dos solicitudes distintas
 * con el MISMO telefono/placa y verifica, directamente en PostgreSQL (solo
 * lectura), que ambas ordenes quedaron asociadas al mismo cliente_id y
 * vehiculo_id, aunque sean dos ordenes distintas.
 *
 * Requiere que autotaller_n8n y autotaller_db esten corriendo y que el
 * Workflow 1 este Activado en n8n con la version que incluye la verificacion
 * de duplicados.
 */
@DisplayName("Workflow 1 - Deduplicacion de cliente y vehiculo")
class DeduplicacionClienteVehiculoTest {

    private static AutoTallerClient cliente;
    private static OrdenesRepository repositorio;

    @BeforeAll
    static void setUp() {
        cliente = new AutoTallerClient();
        repositorio = new OrdenesRepository();
    }

    @Test
    @DisplayName("Dos solicitudes con el mismo telefono/placa reutilizan cliente_id y vehiculo_id")
    void solicitudesRepetidasReutilizanClienteYVehiculo() throws Exception {
        // Telefono y placa unicos para esta corrida, para no mezclarse con
        // datos de pruebas anteriores, pero IGUALES entre las dos solicitudes.
        String sufijo = String.valueOf(System.currentTimeMillis() % 100000);
        String telefono = "5" + sufijo;
        String placa = "DUP" + sufijo.substring(0, Math.min(3, sufijo.length()));

        RespuestaApi primera = cliente.enviarSolicitudServicio(
                "Cliente Deduplicacion", telefono, placa, "Toyota", "Corolla",
                "Cambio de aceite");
        assertEquals(200, primera.codigoEstado, "La primera solicitud deberia crear la orden correctamente");
        int ordenId1 = primera.cuerpo.getInt("orden_id");

        RespuestaApi segunda = cliente.enviarSolicitudServicio(
                "Cliente Deduplicacion", telefono, placa, "Toyota", "Corolla",
                "Ruido en los frenos");
        assertEquals(200, segunda.codigoEstado, "La segunda solicitud deberia crear la orden correctamente");
        int ordenId2 = segunda.cuerpo.getInt("orden_id");

        assertNotEquals(ordenId1, ordenId2, "Cada solicitud debe generar una orden nueva y distinta");

        ClienteVehiculoIds idsOrden1 = repositorio.obtenerClienteYVehiculoPorOrden(ordenId1);
        ClienteVehiculoIds idsOrden2 = repositorio.obtenerClienteYVehiculoPorOrden(ordenId2);

        assertEquals(idsOrden1.clienteId(), idsOrden2.clienteId(),
                "El cliente_id deberia ser el mismo en ambas ordenes (se reutilizo, no se duplico)");
        assertEquals(idsOrden1.vehiculoId(), idsOrden2.vehiculoId(),
                "El vehiculo_id deberia ser el mismo en ambas ordenes (se reutilizo, no se duplico)");
        assertTrue(idsOrden1.clienteId() > 0 && idsOrden1.vehiculoId() > 0,
                "Los ids reutilizados deben ser validos (mayores a 0)");
    }
}
