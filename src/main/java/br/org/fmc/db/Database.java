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
 * O banco é um único arquivo (fmc.db), parecido com o db.sqlite3 do Django.
 * As tabelas são criadas a partir de db/schema.sql.
 *
 * Onde fica a pasta de dados (banco, documentos, cópias e caixa de saída):
 * - rodando pelo código (mvnw ou VS Code): data/, na pasta do projeto, como sempre;
 * - no programa pronto (FMC.exe, instalado ou portátil): %LOCALAPPDATA%\FMC\data
 *   (ex.: C:\Users\maria\AppData\Local\FMC\data). O programa instalado fica em
 *   "Arquivos de Programas", onde o Windows não deixa gravar; além disso, assim o
 *   atalho da Área de Trabalho e uma versão nova do programa enxergam os mesmos dados.
 */
public final class Database {

    private static Path arquivo = pastaPadrao(System.getProperty("jpackage.app-path"),
            System.getenv("LOCALAPPDATA")).resolve("fmc.db");

    private Database() {
    }

    /**
     * A pasta de dados padrão. O jpackage, que monta o FMC.exe, avisa que o sistema está rodando
     * como programa pronto pela propriedade "jpackage.app-path" (o caminho do FMC.exe).
     *
     * @param caminhoDoExe  o valor de jpackage.app-path (null rodando pelo código)
     * @param localAppData  a pasta %LOCALAPPDATA% do Windows
     */
    static Path pastaPadrao(String caminhoDoExe, String localAppData) {
        if (caminhoDoExe == null || caminhoDoExe.isBlank() || localAppData == null || localAppData.isBlank()) {
            return Path.of("data");
        }
        return Path.of(localAppData, "FMC", "data");
    }

    /** Troca o arquivo do banco (usado nos testes para não mexer nos dados reais). */
    public static void usarArquivo(Path novoArquivo) {
        arquivo = novoArquivo;
    }

    /** O arquivo do banco em uso (a importação de planilha faz um ensaio numa cópia dele). */
    public static Path arquivo() {
        return arquivo;
    }

    /** A pasta onde fica o arquivo do banco (ex.: data/). Também guarda a caixa de saída simulada. */
    public static Path pasta() {
        Path pasta = arquivo.toAbsolutePath().getParent();
        return pasta == null ? Path.of(".").toAbsolutePath() : pasta;
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
            Migracoes.aplicar(conexao); // ajustes em bancos criados por versões antigas
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
