package com.autotaller.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.json.JSONObject;

/**
 * Cliente HTTP para los webhooks de los workflows de n8n del proyecto AutoTaller.
 *
 * Solo los Workflows 1 (ingesta) y 4 (consulta de orden) tienen un nodo Webhook
 * y se pueden invocar directamente por HTTP. Los Workflows 2 (procesamiento) y 3
 * (notificacion) se disparan solos con un Schedule Trigger cada 1 minuto y leen
 * ordenes pendientes de la base de datos; no tienen endpoint que este cliente
 * pueda llamar.
 */
public class AutoTallerClient {

    private final String baseUrl;
    private final HttpClient httpClient;

    public AutoTallerClient() {
        // 127.0.0.1 en vez de "localhost": evita que HttpClient intente
        // resolver por IPv6 primero y se quede colgado con el port-forwarding
        // de Docker Desktop en Windows (sin fallar, sin conectar).
        this("http://127.0.0.1:5678");
    }

    public AutoTallerClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Forzar HTTP/1.1: por defecto HttpClient intenta negociar
                // HTTP/2 incluso en texto plano, y el servidor de webhooks
                // de n8n no responde a esa negociacion (se queda colgado).
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** Resultado de una llamada: codigo de estado HTTP mas el cuerpo JSON de la respuesta. */
    public static class RespuestaApi {
        public final int codigoEstado;
        public final JSONObject cuerpo;

        public RespuestaApi(int codigoEstado, JSONObject cuerpo) {
            this.codigoEstado = codigoEstado;
            this.cuerpo = cuerpo;
        }
    }

    /**
     * Workflow 1 - Ingesta de solicitud de servicio.
     * POST /webhook/solicitud-servicio
     * Cualquier parametro puede ir null para simular una solicitud invalida
     * (el workflow responde 400 si falta nombre, telefono, placa o problema).
     */
    public RespuestaApi enviarSolicitudServicio(String nombreCliente, String telefono,
                                                 String placa, String marca, String modelo,
                                                 String problema) throws Exception {
        JSONObject cliente = new JSONObject();
        cliente.put("nombre", nombreCliente == null ? JSONObject.NULL : nombreCliente);
        cliente.put("telefono", telefono == null ? JSONObject.NULL : telefono);

        JSONObject vehiculo = new JSONObject();
        vehiculo.put("placa", placa == null ? JSONObject.NULL : placa);
        vehiculo.put("marca", marca == null ? JSONObject.NULL : marca);
        vehiculo.put("modelo", modelo == null ? JSONObject.NULL : modelo);

        JSONObject body = new JSONObject();
        body.put("cliente", cliente);
        body.put("vehiculo", vehiculo);
        body.put("problema", problema == null ? JSONObject.NULL : problema);

        return post("/webhook/solicitud-servicio", body);
    }

    /**
     * Workflow 4 - Consulta de orden.
     * POST /webhook/consultar-orden
     * Devuelve 200 con los datos de la orden si el codigo es valido,
     * o 403 con un mensaje de error si no lo es.
     */
    public RespuestaApi consultarOrden(String codigoAcceso) throws Exception {
        JSONObject body = new JSONObject();
        body.put("codigo_acceso", codigoAcceso == null ? JSONObject.NULL : codigoAcceso);
        return post("/webhook/consultar-orden", body);
    }

    private RespuestaApi post(String path, JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String texto = response.body();
        JSONObject json = (texto == null || texto.isBlank()) ? new JSONObject() : new JSONObject(texto);

        return new RespuestaApi(response.statusCode(), json);
    }
}
