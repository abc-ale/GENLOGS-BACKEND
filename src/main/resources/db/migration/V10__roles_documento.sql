-- RF-31: un rol por cada trabajador del negocio del documento (sección 2.1.2):
-- ASESOR_COMERCIAL (Vendedor / Asesor Comercial), JEFE_ADMINISTRATIVO y CONTADOR_GENERAL.
-- Ejecutar una vez sobre la base V9.
--
-- Los roles actuales se RENOMBRAN para que los usuarios existentes conserven su rol:
--   ADMINISTRADOR -> JEFE_ADMINISTRATIVO   (administra usuarios y catálogos maestros: CU-03, CU-14)
--   VENDEDOR      -> ASESOR_COMERCIAL
-- y se crea CONTADOR_GENERAL. Es idempotente: se puede ejecutar más de una vez.

UPDATE rol
SET nombre_rol = 'JEFE_ADMINISTRATIVO',
    descripcion = 'Registra las órdenes de compra, mantiene proveedores y productos, y administra usuarios y catálogos maestros.',
    user_update = 'sistema', process_update = 'MIGRACION_V10_ROLES', date_update = NOW()
WHERE UPPER(TRIM(nombre_rol)) = 'ADMINISTRADOR'
  AND NOT EXISTS (SELECT 1 FROM rol WHERE nombre_rol = 'JEFE_ADMINISTRATIVO');

UPDATE rol
SET nombre_rol = 'ASESOR_COMERCIAL',
    descripcion = 'Vendedor / Asesor Comercial: atiende al cliente, elabora y envía las cotizaciones y les da seguimiento.',
    user_update = 'sistema', process_update = 'MIGRACION_V10_ROLES', date_update = NOW()
WHERE UPPER(TRIM(nombre_rol)) = 'VENDEDOR'
  AND NOT EXISTS (SELECT 1 FROM rol WHERE nombre_rol = 'ASESOR_COMERCIAL');

INSERT INTO rol (nombre_rol, descripcion, user_create, process_create, date_create, status)
SELECT 'CONTADOR_GENERAL',
       'Registra la facturación de las órdenes de compra, controla su estado y da seguimiento a la cobranza.',
       'sistema', 'MIGRACION_V10_ROLES', NOW(), 'A'
WHERE NOT EXISTS (SELECT 1 FROM rol WHERE nombre_rol = 'CONTADOR_GENERAL');
