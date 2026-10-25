-- Estrutura do banco de dados (SQLite).
-- Este arquivo roda toda vez que o sistema abre. Use sempre
-- "CREATE TABLE IF NOT EXISTS" para não apagar dados existentes.
--
-- Cada história do MVP acrescenta aqui as suas tabelas
-- (usuario, imovel, inquilino, contrato, garantia, aluguel, recibo).
-- O fiador fica dentro da tabela garantia.
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

-- BOARD-41 · Permissões ajustadas pelo Administrador, pessoa a pessoa.
-- Só tem linha aqui quem foge do padrão do perfil. "acoes" guarda os nomes
-- separados por vírgula (ex.: "VER_IMOVEIS,VER_INQUILINOS"). Vazio = não pode nada.
CREATE TABLE IF NOT EXISTS usuario_permissoes (
    usuario_id INTEGER PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    acoes      TEXT    NOT NULL
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

-- BOARD-34 · Imóveis da Fundação.
-- O código é a identificação e não pode se repetir. A unidade é opcional
-- (ex.: "Apto 101 · Bloco A"), mas, se for informada, também não se repete (regra no service).
-- Valores em dinheiro ficam como texto (ex.: "1500.00") para não perder centavos.
CREATE TABLE IF NOT EXISTS imovel (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo        TEXT NOT NULL UNIQUE,
    unidade       TEXT,
    tipo          TEXT NOT NULL,
    endereco      TEXT NOT NULL,
    bairro        TEXT,
    grupo         TEXT,
    valor_aluguel TEXT NOT NULL,
    taxa_luz      TEXT NOT NULL DEFAULT '0',
    valor_iptu    TEXT NOT NULL DEFAULT '0',
    situacao      TEXT NOT NULL
);

-- BOARD-36 · Contratos de locação. O contrato já nasce ativo.
-- Os valores (aluguel, luz, IPTU) são copiados do imóvel no dia do cadastro,
-- para um reajuste futuro no imóvel não mudar contratos antigos.
-- O número (ex.: "CT-1001") é gerado pelo sistema a partir do id.
-- Datas ficam como texto no formato AAAA-MM-DD (ex.: "2026-10-24").
CREATE TABLE IF NOT EXISTS contrato (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    numero              TEXT    UNIQUE,
    imovel_id           INTEGER NOT NULL REFERENCES imovel(id),
    inquilino_id        INTEGER NOT NULL REFERENCES inquilino(id),
    valor_aluguel       TEXT    NOT NULL,
    taxa_luz            TEXT    NOT NULL DEFAULT '0',
    valor_iptu          TEXT    NOT NULL DEFAULT '0',
    prazo               TEXT    NOT NULL,
    data_entrega_chaves TEXT    NOT NULL,
    data_termino        TEXT    NOT NULL,
    dia_vencimento      INTEGER NOT NULL,
    status              TEXT    NOT NULL
);

-- BOARD-36/57 · Garantia do contrato: fiador ou caução (uma por contrato).
-- tipo = 'FIADOR' usa as colunas fiador_*, tipo = 'CAUCAO' usa as colunas caucao_*.
-- As colunas fiador_conjuge_* só são preenchidas para fiador casado(a) ou em união estável.
CREATE TABLE IF NOT EXISTS garantia (
    contrato_id          INTEGER PRIMARY KEY REFERENCES contrato(id) ON DELETE CASCADE,
    tipo                 TEXT NOT NULL,
    fiador_nome          TEXT,
    fiador_cpf           TEXT,
    fiador_telefone      TEXT,
    fiador_estado_civil  TEXT,
    fiador_renda         TEXT,
    fiador_endereco      TEXT,
    fiador_conjuge_nome  TEXT,
    fiador_conjuge_cpf   TEXT,
    caucao_valor         TEXT,
    caucao_data          TEXT,
    caucao_forma         TEXT,
    caucao_recibo        TEXT
);
