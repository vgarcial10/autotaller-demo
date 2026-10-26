package com.autotaller.tests;

import com.autotaller.client.AutoTallerClient;
import com.autotaller.client.AutoTallerClient.RespuestaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Los Workflows 2 (procesamiento) y 3 (notificacion) no tienen webhook:
 * se disparan solos con un Schedule Trigger cada 1 minuto y leen ordenes
 * pendientes de la base de datos. No se pueden invocar directamente por
 * HTTP, asi que esta prueba crea una orden real con el Workflow 1 y
 * espera, consultando el Workflow 4 repetidamente, a que el ciclo
 * completo (recibido -> asignado -> notificado) ocurra solo.
 *
 * Es una prueba de integracion lenta por diseno (puede tardar varios
 * minutos, porque depende del Schedule Trigger de n8n). No es un
 * problema del cliente Java ni de esta prueba.
 */
@DisplayName("Ciclo completo de una orden (Workflows 1 a 4)")
class CicloCompletoOrdenTest {

    private static final long INTERVALO_SONDEO_MS = 5000;
    private static final long TIEMPO_MAXIMO_POR_ESTADO_MS = 130000; // ~2.2 min por estado

    private AutoTallerClient cliente;

    @BeforeEach
    void setUp() {
        cliente = new AutoTallerClient();
    }

    @Test
    @DisplayName("Una orden nueva avanza sola de recibido a asignado y luego a notificado")
    void ordenAvanzaDeRecibidoAAsignadoANotificado() throws Exception {
        RespuestaApi solicitud = cliente.enviarSolicitudServicio(
                "Cliente Ciclo Completo", "5555-1111", "CICLO1", "Honda", "Civic",
                "El motor no enciende");

        assertEquals(200, solicitud.codigoEstado);
        String codigoAcceso = solicitud.cuerpo.getString("codigo_acceso");

        String estadoTrasProcesamiento = esperarEstado(codigoAcceso, "asignado");
        assertEquals("asignado", estadoTrasProcesamiento,
                "El Workflow 2 (Schedule Trigger cada 1 min) deberia haber asignado la orden");

        String estadoTrasNotificacion = esperarEstado(codigoAcceso, "notificado");
        assertEquals("notificado", estadoTrasNotificacion,
                "El Workflow 3 (Schedule Trigger cada 1 min) deberia haber notificado la orden");
    }

    private String esperarEstado(String codigoAcceso, String estadoEsperado) throws Exception {
        long inicio = System.currentTimeMillis();
        String ultimoEstado = null;

        while (System.currentTimeMillis() - inicio < TIEMPO_MAXIMO_POR_ESTADO_MS) {
            RespuestaApi respuesta = cliente.consultarOrden(codigoAcceso);
            if (respuesta.codigoEstado == 200) {
                ultimoEstado = respuesta.cuerpo.optString("estado", null);
                if (estadoEsperado.equals(ultimoEstado)) {
                    return ultimoEstado;
                }
            }
            Thread.sleep(INTERVALO_SONDEO_MS);
        }

        fail("La orden no llego al estado '" + estadoEsperado + "' en "
                + (TIEMPO_MAXIMO_POR_ESTADO_MS / 1000) + " segundos (ultimo estado visto: "
                + ultimoEstado + ")");
        return null;
    }
}
