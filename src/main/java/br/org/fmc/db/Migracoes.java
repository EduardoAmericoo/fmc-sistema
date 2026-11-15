package br.org.fmc.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Ajustes em bancos que já existem, quando uma tabela precisa mudar de um jeito que o
 * "CREATE TABLE IF NOT EXISTS" do schema.sql não alcança (ele só cria tabelas novas).
 *
 * Cada ajuste confere se ainda é preciso antes de mexer, então rodar de novo não faz nada.
 */
final class Migracoes {

    private Migracoes() {
    }

    /** Coluna aluguel_id com UNIQUE: o jeito antigo da tabela recibo (até a D10). */
    private static final Pattern ALUGUEL_UNICO =
            Pattern.compile("aluguel_id\\s+INTEGER\\s+NOT\\s+NULL\\s+UNIQUE", Pattern.CASE_INSENSITIVE);

    /** A tabela recibo de hoje, igual à do schema.sql, sem o UNIQUE no aluguel_id. */
    private static final String RECIBO_NOVO = """
            CREATE TABLE recibo_novo (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                aluguel_id       INTEGER NOT NULL REFERENCES aluguel(id),
                ano              INTEGER NOT NULL,
                sequencial       INTEGER NOT NULL,
                data_pagamento   TEXT    NOT NULL,
                forma_pagamento  TEXT    NOT NULL,
                documento        TEXT,
                dias_atraso      INTEGER NOT NULL,
                meses_encargos   INTEGER NOT NULL,
                multa            TEXT    NOT NULL,
                juros            TEXT    NOT NULL,
                correcao         TEXT    NOT NULL,
                valor_total      TEXT    NOT NULL,
                emitido_por      INTEGER NOT NULL REFERENCES usuario(id),
                data_emissao     TEXT    NOT NULL,
                UNIQUE (ano, sequencial)
            )""";

    private static final String COLUNAS = "id, aluguel_id, ano, sequencial, data_pagamento, forma_pagamento, "
            + "documento, dias_atraso, meses_encargos, multa, juros, correcao, valor_total, emitido_por, data_emissao";

    static void aplicar(Connection conexao) throws SQLException {
        reciboSemAluguelUnico(conexao);
    }

    /**
     * [D10] BOARD-65 · Depois de cancelar um recibo, o mesmo aluguel recebe um recibo novo.
     * O SQLite não deixa tirar um UNIQUE de uma coluna, então a tabela é refeita do jeito que
     * a documentação do SQLite ensina: cria a nova, copia tudo, apaga a velha e renomeia.
     * Os ids dos recibos continuam os mesmos, então a dispensa e os lançamentos seguem ligados.
     */
    static void reciboSemAluguelUnico(Connection conexao) throws SQLException {
        String atual = definicao(conexao, "recibo");
        if (atual == null || !ALUGUEL_UNICO.matcher(atual).find()) {
            return; // banco novo ou já ajustado
        }
        try (Statement st = conexao.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF"); // só funciona fora da transação
            conexao.setAutoCommit(false);
            try {
                st.execute(RECIBO_NOVO);
                st.execute("INSERT INTO recibo_novo (" + COLUNAS + ") SELECT " + COLUNAS + " FROM recibo");
                st.execute("DROP TABLE recibo");
                st.execute("ALTER TABLE recibo_novo RENAME TO recibo");
                st.execute("CREATE INDEX IF NOT EXISTS recibo_por_aluguel ON recibo(aluguel_id)");
                try (ResultSet problemas = st.executeQuery("PRAGMA foreign_key_check")) {
                    if (problemas.next()) {
                        throw new SQLException("A tabela recibo ficou com uma ligação quebrada na tabela "
                                + problemas.getString(1) + ".");
                    }
                }
                conexao.commit();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            } finally {
                conexao.setAutoCommit(true);
                st.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    /** O CREATE TABLE guardado pelo SQLite para a tabela, ou null se ela não existe. */
    static String definicao(Connection conexao, String tabela) throws SQLException {
        try (PreparedStatement st = conexao.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            st.setString(1, tabela);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
