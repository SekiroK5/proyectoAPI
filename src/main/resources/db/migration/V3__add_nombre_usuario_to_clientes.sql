ALTER TABLE clientes ADD COLUMN IF NOT EXISTS nombre_usuario VARCHAR(50) UNIQUE;
CREATE INDEX IF NOT EXISTS idx_clientes_usuario ON clientes(nombre_usuario);
