-- =====================================================================
-- V8: Depositos a la cuenta propia (bitacora de cada abono al saldo)
-- =====================================================================

CREATE TABLE IF NOT EXISTS depositos (
    id               SERIAL PRIMARY KEY,
    cuenta_id        INTEGER        NOT NULL REFERENCES cuentas(id) ON DELETE RESTRICT,
    monto            NUMERIC(15, 2) NOT NULL,
    saldo_resultante NUMERIC(15, 2) NOT NULL,
    fecha_creacion   TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_depositos_monto CHECK (monto > 0),
    CONSTRAINT chk_depositos_saldo CHECK (saldo_resultante >= 0)
);

CREATE INDEX IF NOT EXISTS idx_depositos_cuenta ON depositos (cuenta_id, fecha_creacion);

COMMENT ON TABLE depositos IS 'Abonos al saldo de una cuenta hechos por su titular, con el saldo resultante';
