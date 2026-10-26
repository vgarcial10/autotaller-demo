package com.autotaller.db;

import com.autotaller.util.EnvConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso de SOLO LECTURA a la base de datos de AutoTaller, unicamente
 * para mostrar una tabla de ordenes en la app de demostracion. No
 * reemplaza a n8n: los 4 workflows siguen siendo los unicos que
 * escriben en la base de datos; esta clase nunca hace INSERT/UPDATE.
 */
public class OrdenesRepository {

    public static final String ORDEN_RECIENTES = "recientes";
    public static final String ORDEN_PENDIENTES = "pendientes";

    private String urlConexion() {
        String host = EnvConfig.get("DB_HOST", "127.0.0.1");
        String puerto = EnvConfig.get("DB_PORT", "5432");
        String baseDatos = EnvConfig.get("POSTGRES_DB", "autotaller");
        return "jdbc:postgresql://" + host + ":" + puerto + "/" + baseDatos;
    }

    public List<Orden> listarOrdenes(String criterioOrden) throws SQLException {
        String usuario = EnvConfig.get("POSTGRES_USER", "autotaller_admin");
        String clave = EnvConfig.get("POSTGRES_PASSWORD", "");

        String ordenarPor = ORDEN_PENDIENTES.equals(criterioOrden)
                ? "CASE o.estado WHEN 'recibido' THEN 0 WHEN 'asignado' THEN 1 WHEN 'notificado' THEN 2 ELSE 3 END ASC, o.creado_en DESC"
                : "o.creado_en DESC";

        String sql = "SELECT o.id, o.estado, o.prioridad, o.problema, o.codigo_acceso, o.creado_en, "
                + "c.nombre AS cliente_nombre, v.placa "
                + "FROM ordenes o "
                + "JOIN clientes c ON c.id = o.cliente_id "
                + "JOIN vehiculos v ON v.id = o.vehiculo_id "
                + "ORDER BY " + ordenarPor;

        List<Orden> resultado = new ArrayList<>();
        try (Connection conexion = DriverManager.getConnection(urlConexion(), usuario, clave);
             Statement sentencia = conexion.createStatement();
             ResultSet filas = sentencia.executeQuery(sql)) {

            while (filas.next()) {
                resultado.add(new Orden(
                        filas.getInt("id"),
                        filas.getString("estado"),
                        filas.getString("prioridad"),
                        filas.getString("problema"),
                        filas.getString("codigo_acceso"),
                        filas.getTimestamp("creado_en").toLocalDateTime(),
                        filas.getString("cliente_nombre"),
                        filas.getString("placa")
                ));
            }
        }
        return resultado;
    }

    /**
     * Representa los ids de cliente y vehiculo asociados a una orden,
     * usado por las pruebas de deduplicacion para comparar entre dos
     * ordenes si se reutilizo el mismo cliente/vehiculo o se crearon nuevos.
     */
    public record ClienteVehiculoIds(int clienteId, int vehiculoId) {
    }

    /**
     * Lectura de SOLO LECTURA: dado el id de una orden, devuelve el
     * cliente_id y vehiculo_id que quedaron asociados a ella. Se usa
     * desde las pruebas para verificar que el Workflow 1 reutilizo un
     * cliente/vehiculo existente en vez de crear uno duplicado.
     */
    public ClienteVehiculoIds obtenerClienteYVehiculoPorOrden(int ordenId) throws SQLException {
        String usuario = EnvConfig.get("POSTGRES_USER", "autotaller_admin");
        String clave = EnvConfig.get("POSTGRES_PASSWORD", "");
        String sql = "SELECT cliente_id, vehiculo_id FROM ordenes WHERE id = ?";

        try (Connection conexion = DriverManager.getConnection(urlConexion(), usuario, clave);
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, ordenId);
            try (ResultSet fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    throw new SQLException("No se encontro la orden con id " + ordenId);
                }
                return new ClienteVehiculoIds(fila.getInt("cliente_id"), fila.getInt("vehiculo_id"));
            }
        }
    }
}
