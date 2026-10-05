package br.org.fmc.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Conexão com o banco SQLite.
 *
 * O banco é um único arquivo (por padrão, data/fmc.db), parecido com o
 * db.sqlite3 do Django. As tabelas são criadas a partir de db/schema.sql.
 */
public final class Database {

    private static Path arquivo = Path.of("data", "fmc.db");

    private Database() {
    }

    /** Troca o arquivo do banco (usado nos testes para não mexer nos dados reais). */
    public static void usarArquivo(Path novoArquivo) {
        arquivo = novoArquivo;
    }

    /** Abre uma conexão. Use sempre com try-with-resources para fechar no fim. */
    public static Connection conectar() throws SQLException {
        try {
            Path pasta = arquivo.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
        } catch (IOException e) {
            throw new SQLException("Não foi possível criar a pasta do banco: " + arquivo, e);
        }
        Connection conexao = DriverManager.getConnection("jdbc:sqlite:" + arquivo);
        try (Statement st = conexao.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conexao;
    }

    /** Cria as tabelas que ainda não existem, executando db/schema.sql. */
    public static void inicializar() throws SQLException {
        String sql = lerSchema();
        try (Connection conexao = conectar(); Statement st = conexao.createStatement()) {
            for (String comando : sql.split(";")) {
                if (!comando.isBlank()) {
                    st.execute(comando);
                }
            }
        }
    }

    private static String lerSchema() throws SQLException {
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) {
                throw new SQLException("Arquivo db/schema.sql não encontrado.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Erro ao ler db/schema.sql", e);
        }
    }
}
