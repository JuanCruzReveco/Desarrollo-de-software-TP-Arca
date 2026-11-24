-- Ejecutar una vez en PostgreSQL existente, con respaldo previo y backend detenido.
-- WSFEv1 numera por CUIT, punto de venta y tipo de comprobante.
BEGIN;
DO $$
DECLARE antiguo record;
BEGIN
  FOR antiguo IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'factura_venta'::regclass
      AND contype = 'u'
      AND pg_get_constraintdef(oid) = 'UNIQUE (punto_venta_id, numero)'
  LOOP
    EXECUTE format('ALTER TABLE factura_venta DROP CONSTRAINT %I', antiguo.conname);
  END LOOP;
END $$;
ALTER TABLE factura_venta
  ADD CONSTRAINT uk_factura_pv_tipo_numero
  UNIQUE (punto_venta_id, tipo_comprobante_arca, numero);
COMMIT;
