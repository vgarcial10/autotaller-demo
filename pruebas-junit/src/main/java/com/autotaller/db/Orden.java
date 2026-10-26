package com.autotaller.db;

import java.time.LocalDateTime;

/** Representa una fila de la tabla ordenes, con el nombre del cliente y la placa ya resueltos. */
public record Orden(
        int id,
        String estado,
        String prioridad,
        String problema,
        String codigoAcceso,
        LocalDateTime creadoEn,
        String clienteNombre,
        String placa
) {
}
