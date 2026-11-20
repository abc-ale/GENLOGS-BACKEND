-- RF-37: tasa de tributos vigente y configurable, con desglose IGV + IPM.
-- Ejecutar una vez sobre la base (después de V9; V10 es la de roles). Es idempotente.
--
-- 1) Historial de tasas. Rige la de "vigente_desde" más reciente que no sea posterior
--    a la fecha del documento. Se siembra la tasa del documento para 2026:
--    IGV 15,5 % + IPM 2,5 % = 18 %.
CREATE TABLE IF NOT EXISTS tasa_igv (
    id_tasa_igv     SERIAL PRIMARY KEY,
    vigente_desde   DATE          NOT NULL,
    porcentaje_igv  NUMERIC(5, 2) NOT NULL CHECK (porcentaje_igv >= 0 AND porcentaje_igv <= 100),
    porcentaje_ipm  NUMERIC(5, 2) NOT NULL CHECK (porcentaje_ipm >= 0 AND porcentaje_ipm <= 100),
    descripcion     VARCHAR(255),
    user_create     VARCHAR(50)   NOT NULL,
    process_create  VARCHAR(100)  NOT NULL,
    date_create     TIMESTAMP     NOT NULL DEFAULT NOW(),
    user_update     VARCHAR(50),
    process_update  VARCHAR(100),
    date_update     TIMESTAMP,
    status          CHAR(1)       NOT NULL DEFAULT 'A',
    CONSTRAINT uq_tasa_igv_vigente_desde UNIQUE (vigente_desde),
    CONSTRAINT ck_tasa_igv_total CHECK (porcentaje_igv + porcentaje_ipm <= 100)
);

INSERT INTO tasa_igv (vigente_desde, porcentaje_igv, porcentaje_ipm, descripcion, user_create, process_create)
SELECT DATE '2026-01-01', 15.50, 2.50, 'Tasa total aplicable 2026: IGV 15,5 % + IPM 2,5 % (SUNAT).',
       'sistema', 'MIGRACION_V11_IGV'
WHERE NOT EXISTS (SELECT 1 FROM tasa_igv WHERE vigente_desde = DATE '2026-01-01');

-- 2) Cada cotización y facturación guarda la tasa con que se creó, para que un cambio
--    de tasa no altere los documentos anteriores. Los registros existentes se calcularon
--    con 18 % en total, por eso reciben 15,50 + 2,50.
ALTER TABLE cotizacion  ADD COLUMN IF NOT EXISTS porcentaje_igv NUMERIC(5, 2) NOT NULL DEFAULT 15.50;
ALTER TABLE cotizacion  ADD COLUMN IF NOT EXISTS porcentaje_ipm NUMERIC(5, 2) NOT NULL DEFAULT 2.50;
ALTER TABLE facturacion ADD COLUMN IF NOT EXISTS porcentaje_igv NUMERIC(5, 2) NOT NULL DEFAULT 15.50;
ALTER TABLE facturacion ADD COLUMN IF NOT EXISTS porcentaje_ipm NUMERIC(5, 2) NOT NULL DEFAULT 2.50;

-- Nota: las vistas vw_cotizacion_totales y vw_facturacion_totales siguen calculando con
-- 18 % fijo. El backend ya no usa sus columnas igv/total: toma solo el subtotal de
-- vw_cotizacion_totales y calcula los tributos con la tasa guardada en cada documento.
