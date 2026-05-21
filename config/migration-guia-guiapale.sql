-- Migración: TransporteCarga / detalle → Guia + GuiaPale (código G-{numero_guia})
-- Ejecutar en PostgreSQL según tu entorno (ajusta nombres de tablas legacy si difieren).

CREATE TABLE IF NOT EXISTS guia (
    id BIGSERIAL PRIMARY KEY,
    numero_guia VARCHAR(128) NOT NULL,
    transporte_id BIGINT NOT NULL REFERENCES transporte(transporteid),
    chofer_nombre VARCHAR(256) NOT NULL,
    chofer_documento VARCHAR(64),
    estado VARCHAR(32) NOT NULL DEFAULT 'BORRADOR',
    notas TEXT,
    fecha_salida TIMESTAMP,
    fecha_entrega TIMESTAMP,
    creado_por BIGINT,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    sucursal_destino_id BIGINT,
    ubicacion_destino_id BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_guia_numero ON guia (LOWER(numero_guia));

-- Ampliar guiapale (tabla ya existente con origen/destino)
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS codigo VARCHAR(80);
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS guia_id BIGINT;
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS pale_id BIGINT;
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS cantidad INTEGER NOT NULL DEFAULT 1;
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS observacion TEXT;
ALTER TABLE guiapale ADD COLUMN IF NOT EXISTS fecha_registro TIMESTAMP NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS uq_guiapale_codigo ON guiapale (LOWER(codigo));

-- Opcional: FKs cuando datos migrados
-- ALTER TABLE guiapale ADD CONSTRAINT fk_guiapale_guia FOREIGN KEY (guia_id) REFERENCES guia(id);
-- ALTER TABLE guiapale ADD CONSTRAINT fk_guiapale_pale FOREIGN KEY (pale_id) REFERENCES pale(paleeid);
