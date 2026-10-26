package com.autotaller.tests;

import com.autotaller.client.AutoTallerClient;
import com.autotaller.client.AutoTallerClient.RespuestaApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Workflow 4 (Consulta de orden / control de acceso).
 * Crea una orden real en setUp() para tener un codigo_acceso valido
 * que consultar (depende de que el Workflow 1 este activo tambien).
 */
@DisplayName("Workflow 4 - Consulta de orden")
class ConsultaOrdenWebhookTest {

    private static AutoTallerClient cliente;
    private static String codigoAccesoValido;

    @BeforeAll
    static void setUp() throws Exception {
        cliente = new AutoTallerClient();

        RespuestaApi solicitud = cliente.enviarSolicitudServicio(
                "Cliente de Prueba JUnit", "5555-0000", "TEST01", "Toyota", "Corolla",
                "Fuga de aceite en el motor");

        codigoAccesoValido = solicitud.cuerpo.getString("codigo_acceso");
    }

    @Test
    @DisplayName("Codigo de acceso valido responde 200 con los datos de la orden")
    void codigoValidoDevuelveOrden() throws Exception {
        RespuestaApi respuesta = cliente.consultarOrden(codigoAccesoValido);

        assertEquals(200, respuesta.codigoEstado);
        assertEquals(codigoAccesoValido, respuesta.cuerpo.getString("codigo_acceso"));
        assertTrue(respuesta.cuerpo.has("estado"));
    }

    @Test
    @DisplayName("Codigo de acceso invalido responde 403 - acceso denegado")
    void codigoInvalidoDevuelveAccesoDenegado() throws Exception {
        RespuestaApi respuesta = cliente.consultarOrden("CODIGOFALSO");

        assertEquals(403, respuesta.codigoEstado);
        assertTrue(respuesta.cuerpo.has("error"));
    }
}
