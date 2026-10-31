-- =====================================================================
-- V6: Inicio de sesion (contrasena) y segundo factor de autenticacion
-- =====================================================================

-- Las columnas aceptan NULL porque los clientes registrados antes de esta version
-- no tienen contrasena; no podran iniciar sesion hasta que se les asigne una.
ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS contrasena_hash        VARCHAR(60),
    ADD COLUMN IF NOT EXISTS a2f_metodo             VARCHAR(20),
    ADD COLUMN IF NOT EXISTS a2f_secreto            VARCHAR(32),
    ADD COLUMN IF NOT EXISTS a2f_activo             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS a2f_ultimo_paso        BIGINT,
    ADD COLUMN IF NOT EXISTS intentos_fallidos      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bloqueado_hasta        TIMESTAMP;

ALTER TABLE clientes
    -- Hash BCrypt: siempre 60 caracteres
    ADD CONSTRAINT chk_clientes_contrasena_hash CHECK (contrasena_hash IS NULL OR char_length(contrasena_hash) = 60),
    -- Por ahora solo TOTP; huella y reconocimiento facial se agregaran como nuevos metodos
    ADD CONSTRAINT chk_clientes_a2f_metodo      CHECK (a2f_metodo IS NULL OR a2f_metodo IN ('TOTP')),
    ADD CONSTRAINT chk_clientes_a2f_activo      CHECK (NOT a2f_activo OR (a2f_metodo IS NOT NULL AND a2f_secreto IS NOT NULL)),
    ADD CONSTRAINT chk_clientes_intentos        CHECK (intentos_fallidos >= 0);

COMMENT ON COLUMN clientes.contrasena_hash   IS 'Contrasena cifrada con BCrypt (nunca se guarda en texto plano)';
COMMENT ON COLUMN clientes.a2f_secreto       IS 'Secreto TOTP en Base32 compartido con la app autenticadora';
COMMENT ON COLUMN clientes.a2f_ultimo_paso   IS 'Ultimo paso de tiempo TOTP aceptado; impide reutilizar el mismo codigo';
COMMENT ON COLUMN clientes.intentos_fallidos IS 'Intentos fallidos consecutivos de contrasena o codigo A2F';
COMMENT ON COLUMN clientes.bloqueado_hasta   IS 'Si tiene valor futuro, el inicio de sesion esta bloqueado temporalmente';
