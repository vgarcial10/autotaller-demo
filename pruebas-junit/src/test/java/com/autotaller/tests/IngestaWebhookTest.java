package com.autotaller.tests;

import com.autotaller.client.AutoTallerClient;
import com.autotaller.client.AutoTallerClient.RespuestaApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Workflow 1 (Ingesta de solicitud de servicio).
 * Requiere que autotaller_n8n y autotaller_db esten corriendo
 * (docker start autotaller_db autotaller_n8n) y que el workflow
 * este Activado en n8n.
 */
@DisplayName("Workflow 1 - Ingesta de solicitud de servicio")
class IngestaWebhookTest {

    private static AutoTallerClient cliente;

    @BeforeAll
    static void setUp() {
        cliente = new AutoTallerClient();
    }

    @Test
    @DisplayName("Solicitud valida responde 200 con orden_id y codigo_acceso")
    void solicitudValidaDevuelveCodigoAcceso() throws Exception {
        RespuestaApi respuesta = cliente.enviarSolicitudServicio(
                "Victor Garcia", "5878-4896", "X23JF", "Mazda", "Miata",
                "Perdida de potencia del motor");

        assertEquals(200, respuesta.codigoEstado);
        assertTrue(respuesta.cuerpo.has("orden_id"));
        assertTrue(respuesta.cuerpo.has("codigo_acceso"));
        assertFalse(respuesta.cuerpo.getString("codigo_acceso").isBlank());
    }

    @Test
    @DisplayName("Solicitud con campos faltantes responde 400 con mensaje de error")
    void solicitudInvalidaDevuelveError() throws Exception {
        RespuestaApi respuesta = cliente.enviarSolicitudServicio(
                "Solo Nombre", null, null, null, null, null);

        assertEquals(400, respuesta.codigoEstado);
        assertTrue(respuesta.cuerpo.has("error"));
    }
}
