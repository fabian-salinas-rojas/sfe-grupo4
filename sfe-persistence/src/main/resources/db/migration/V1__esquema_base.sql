-- SFE Grupo 4 - esquema base (PostgreSQL 16). Gestionado por Flyway: NO editar migraciones ya aplicadas.

-- ---------------------------------------------------------------- catalogos SUNAT (tablas de referencia)
CREATE TABLE cat_tipo_comprobante   (codigo CHAR(2)     PRIMARY KEY, nombre VARCHAR(60) NOT NULL);
CREATE TABLE cat_tipo_doc_identidad (codigo VARCHAR(2)  PRIMARY KEY, nombre VARCHAR(80) NOT NULL);
CREATE TABLE cat_afectacion_igv     (codigo CHAR(2)     PRIMARY KEY, nombre VARCHAR(80) NOT NULL, tributo VARCHAR(4) NOT NULL);
CREATE TABLE cat_unidad_medida      (codigo VARCHAR(3)  PRIMARY KEY, nombre VARCHAR(60) NOT NULL);
CREATE TABLE cat_tipo_nota_credito  (codigo CHAR(2)     PRIMARY KEY, nombre VARCHAR(80) NOT NULL);
CREATE TABLE cat_tipo_nota_debito   (codigo CHAR(2)     PRIMARY KEY, nombre VARCHAR(80) NOT NULL);

-- ---------------------------------------------------------------- emisor / POS / series
CREATE TABLE emisor (
    id                BIGSERIAL PRIMARY KEY,
    ruc               CHAR(11)     NOT NULL UNIQUE CHECK (ruc ~ '^[0-9]{11}$'),
    razon_social      VARCHAR(200) NOT NULL,
    nombre_comercial  VARCHAR(200),
    ubigeo            CHAR(6)      NOT NULL CHECK (ubigeo ~ '^[0-9]{6}$'),
    direccion         VARCHAR(200) NOT NULL,
    departamento      VARCHAR(60)  NOT NULL,
    provincia         VARCHAR(60)  NOT NULL,
    distrito          VARCHAR(60)  NOT NULL,
    entorno           VARCHAR(10)  NOT NULL DEFAULT 'BETA' CHECK (entorno IN ('BETA', 'PRODUCCION')),
    sol_usuario       VARCHAR(30),                 -- usuario secundario SOL (no es secreto)
    sol_clave_cifrada TEXT,                        -- AES-256-GCM en Base64; la Clave SOL JAMAS se guarda en claro
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pos (
    id            BIGSERIAL PRIMARY KEY,
    emisor_id     BIGINT      NOT NULL REFERENCES emisor(id),
    nombre        VARCHAR(80) NOT NULL,
    api_key_hash  CHAR(64)    NOT NULL UNIQUE,     -- SHA-256 (hex) de la API Key; la llave en claro no se almacena
    activo        BOOLEAN     NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE serie (
    id                 BIGSERIAL PRIMARY KEY,
    emisor_id          BIGINT   NOT NULL REFERENCES emisor(id),
    pos_id             BIGINT   NOT NULL REFERENCES pos(id),
    tipo_comprobante   CHAR(2)  NOT NULL REFERENCES cat_tipo_comprobante(codigo),
    serie              CHAR(4)  NOT NULL CHECK (serie ~ '^[FB][A-Z0-9]{3}$'),
    ultimo_correlativo BIGINT   NOT NULL DEFAULT 0 CHECK (ultimo_correlativo BETWEEN 0 AND 99999999),
    CONSTRAINT uq_serie UNIQUE (emisor_id, tipo_comprobante, serie)
);
CREATE INDEX ix_serie_pos ON serie (pos_id, tipo_comprobante);

-- ---------------------------------------------------------------- comprobante
CREATE TABLE comprobante (
    id                UUID PRIMARY KEY,
    emisor_id         BIGINT        NOT NULL REFERENCES emisor(id),
    pos_id            BIGINT        NOT NULL REFERENCES pos(id),
    id_venta_externo  VARCHAR(64)   NOT NULL,       -- idempotencia: una venta del POS = un comprobante
    tipo_comprobante  CHAR(2)       NOT NULL REFERENCES cat_tipo_comprobante(codigo),
    serie             CHAR(4)       NOT NULL,
    correlativo       BIGINT        NOT NULL CHECK (correlativo BETWEEN 1 AND 99999999),
    fecha_emision     DATE          NOT NULL,
    moneda            CHAR(3)       NOT NULL CHECK (moneda IN ('PEN', 'USD')),
    importe_total     NUMERIC(14,2) NOT NULL CHECK (importe_total >= 0),
    estado            VARCHAR(30)   NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN (
                          'PENDIENTE', 'EN_CONTINGENCIA', 'ACEPTADO', 'ACEPTADO_CON_OBSERVACIONES',
                          'RECHAZADO', 'EXCEPCION')),
    xml_firmado       BYTEA         NOT NULL,       -- exactamente los bytes firmados (ISO-8859-1)
    xml_sha256        CHAR(64)      NOT NULL,
    zip_nombre        VARCHAR(80)   NOT NULL,
    zip_contenido     BYTEA         NOT NULL,
    plazo_limite      DATE          NOT NULL,       -- RN-06: fecha limite de envio a SUNAT
    cdr_codigo        VARCHAR(10),
    cdr_descripcion   TEXT,
    cdr_observaciones TEXT,                         -- una por linea: "4031 - texto"
    cdr_zip           BYTEA,
    cdr_firma_integra BOOLEAN,
    cdr_recibido_en   TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_comprobante_numero  UNIQUE (emisor_id, tipo_comprobante, serie, correlativo),
    CONSTRAINT uq_comprobante_idventa UNIQUE (pos_id, id_venta_externo)
);
CREATE INDEX ix_comprobante_pendientes ON comprobante (plazo_limite)
    WHERE estado IN ('PENDIENTE', 'EN_CONTINGENCIA');

-- El documento firmado es INMUTABLE: ni el XML, ni el ZIP, ni el numero, ni el importe cambian jamas; y no se borra.
CREATE FUNCTION comprobante_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Los comprobantes no se eliminan (conservacion legal)' USING ERRCODE = 'P0001';
    END IF;
    IF NEW.xml_firmado     IS DISTINCT FROM OLD.xml_firmado
    OR NEW.xml_sha256      IS DISTINCT FROM OLD.xml_sha256
    OR NEW.zip_contenido   IS DISTINCT FROM OLD.zip_contenido
    OR NEW.zip_nombre      IS DISTINCT FROM OLD.zip_nombre
    OR NEW.serie           IS DISTINCT FROM OLD.serie
    OR NEW.correlativo     IS DISTINCT FROM OLD.correlativo
    OR NEW.tipo_comprobante IS DISTINCT FROM OLD.tipo_comprobante
    OR NEW.importe_total   IS DISTINCT FROM OLD.importe_total
    OR NEW.emisor_id       IS DISTINCT FROM OLD.emisor_id THEN
        RAISE EXCEPTION 'El comprobante firmado es inmutable' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_comprobante_inmutable BEFORE UPDATE OR DELETE ON comprobante
    FOR EACH ROW EXECUTE FUNCTION comprobante_inmutable();

-- ---------------------------------------------------------------- outbox transaccional (cola de envio)
CREATE TABLE envio_outbox (
    id                 UUID PRIMARY KEY,
    comprobante_id     UUID        NOT NULL UNIQUE REFERENCES comprobante(id),
    estado             VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'EN_PROCESO', 'COMPLETADO')),
    intentos           INT         NOT NULL DEFAULT 0,
    proximo_intento    TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_hasta        TIMESTAMPTZ,                  -- un worker que muere deja su lease vencer y otro retoma
    worker_id          VARCHAR(60),
    ultimo_error       TEXT,
    ultimo_intento_en  TIMESTAMPTZ,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_cola ON envio_outbox (proximo_intento) WHERE estado <> 'COMPLETADO';

-- ---------------------------------------------------------------- bitacora de auditoria (solo se agrega)
CREATE TABLE bitacora_auditoria (
    id             BIGSERIAL PRIMARY KEY,
    comprobante_id UUID,
    evento         VARCHAR(40)  NOT NULL,
    detalle        TEXT,                              -- nunca credenciales ni contenido de llaves
    actor          VARCHAR(60)  NOT NULL,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_bitacora_comprobante ON bitacora_auditoria (comprobante_id);

CREATE FUNCTION bitacora_solo_agregar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La bitacora de auditoria es de solo agregar' USING ERRCODE = 'P0001';
END $$;
CREATE TRIGGER trg_bitacora_solo_agregar BEFORE UPDATE OR DELETE ON bitacora_auditoria
    FOR EACH ROW EXECUTE FUNCTION bitacora_solo_agregar();
