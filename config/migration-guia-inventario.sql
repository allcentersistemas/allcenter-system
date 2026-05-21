-- Guías en inventario: número correlativo G-000001, líneas en guiadetalle (sin vehículo/chofer).

CREATE TABLE IF NOT EXISTS guia (
    id BIGSERIAL PRIMARY KEY,
    numero_guia VARCHAR(32) NOT NULL,
    estado VARCHAR(32) NOT NULL DEFAULT 'BORRADOR',
    notas TEXT,
    sucursal_destino_id BIGINT,
    ubicacion_destino_id BIGINT,
    creado_por BIGINT,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_guia_numero ON guia (LOWER(numero_guia));

-- Relajar NOT NULL antes de eliminar (PostgreSQL)
ALTER TABLE guia ALTER COLUMN chofer_nombre DROP NOT NULL;
ALTER TABLE guia ALTER COLUMN chofer_documento DROP NOT NULL;
ALTER TABLE guia ALTER COLUMN transporte_id DROP NOT NULL;

ALTER TABLE guia DROP COLUMN IF EXISTS transporte_id;
ALTER TABLE guia DROP COLUMN IF EXISTS chofer_nombre;
ALTER TABLE guia DROP COLUMN IF EXISTS chofer_documento;
ALTER TABLE guia DROP COLUMN IF EXISTS fecha_salida;
ALTER TABLE guia DROP COLUMN IF EXISTS fecha_entrega;

CREATE TABLE IF NOT EXISTS guiadetalle (
    id BIGSERIAL PRIMARY KEY,
    guia_id BIGINT NOT NULL REFERENCES guia(id) ON DELETE CASCADE,
    pale_id BIGINT,
    descripcion VARCHAR(1024) NOT NULL,
    unidad_medida VARCHAR(64) NOT NULL,
    cantidad VARCHAR(64) NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_guiadetalle_guia ON guiadetalle (guia_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_guiadetalle_guia_pale
    ON guiadetalle (guia_id, pale_id)
    WHERE pale_id IS NOT NULL;
