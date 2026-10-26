# Pruebas JUnit - Cliente de los webhooks de AutoTaller

Proyecto Maven aparte del automatizador n8n. Contiene un cliente Java que
consume los webhooks de AutoTaller y pruebas JUnit 5 sobre ese cliente.

## Requisitos

- JDK 17 o superior
- Maven
- Los contenedores `autotaller_db` y `autotaller_n8n` corriendo, con los
  4 workflows importados y Activados en n8n

```powershell
docker start autotaller_db autotaller_n8n
```

## Que se puede probar por HTTP y que no

Solo el Workflow 1 (ingesta) y el Workflow 4 (consulta de orden) tienen un
nodo Webhook. Los Workflows 2 (procesamiento) y 3 (notificacion) usan un
Schedule Trigger que corre solo cada 1 minuto y lee ordenes pendientes de
la base de datos: no hay URL que llamar para "probarlos" directamente.

Por eso hay 3 clases de prueba:

| Clase | Que prueba |
|---|---|
| `IngestaWebhookTest` | Workflow 1: solicitud valida (200) y solicitud con campos faltantes (400) |
| `ConsultaOrdenWebhookTest` | Workflow 4: codigo de acceso valido (200) e invalido (403) |
| `CicloCompletoOrdenTest` | Workflows 2 y 3 de forma indirecta: crea una orden y espera (consultando el Workflow 4 repetidamente) a que el Schedule Trigger la pase sola de `recibido` a `asignado` y luego a `notificado` |

`CicloCompletoOrdenTest` es lenta a propósito (puede tardar varios minutos,
hasta ~4-5 min en el peor caso) porque depende de que el Schedule Trigger de
n8n dispare solo cada 60 segundos. No es un error de la prueba.

## Como correr las pruebas

```powershell
cd pruebas-junit
mvn test
```

Para correr solo las rápidas (sin esperar el ciclo completo):

```powershell
mvn test "-Dtest=IngestaWebhookTest,ConsultaOrdenWebhookTest"
```

## Reporte de resultados

Maven genera un reporte por clase en `target/surefire-reports/`
(archivos `.txt` y `.xml`) con el resultado de cada prueba — útil para
anexar evidencia al documento de pruebas aparte de la documentación técnica.

## Cómo usar la app (DemoApp)

La aplicación de escritorio (Swing) permite crear y seguir órdenes sin usar Postman ni la terminal.

**Ejecutar:**
```bash
mvn compile exec:java
```

**Funciones:**
1. **Crear orden** — llena el formulario (cliente, teléfono, vehículo, placa, problema) y presiona el botón de envío. La app llama al webhook de ingesta y muestra el `codigo_acceso` generado.
2. **Consultar orden** — pega el `codigo_acceso` en el campo de búsqueda para ver el estado actual de una orden existente.
3. **Seguimiento en vivo** — al crear una orden, la pantalla de estado se actualiza sola mostrando el avance automático: `recibido → asignado → notificado`, conforme los Workflows 2 y 3 la procesan en segundo plano (cada uno corre por Schedule Trigger cada minuto).

**Requisito:** los contenedores `autotaller_db` y `autotaller_n8n` deben estar corriendo (`docker start autotaller_db autotaller_n8n`).
