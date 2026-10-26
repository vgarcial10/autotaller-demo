package com.autotaller.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee las variables del archivo .env del proyecto (el mismo que usa
 * docker-compose), para no tener que escribir credenciales en el
 * codigo fuente. Busca .env en el directorio actual y, si no esta,
 * un nivel arriba (cuando se corre desde la carpeta pruebas-junit,
 * el .env real esta en la raiz del proyecto, un nivel arriba).
 */
public final class EnvConfig {

    private static final Map<String, String> VALORES = cargar();

    private EnvConfig() {
    }

    public static String get(String clave, String porDefecto) {
        String valor = VALORES.get(clave);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(clave);
        }
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    private static Map<String, String> cargar() {
        Map<String, String> valores = new HashMap<>();
        for (Path candidato : new Path[]{Path.of(".env"), Path.of("..", ".env")}) {
            if (Files.isRegularFile(candidato)) {
                leerArchivo(candidato, valores);
                break;
            }
        }
        return valores;
    }

    private static void leerArchivo(Path archivo, Map<String, String> destino) {
        try {
            for (String linea : Files.readAllLines(archivo)) {
                String limpia = linea.strip();
                if (limpia.isEmpty() || limpia.startsWith("#") || !limpia.contains("=")) {
                    continue;
                }
                int separador = limpia.indexOf('=');
                String clave = limpia.substring(0, separador).strip();
                String valor = limpia.substring(separador + 1).strip();
                destino.put(clave, valor);
            }
        } catch (IOException e) {
            // Sin .env disponible: se usara System.getenv() como respaldo.
        }
    }
}
