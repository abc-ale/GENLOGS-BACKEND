-- Agrega existencias administrables por producto. Ejecutar una vez sobre la base V8.
ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS stock INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_producto_stock_no_negativo') THEN
        ALTER TABLE producto ADD CONSTRAINT ck_producto_stock_no_negativo CHECK (stock >= 0);
    END IF;
END $$;

-- El pasaporte (PAS) tiene longitud 12 en tipo_documento, pero tercero.numero_documento
-- era VARCHAR(11): un pasaporte válido no se podía guardar.
-- Ampliar una columna VARCHAR es seguro; las vistas actuales no seleccionan esta columna.
ALTER TABLE tercero ALTER COLUMN numero_documento TYPE VARCHAR(12);