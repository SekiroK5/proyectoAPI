-- =====================================================================
-- V10: Verificacion de referencias y cierre de sesion
-- =====================================================================

-- Cada verificacion de referencia (verifyReference de GestoPago, o solo de formato cuando el
-- servicio no ofrece verificacion). Un pago de servicio exige un folio de verificacion valido,
-- vigente, del mismo cliente, servicio y referencia, y que no se haya usado antes.
CREATE TABLE IF NOT EXISTS verificaciones_referencia (
    folio               VARCHAR(36)    PRIMARY KEY,
    cliente_id          INTEGER        NOT NULL REFERENCES clientes(id) ON DELETE RESTRICT,
    id_servicio         INTEGER        NOT NULL,
    id_producto         INTEGER        NOT NULL,
    referencia          VARCHAR(100)   NOT NULL,
    resultado           VARCHAR(20)    NOT NULL,
    verificada_gestopago BOOLEAN       NOT NULL,
    mensaje             VARCHAR(500),
    saldo_pendiente     NUMERIC(15, 2),
    usada               BOOLEAN        NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP      NOT NULL DEFAULT NOW(),
    expira              TIMESTAMP      NOT NULL,
    CONSTRAINT chk_verificaciones_resultado CHECK (resultado IN ('VALIDA', 'INVALIDA', 'FORMATO_VALIDO'))
);

CREATE INDEX IF NOT EXISTS idx_verificaciones_cliente ON verificaciones_referencia (cliente_id, fecha_creacion);

-- Tokens de acceso cerrados con /auth/logout antes de vencer. Se guardan solo hasta su expiracion.
CREATE TABLE IF NOT EXISTS tokens_revocados (
    jti    VARCHAR(36) PRIMARY KEY,
    expira TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tokens_revocados_expira ON tokens_revocados (expira);

COMMENT ON TABLE verificaciones_referencia IS 'Verificaciones de referencia previas a un pago de servicio';
COMMENT ON TABLE tokens_revocados IS 'Tokens JWT de acceso invalidados al cerrar sesion';
