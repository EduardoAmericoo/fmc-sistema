-- Estrutura do banco de dados (SQLite).
-- Este arquivo roda toda vez que o sistema abre. Use sempre
-- "CREATE TABLE IF NOT EXISTS" para não apagar dados existentes.
--
-- Cada história do MVP acrescenta aqui as suas tabelas
-- (usuario, imovel, inquilino, fiador, contrato, garantia, aluguel, recibo).
-- O pagamento fica registrado na própria tabela recibo.

CREATE TABLE IF NOT EXISTS sistema (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

INSERT OR IGNORE INTO sistema (chave, valor) VALUES ('versao_schema', '1');

-- BOARD-33 · Login: quem pode entrar no sistema.
-- A senha nunca é guardada: só o hash (ver util/SenhaUtil.java).
CREATE TABLE IF NOT EXISTS usuario (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    nome       TEXT    NOT NULL,
    email      TEXT    NOT NULL UNIQUE,
    senha_hash TEXT    NOT NULL,
    perfil     TEXT    NOT NULL,
    ativo      INTEGER NOT NULL DEFAULT 1
);
