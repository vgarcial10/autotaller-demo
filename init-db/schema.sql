-- AutoTaller — Esquema inicial
-- Se ejecuta automáticamente al levantar el contenedor de PostgreSQL por primera vez.

CREATE TABLE IF NOT EXISTS clientes (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vehiculos (
    id SERIAL PRIMARY KEY,
    placa VARCHAR(15) NOT NULL,
    marca VARCHAR(50),
    modelo VARCHAR(50),
    cliente_id INTEGER NOT NULL REFERENCES clientes(id)
);

CREATE TABLE IF NOT EXISTS ordenes (
    id SERIAL PRIMARY KEY,
    cliente_id INTEGER NOT NULL REFERENCES clientes(id),
    vehiculo_id INTEGER NOT NULL REFERENCES vehiculos(id),
    problema TEXT NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'recibido',
    codigo_acceso VARCHAR(10) NOT NULL UNIQUE,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);

-- Bitácora de auditoría: registra cada intento de ingesta, exitoso o no.
-- Cumple el requisito de "Bitácora de ejecución" del proyecto.
CREATE TABLE IF NOT EXISTS bitacora (
    id SERIAL PRIMARY KEY,
    workflow VARCHAR(50) NOT NULL,
    resultado VARCHAR(20) NOT NULL,   -- 'exito' | 'error'
    detalle TEXT,
    payload_recibido JSONB,
    creado_en TIMESTAMP NOT NULL DEFAULT now()
);

-- Índice para que el cliente pueda consultar su orden por código (control de acceso)
CREATE INDEX IF NOT EXISTS idx_ordenes_codigo_acceso ON ordenes(codigo_acceso);
