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
 * - no programa pronto (instalado ou portátil), na pasta de dados do usuário de cada sistema:
 *   - Windows: %LOCALAPPDATA%\FMC\data (ex.: C:\Users\maria\AppData\Local\FMC\data);
 *   - Mac: ~/Library/Application Support/FMC/data;
 *   - Linux: ~/.local/share/FMC/data (ou $XDG_DATA_HOME/FMC/data, se estiver definido).
 *   O programa instalado fica numa pasta onde o sistema não deixa gravar ("Arquivos de Programas",
 *   "Aplicativos" ou /opt); além disso, assim o atalho e uma versão nova do programa enxergam
 *   os mesmos dados.
 */
public final class Database {

    private static Path arquivo = pastaPadrao(System.getProperty("jpackage.app-path"),
            System.getProperty("os.name", ""), System.getenv("LOCALAPPDATA"),
            System.getProperty("user.home"), System.getenv("XDG_DATA_HOME")).resolve("fmc.db");

    private Database() {
    }

    /**
     * A pasta de dados padrão. O jpackage, que monta o programa pronto, avisa que o sistema está
     * rodando instalado pela propriedade "jpackage.app-path" (o caminho do programa).
     *
     * @param caminhoDoPrograma o valor de jpackage.app-path (null rodando pelo código)
     * @param sistema           o nome do sistema operacional (os.name), ex.: "Windows 11", "Mac OS X", "Linux"
     * @param localAppData      a pasta %LOCALAPPDATA% do Windows
     * @param home              a pasta do usuário (user.home), usada no Mac e no Linux
     * @param xdgDataHome       a variável XDG_DATA_HOME do Linux (normalmente vazia)
     */
    static Path pastaPadrao(String caminhoDoPrograma, String sistema, String localAppData, String home,
            String xdgDataHome) {
        if (vazio(caminhoDoPrograma)) {
            return Path.of("data");
        }
        String os = sistema == null ? "" : sistema.toLowerCase();
        if (os.startsWith("windows")) {
            return vazio(localAppData) ? Path.of("data") : Path.of(localAppData, "FMC", "data");
        }
        if (vazio(home)) {
            return Path.of("data");
        }
        if (os.startsWith("mac")) {
            return Path.of(home, "Library", "Application Support", "FMC", "data");
        }
        // Linux e outros Unix: padrão XDG (~/.local/share)
        return vazio(xdgDataHome) ? Path.of(home, ".local", "share", "FMC", "data")
                : Path.of(xdgDataHome, "FMC", "data");
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
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
