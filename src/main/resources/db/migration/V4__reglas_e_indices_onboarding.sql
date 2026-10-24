-- =====================================================================
-- V4: Ajuste de tipos, restricciones de dominio e indices del onboarding
-- =====================================================================

-- Normaliza datos previos para que cumplan las nuevas restricciones
-- (registros anteriores pueden traer valores en texto libre: 'Hombre', 'Soltera', etc.)
UPDATE clientes SET
    sexo = CASE
               WHEN UPPER(TRIM(sexo)) IN ('M', 'H', 'HOMBRE', 'MASCULINO') THEN 'M'
               WHEN UPPER(TRIM(sexo)) IN ('F', 'MUJER', 'FEMENINO') THEN 'F'
               ELSE 'O'
           END,
    estado_civil = CASE
               WHEN UPPER(TRIM(estado_civil)) IN ('SOLTERO', 'SOLTERA') THEN 'SOLTERO'
               WHEN UPPER(TRIM(estado_civil)) IN ('CASADO', 'CASADA') THEN 'CASADO'
               WHEN UPPER(TRIM(estado_civil)) IN ('DIVORCIADO', 'DIVORCIADA') THEN 'DIVORCIADO'
               WHEN UPPER(TRIM(estado_civil)) IN ('VIUDO', 'VIUDA') THEN 'VIUDO'
               WHEN UPPER(REPLACE(TRIM(estado_civil), ' ', '_')) = 'UNION_LIBRE' THEN 'UNION_LIBRE'
               ELSE UPPER(TRIM(estado_civil))
           END;
UPDATE clientes SET correo_electronico = LOWER(correo_electronico), curp = UPPER(curp), rfc = UPPER(rfc);

-- ---------------------------------------------------------------------
-- Tipos: se ajusta cada columna al tamano real del dato
-- ---------------------------------------------------------------------
ALTER TABLE clientes
    ALTER COLUMN sexo         TYPE VARCHAR(1),
    ALTER COLUMN estado_civil TYPE VARCHAR(15);

ALTER TABLE cuentas
    ALTER COLUMN numero_cuenta TYPE VARCHAR(10),
    ALTER COLUMN saldo         TYPE NUMERIC(15, 2);

-- ---------------------------------------------------------------------
-- Restricciones de dominio (ultima linea de defensa ante datos invalidos)
-- ---------------------------------------------------------------------
ALTER TABLE clientes
    ADD CONSTRAINT chk_clientes_sexo            CHECK (sexo IN ('M', 'F', 'O')),
    ADD CONSTRAINT chk_clientes_estado_civil    CHECK (estado_civil IN ('SOLTERO', 'CASADO', 'DIVORCIADO', 'VIUDO', 'UNION_LIBRE')),
    ADD CONSTRAINT chk_clientes_curp            CHECK (char_length(curp) = 18 AND curp = UPPER(curp)),
    ADD CONSTRAINT chk_clientes_rfc             CHECK (char_length(rfc) BETWEEN 12 AND 13 AND rfc = UPPER(rfc)),
    ADD CONSTRAINT chk_clientes_correo          CHECK (correo_electronico = LOWER(correo_electronico)),
    ADD CONSTRAINT chk_clientes_telefono_movil  CHECK (telefono_movil ~ '^[0-9]{10}$'),
    ADD CONSTRAINT chk_clientes_telefono_alt    CHECK (telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$'),
    ADD CONSTRAINT chk_clientes_ingreso         CHECK (ingreso_mensual > 0),
    ADD CONSTRAINT chk_clientes_fecha_nac       CHECK (fecha_nacimiento > DATE '1900-01-01'),
    ADD CONSTRAINT uq_clientes_domicilio        UNIQUE (domicilio_id);

ALTER TABLE domicilios
    ADD CONSTRAINT chk_domicilios_cp CHECK (codigo_postal ~ '^[0-9]{5}$');

ALTER TABLE cuentas
    ADD CONSTRAINT chk_cuentas_saldo   CHECK (saldo >= 0),
    ADD CONSTRAINT chk_cuentas_estatus CHECK (estatus IN ('ACTIVA', 'INACTIVA')),
    ADD CONSTRAINT chk_cuentas_numero  CHECK (numero_cuenta ~ '^[0-9]{10}$'),
    ADD CONSTRAINT uq_cuentas_cliente  UNIQUE (cliente_id);

-- ---------------------------------------------------------------------
-- Indices
-- Las columnas UNIQUE (curp, rfc, correo, usuario, numero_cuenta, cliente_id,
-- domicilio_id) ya tienen un indice implicito; los indices duplicados solo
-- consumen memoria y penalizan las escrituras, por lo que se eliminan.
-- ---------------------------------------------------------------------
DROP INDEX IF EXISTS idx_clientes_curp;
DROP INDEX IF EXISTS idx_clientes_rfc;
DROP INDEX IF EXISTS idx_clientes_correo;
DROP INDEX IF EXISTS idx_clientes_usuario;
DROP INDEX IF EXISTS idx_cuentas_numero;
DROP INDEX IF EXISTS idx_cuentas_cliente;

-- Consulta por rango de fechas de registro
CREATE INDEX IF NOT EXISTS idx_clientes_fecha_creacion ON clientes (fecha_creacion);
-- Indices parciales: solo indexan los registros activos (mas pequenos que un indice completo)
CREATE INDEX IF NOT EXISTS idx_clientes_activos ON clientes (fecha_creacion) WHERE activo;
CREATE INDEX IF NOT EXISTS idx_cuentas_activas  ON cuentas (fecha_apertura) WHERE estatus = 'ACTIVA';

COMMENT ON TABLE clientes   IS 'Clientes personas fisicas: datos personales, de contacto y laborales';
COMMENT ON TABLE domicilios IS 'Domicilio asociado a cada cliente (relacion 1:1)';
COMMENT ON TABLE cuentas    IS 'Cuenta bancaria asociada a cada cliente (relacion 1:1) con su saldo disponible';
COMMENT ON COLUMN clientes.activo IS 'Baja logica: FALSE indica cliente dado de baja';
