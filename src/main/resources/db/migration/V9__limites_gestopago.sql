-- =====================================================================
-- V9: Limites de llamadas a GestoPago/PuntoRed
-- PuntoRed bloquea la IP si se rebasan sus limites (p. ej. getProductList: 3 veces al dia).
-- =====================================================================

-- Contador diario por operacion. Vive en la base de datos para que reinicios y nuevos
-- despliegues no lo reinicien.
CREATE TABLE IF NOT EXISTS llamadas_gestopago (
    fecha     DATE        NOT NULL,
    operacion VARCHAR(30) NOT NULL,
    total     INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (fecha, operacion),
    CONSTRAINT chk_llamadas_total CHECK (total >= 0)
);

-- Catalogo de productos guardado en la base de datos, como pide la documentacion:
-- /productosList y los pagos lo leen de aqui y nunca llaman a getProductList.
CREATE TABLE IF NOT EXISTS productos_catalogo (
    id                     SERIAL PRIMARY KEY,
    id_servicio            INTEGER        NOT NULL,
    id_producto            INTEGER        NOT NULL,
    id_cat_tipo_servicio   INTEGER,
    servicio               TEXT,
    producto               TEXT,
    precio                 NUMERIC(15, 2),
    tipo_front             INTEGER,
    has_digito_verificador BOOLEAN,
    show_ayuda             BOOLEAN,
    tipo_referencia        VARCHAR(5),
    legend                 TEXT,
    fecha_actualizacion    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_productos_catalogo UNIQUE (id_servicio, id_producto)
);

-- confirmTx con reintentos espaciados y un maximo de intentos
ALTER TABLE transacciones
    ADD COLUMN IF NOT EXISTS intentos_confirmacion INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS proxima_confirmacion  TIMESTAMP;

ALTER TABLE transacciones DROP CONSTRAINT IF EXISTS chk_transacciones_estatus;
ALTER TABLE transacciones
    ADD CONSTRAINT chk_transacciones_estatus
        CHECK (estatus IN ('PENDIENTE', 'APLICADA', 'RECHAZADA', 'PENDIENTE_CONFIRMACION', 'REVISION_MANUAL'));

COMMENT ON TABLE llamadas_gestopago IS 'Llamadas hechas a GestoPago por dia y operacion, para no rebasar sus limites';
COMMENT ON TABLE productos_catalogo IS 'Catalogo de getProductList, actualizado una vez al dia';
COMMENT ON COLUMN transacciones.proxima_confirmacion IS 'No se llama a confirmTx antes de esta fecha (reintentos espaciados)';
