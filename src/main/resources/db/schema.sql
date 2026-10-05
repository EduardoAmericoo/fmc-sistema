-- Estrutura do banco de dados (SQLite).
-- Este arquivo roda toda vez que o sistema abre. Use sempre
-- "CREATE TABLE IF NOT EXISTS" para não apagar dados existentes.
--
-- Cada história do MVP acrescenta aqui as suas tabelas
-- (usuario, imovel, inquilino, contrato, garantia, cobranca, pagamento, recibo).

CREATE TABLE IF NOT EXISTS sistema (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

INSERT OR IGNORE INTO sistema (chave, valor) VALUES ('versao_schema', '1');
