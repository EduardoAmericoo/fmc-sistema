package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Cópias de segurança do banco de dados (revisão da SR2).
 *
 * Todo o sistema fica num arquivo só (data/fmc.db). Se o computador estragar ou o arquivo
 * for apagado, perde-se tudo. Por isso:
 * - ao fechar o sistema, ele faz sozinho a cópia do dia (uma por dia, guarda os últimos 30 dias);
 * - o Administrador pode fazer uma cópia na hora e salvar uma cópia num pen drive ou outra pasta;
 * - se precisar, restaura uma cópia (antes, o sistema guarda como estava, para poder voltar atrás).
 *
 * As cópias ficam em data/copias. A cópia é feita pelo próprio SQLite (VACUUM INTO), que gera
 * um arquivo completo e consistente mesmo com o sistema aberto.
 */
public class ServicoCopiaSeguranca {

    /** Quantas cópias automáticas (uma por dia) são guardadas. As mais antigas são apagadas. */
    public static final int DIAS_GUARDADOS = 30;

    private static final String PREFIXO = "fmc-";
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /** Por que a cópia foi feita (fica no nome do arquivo). */
    public enum Tipo {
        AUTOMATICA("automatica", "Automática (ao fechar)"),
        MANUAL("manual", "Feita na hora"),
        ANTES_DE_RESTAURAR("antes-de-restaurar", "Antes de restaurar");

        private final String sufixo;
        private final String descricao;

        Tipo(String sufixo, String descricao) {
            this.sufixo = sufixo;
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        static Optional<Tipo> doArquivo(String nome) {
            for (Tipo t : values()) {
                if (nome.endsWith("-" + t.sufixo + ".db")) {
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }
    }

    /** Uma cópia guardada em data/copias. */
    public record Copia(Path arquivo, Tipo tipo, LocalDateTime feitaEm, long bytes) {
        public String tamanho() {
            return bytes < 1024 * 1024 ? Math.max(1, bytes / 1024) + " KB"
                    : String.format("%.1f MB", bytes / (1024.0 * 1024.0)).replace('.', ',');
        }
    }

    /** A pasta das cópias (data/copias, ao lado do banco). */
    public Path pasta() {
        return Database.pasta().resolve("copias");
    }

    /** As cópias guardadas, da mais recente para a mais antiga. */
    public List<Copia> listar() throws IOException {
        Path pasta = pasta();
        if (!Files.isDirectory(pasta)) {
            return List.of();
        }
        List<Copia> copias = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(pasta)) {
            for (Path arquivo : arquivos.toList()) {
                String nome = arquivo.getFileName().toString();
                Optional<Tipo> tipo = Tipo.doArquivo(nome);
                if (nome.startsWith(PREFIXO) && tipo.isPresent()) {
                    LocalDateTime quando = LocalDateTime.ofInstant(Files.getLastModifiedTime(arquivo).toInstant(),
                            ZoneId.systemDefault());
                    copias.add(new Copia(arquivo, tipo.get(), quando, Files.size(arquivo)));
                }
            }
        }
        copias.sort(Comparator.comparing(Copia::feitaEm).reversed());
        return copias;
    }

    /** A cópia mais recente, se houver (mostrada na tela e no rodapé do menu). */
    public Optional<Copia> ultima() throws IOException {
        return listar().stream().findFirst();
    }

    /**
     * A cópia automática do dia, feita ao fechar o sistema. Não pede permissão: protege os dados
     * de qualquer jeito. Se já houver a cópia de hoje, ela é trocada pela mais nova. Depois apaga
     * as cópias automáticas além dos últimos {@value #DIAS_GUARDADOS} dias.
     */
    public Path copiaAutomatica(LocalDate hoje) throws IOException, SQLException {
        Path destino = pasta().resolve(PREFIXO + hoje.format(FORMATO_DIA) + "-" + Tipo.AUTOMATICA.sufixo + ".db");
        copiarBanco(destino);
        apagarAutomaticasAntigas();
        return destino;
    }

    /** Cópia feita na hora pelo botão "Fazer cópia agora". */
    public Copia fazerAgora() throws IOException, SQLException {
        Sessao.exigir(Acao.FAZER_COPIA_SEGURANCA);
        Path destino = novoArquivo(Tipo.MANUAL);
        copiarBanco(destino);
        return new Copia(destino, Tipo.MANUAL, LocalDateTime.now(), Files.size(destino));
    }

    /** Salva uma cópia fora do computador (pen drive, HD externo, pasta na nuvem...). */
    public void salvarEm(Path destino) throws IOException, SQLException {
        Sessao.exigir(Acao.FAZER_COPIA_SEGURANCA);
        if (Files.exists(destino)) {
            Files.delete(destino); // o VACUUM INTO não sobrescreve; a pessoa já confirmou ao escolher o nome
        }
        copiarBanco(destino);
    }

    /**
     * Volta o sistema para o que estava na cópia. Antes, guarda o banco atual como
     * "antes de restaurar" (dá para desfazer restaurando essa cópia). Quem estiver logado
     * precisa entrar de novo, porque os usuários e senhas também voltam.
     *
     * @return a cópia "antes de restaurar" que foi feita
     */
    public Copia restaurar(Path copia) throws IOException, SQLException {
        Sessao.exigir(Acao.RESTAURAR_COPIA_SEGURANCA);
        conferirQueEhDoSistema(copia);
        Path antes = novoArquivo(Tipo.ANTES_DE_RESTAURAR);
        copiarBanco(antes);
        Path banco = Database.arquivo();
        Path temporario = banco.resolveSibling(banco.getFileName() + ".restaurando");
        Files.copy(copia, temporario, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temporario, banco, StandardCopyOption.REPLACE_EXISTING);
        Database.inicializar(); // uma cópia antiga ganha as tabelas novas desta versão
        return new Copia(antes, Tipo.ANTES_DE_RESTAURAR, LocalDateTime.now(), Files.size(antes));
    }

    /** Recusa arquivos que não são um banco do Sistema FMC (ex.: outro .db qualquer escolhido por engano). */
    void conferirQueEhDoSistema(Path arquivo) throws IOException {
        if (!Files.isRegularFile(arquivo)) {
            throw new RegraNegocioException("O arquivo da cópia não foi encontrado: " + arquivo.getFileName());
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo.toAbsolutePath() + "?open_mode=1");
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' "
                        + "AND name IN ('usuario', 'imovel', 'inquilino', 'contrato')")) {
            if (!rs.next() || rs.getInt(1) < 4) {
                throw new RegraNegocioException(naoEhCopia(arquivo));
            }
        } catch (SQLException e) {
            throw new RegraNegocioException(naoEhCopia(arquivo));
        }
    }

    private static String naoEhCopia(Path arquivo) {
        return "O arquivo " + arquivo.getFileName() + " não é uma cópia do Sistema FMC. Nada foi alterado.";
    }

    /** Copia o banco em uso para o destino, pelo próprio SQLite (arquivo completo e consistente). */
    private void copiarBanco(Path destino) throws IOException, SQLException {
        Path pastaDestino = destino.toAbsolutePath().getParent();
        if (pastaDestino != null) {
            Files.createDirectories(pastaDestino);
        }
        Path temporario = destino.resolveSibling(destino.getFileName() + ".parcial");
        Files.deleteIfExists(temporario);
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            st.execute("VACUUM INTO '" + temporario.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path novoArquivo(Tipo tipo) {
        String base = PREFIXO + LocalDateTime.now().format(FORMATO_HORA) + "-" + tipo.sufixo;
        Path arquivo = pasta().resolve(base + ".db");
        for (int i = 2; Files.exists(arquivo); i++) { // duas cópias no mesmo segundo
            arquivo = pasta().resolve(base.replace("-" + tipo.sufixo, "-" + i + "-" + tipo.sufixo) + ".db");
        }
        return arquivo;
    }

    private void apagarAutomaticasAntigas() throws IOException {
        List<Copia> automaticas = listar().stream().filter(c -> c.tipo() == Tipo.AUTOMATICA)
                .sorted(Comparator.comparing((Copia c) -> c.arquivo().getFileName().toString()).reversed())
                .toList();
        for (int i = DIAS_GUARDADOS; i < automaticas.size(); i++) {
            Files.deleteIfExists(automaticas.get(i).arquivo());
        }
    }
}
