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

-- BOARD-35 · Inquilinos, com os dados da proposta.
-- O CPF é guardado só com os 11 dígitos e não pode se repetir.
-- Valores em dinheiro ficam como texto (ex.: "2500.00") para não perder centavos.
-- O cônjuge fica na mesma linha: as colunas conjuge_* só são preenchidas
-- para casado(a) ou união estável.
CREATE TABLE IF NOT EXISTS inquilino (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    nome           TEXT NOT NULL,
    cpf            TEXT NOT NULL UNIQUE,
    telefone       TEXT,
    email          TEXT,
    estado_civil   TEXT NOT NULL,
    profissao      TEXT,
    local_trabalho TEXT,
    renda_mensal   TEXT NOT NULL DEFAULT '0',
    outras_rendas  TEXT NOT NULL DEFAULT '0',
    conjuge_nome   TEXT,
    conjuge_cpf    TEXT,
    conjuge_renda  TEXT
);
