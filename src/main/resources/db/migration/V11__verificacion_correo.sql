-- =====================================================================
-- V11: Verificacion del correo electronico con un codigo de 6 digitos
-- =====================================================================

-- Los clientes registrados antes de esta version se consideran verificados (DEFAULT TRUE);
-- los nuevos se registran con FALSE y deben confirmar el codigo que se envia a su correo.
ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS correo_verificado      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS correo_codigo_hash     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS correo_codigo_expira   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS correo_intentos        INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS correo_ultimo_envio    TIMESTAMP;

ALTER TABLE clientes
    ADD CONSTRAINT chk_clientes_correo_intentos CHECK (correo_intentos >= 0);

COMMENT ON COLUMN clientes.correo_verificado    IS 'TRUE cuando el cliente confirmo el codigo enviado a su correo';
COMMENT ON COLUMN clientes.correo_codigo_hash   IS 'SHA-256 (hex) del codigo vigente; el codigo nunca se guarda en texto plano';
COMMENT ON COLUMN clientes.correo_codigo_expira IS 'Fecha en que vence el codigo vigente';
COMMENT ON COLUMN clientes.correo_intentos      IS 'Intentos fallidos con el codigo vigente';
COMMENT ON COLUMN clientes.correo_ultimo_envio  IS 'Ultimo envio del codigo; limita los reenvios';
