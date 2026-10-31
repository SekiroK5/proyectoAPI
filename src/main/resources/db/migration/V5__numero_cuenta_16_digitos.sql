-- =====================================================================
-- V5: El numero de cuenta pasa de 10 a 16 digitos
-- =====================================================================

ALTER TABLE cuentas DROP CONSTRAINT IF EXISTS chk_cuentas_numero;

ALTER TABLE cuentas ALTER COLUMN numero_cuenta TYPE VARCHAR(16);

-- Las cuentas existentes conservan su numero completado con ceros a la izquierda.
-- Las nuevas siempre inician con un digito del 1 al 9, por lo que no pueden repetirse con estas.
UPDATE cuentas SET numero_cuenta = LPAD(numero_cuenta, 16, '0') WHERE char_length(numero_cuenta) < 16;

ALTER TABLE cuentas
    ADD CONSTRAINT chk_cuentas_numero CHECK (numero_cuenta ~ '^[0-9]{16}$');

COMMENT ON COLUMN cuentas.numero_cuenta IS 'Numero de cuenta unico de 16 digitos';
