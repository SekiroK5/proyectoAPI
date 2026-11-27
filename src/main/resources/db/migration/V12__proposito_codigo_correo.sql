-- =====================================================================
-- V12: Para que sirve el codigo enviado por correo
-- =====================================================================

-- Un mismo cliente puede recibir codigos para confirmar su correo, para iniciar sesion o para autorizar
-- un pago o deposito. Cada codigo solo sirve para su proposito.
ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS correo_codigo_proposito VARCHAR(20);

ALTER TABLE clientes
    ADD CONSTRAINT chk_clientes_correo_proposito
        CHECK (correo_codigo_proposito IS NULL OR correo_codigo_proposito IN ('CONFIRMAR_CORREO', 'INICIO_SESION', 'OPERACION'));

-- Los codigos emitidos antes de esta version eran para confirmar el correo
UPDATE clientes SET correo_codigo_proposito = 'CONFIRMAR_CORREO'
WHERE correo_codigo_hash IS NOT NULL AND correo_codigo_proposito IS NULL;

COMMENT ON COLUMN clientes.correo_codigo_proposito IS 'CONFIRMAR_CORREO, INICIO_SESION u OPERACION: para que sirve el codigo vigente';
