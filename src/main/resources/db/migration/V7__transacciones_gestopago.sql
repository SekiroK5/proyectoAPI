-- =====================================================================
-- V7: Pagos y recargas enviados a GestoPago/PuntoRed (sendTx)
-- La documentacion de PuntoRed exige guardar lo enviado y lo recibido de cada transaccion.
-- =====================================================================

CREATE TABLE IF NOT EXISTS transacciones (
    id                  SERIAL PRIMARY KEY,
    cuenta_id           INTEGER        NOT NULL REFERENCES cuentas(id) ON DELETE RESTRICT,
    upc                 VARCHAR(15)    NOT NULL UNIQUE,
    id_servicio         INTEGER        NOT NULL,
    id_producto         INTEGER        NOT NULL,
    telefono            VARCHAR(20)    NOT NULL,
    referencia          VARCHAR(100),
    monto               NUMERIC(15, 2) NOT NULL,
    hora_local          VARCHAR(14)    NOT NULL,
    unidad              VARCHAR(25)    NOT NULL,
    estatus             VARCHAR(25)    NOT NULL,
    -- JSON en claro que se cifro y envio; confirmTx debe reenviar exactamente el mismo
    payload_enviado     TEXT           NOT NULL,
    respuesta           TEXT,
    codigo_respuesta    VARCHAR(4),
    texto_respuesta     VARCHAR(500),
    id_tx               VARCHAR(30),
    num_autorizacion    VARCHAR(60),
    pin                 VARCHAR(250),
    fecha_creacion      TIMESTAMP      NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transacciones_monto   CHECK (monto > 0),
    CONSTRAINT chk_transacciones_upc     CHECK (char_length(upc) BETWEEN 3 AND 15),
    CONSTRAINT chk_transacciones_unidad  CHECK (char_length(unidad) BETWEEN 3 AND 25),
    CONSTRAINT chk_transacciones_estatus CHECK (estatus IN ('PENDIENTE', 'APLICADA', 'RECHAZADA', 'PENDIENTE_CONFIRMACION'))
);

CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta ON transacciones (cuenta_id, fecha_creacion);
-- Solo las que esperan confirmacion; las recorre el proceso de confirmTx
CREATE INDEX IF NOT EXISTS idx_transacciones_por_confirmar ON transacciones (fecha_creacion)
    WHERE estatus IN ('PENDIENTE', 'PENDIENTE_CONFIRMACION');

COMMENT ON TABLE transacciones IS 'Pagos y recargas enviados a GestoPago con su peticion y respuesta (bitacora obligatoria)';
COMMENT ON COLUMN transacciones.upc IS 'Unique Payment Code: identificador unico que GestoPago usa para detectar duplicados';
