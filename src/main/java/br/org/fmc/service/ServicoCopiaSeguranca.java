package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Cópias de segurança do sistema (revisão da SR2).
 *
 * Os dados ficam em dois lugares: o banco (data/fmc.db) e a pasta dos documentos anexados aos
 * contratos (data/documentos). Se o computador estragar ou algo for apagado, perde-se tudo. Por isso:
 * - ao abrir (se ainda não houver a de hoje) e ao fechar o sistema, ele faz sozinho a cópia do dia
 *   (uma por dia, guarda os últimos 30 dias);
 * - o Administrador pode fazer uma cópia na hora e salvar uma cópia completa num pen drive (.zip
 *   com o banco e os documentos);
 * - se precisar, restaura uma cópia (antes, o sistema guarda como estava, para poder voltar atrás).
 *
 * As cópias do banco ficam em data/copias. Os documentos nunca são apagados (só arquivados), então
 * não precisam ser copiados de novo a cada dia: ficam numa pasta espelho, data/copias/documentos,
 * que recebe só os arquivos novos. A cópia do banco é feita pelo próprio SQLite (VACUUM INTO), que
 * gera um arquivo completo e consistente mesmo com o sistema aberto.
 */
public class ServicoCopiaSeguranca {

    /** Quantas cópias automáticas (uma por dia) são guardadas. As mais antigas são apagadas. */
    public static final int DIAS_GUARDADOS = 30;

    /** Nome do banco dentro da cópia completa (.zip) e da pasta dos documentos dentro dela. */
    static final String BANCO_NO_ZIP = "fmc.db";
    static final String DOCUMENTOS_NO_ZIP = ServicoDocumentos.PASTA + "/";

    private static final String PREFIXO = "fmc-";
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /** Como a cópia foi feita (fica no nome do arquivo e na coluna "Tipo de cópia"). */
    public enum Tipo {
        AUTOMATICA("automatica", "Automática"),
        MANUAL("manual", "Manual"),
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

    /** A pasta espelho dos documentos (data/copias/documentos). */
    public Path pastaEspelhoDocumentos() {
        return pasta().resolve(ServicoDocumentos.PASTA);
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

    /** A cópia mais recente, se houver (mostrada na tela). */
    public Optional<Copia> ultima() throws IOException {
        return listar().stream().findFirst();
    }

    /**
     * A cópia automática do dia, feita ao fechar o sistema. Não pede permissão: protege os dados
     * de qualquer jeito. Se já houver a cópia de hoje, ela é trocada pela mais nova. Depois apaga
     * as cópias automáticas além dos últimos {@value #DIAS_GUARDADOS} dias.
     */
    public Path copiaAutomatica(LocalDate hoje) throws IOException, SQLException {
        Path destino = arquivoAutomatico(hoje);
        copiarBanco(destino);
        espelharDocumentos();
        apagarAutomaticasAntigas();
        return destino;
    }

    /**
     * Ao abrir o sistema: faz a cópia do dia só se ainda não houver uma. Cobre os dias em que o
     * sistema não foi fechado direito (queda de energia, computador desligado com ele aberto).
     *
     * @return true se fez a cópia agora
     */
    public boolean copiaAutomaticaSeFaltar(LocalDate hoje) throws IOException, SQLException {
        if (Files.exists(arquivoAutomatico(hoje))) {
            return false;
        }
        copiaAutomatica(hoje);
        return true;
    }

    /** Cópia feita na hora pelo botão "Fazer cópia agora" (tipo Manual). */
    public Copia fazerAgora() throws IOException, SQLException {
        Sessao.exigir(Acao.FAZER_COPIA_SEGURANCA);
        Path destino = novoArquivo(Tipo.MANUAL);
        copiarBanco(destino);
        espelharDocumentos();
        return new Copia(destino, Tipo.MANUAL, LocalDateTime.now(), Files.size(destino));
    }

    /**
     * Salva uma cópia completa fora do computador (pen drive, HD externo, pasta na nuvem): um
     * arquivo .zip com o banco e todos os documentos. Um destino .db salva só o banco (formato antigo).
     */
    public void salvarEm(Path destino) throws IOException, SQLException {
        Sessao.exigir(Acao.FAZER_COPIA_SEGURANCA);
        Path pastaDestino = destino.toAbsolutePath().getParent();
        if (pastaDestino != null) {
            Files.createDirectories(pastaDestino);
        }
        if (!ehZip(destino)) {
            Files.deleteIfExists(destino); // o VACUUM INTO não sobrescreve; a pessoa já confirmou ao escolher o nome
            copiarBanco(destino);
            return;
        }
        Path banco = Files.createTempFile("fmc-copia", ".db");
        Path temporario = destino.resolveSibling(destino.getFileName() + ".parcial");
        try {
            Files.delete(banco);
            copiarBanco(banco);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporario))) {
                adicionar(zip, BANCO_NO_ZIP, banco);
                Path documentos = ServicoDocumentos.pastaDocumentos();
                for (Path arquivo : arquivosDe(documentos)) {
                    adicionar(zip, DOCUMENTOS_NO_ZIP + relativo(documentos, arquivo), arquivo);
                }
            }
            Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(banco);
            Files.deleteIfExists(temporario);
        }
    }

    /**
     * Volta o sistema para o que estava na cópia: uma cópia do banco (.db, da lista ou de um pen
     * drive antigo) ou uma cópia completa (.zip, com os documentos). Antes, guarda o banco atual como
     * "antes de restaurar" (dá para desfazer restaurando essa cópia). Os documentos que faltarem
     * voltam para data/documentos. Quem estiver logado precisa entrar de novo, porque os usuários
     * e senhas também voltam.
     *
     * @return a cópia "antes de restaurar" que foi feita
     */
    public Copia restaurar(Path copia) throws IOException, SQLException {
        Sessao.exigir(Acao.RESTAURAR_COPIA_SEGURANCA);
        if (!Files.isRegularFile(copia)) {
            throw new RegraNegocioException("O arquivo da cópia não foi encontrado: " + copia.getFileName());
        }
        Path pastaTemporaria = null;
        Path banco = copia;
        try {
            if (ehZip(copia)) {
                pastaTemporaria = Files.createTempDirectory("fmc-restaurar");
                extrair(copia, pastaTemporaria);
                banco = pastaTemporaria.resolve(BANCO_NO_ZIP);
                if (!Files.isRegularFile(banco)) {
                    throw new RegraNegocioException(naoEhCopia(copia));
                }
            }
            conferirQueEhDoSistema(banco, copia);
            Path antes = novoArquivo(Tipo.ANTES_DE_RESTAURAR);
            copiarBanco(antes);
            espelharDocumentos();
            Path atual = Database.arquivo();
            Path temporario = atual.resolveSibling(atual.getFileName() + ".restaurando");
            Files.copy(banco, temporario, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporario, atual, StandardCopyOption.REPLACE_EXISTING);
            Database.inicializar(); // uma cópia antiga ganha as tabelas novas desta versão
            Path documentos = ServicoDocumentos.pastaDocumentos();
            completar(pastaEspelhoDocumentos(), documentos);
            if (pastaTemporaria != null) {
                completar(pastaTemporaria.resolve(ServicoDocumentos.PASTA), documentos);
            }
            return new Copia(antes, Tipo.ANTES_DE_RESTAURAR, LocalDateTime.now(), Files.size(antes));
        } finally {
            if (pastaTemporaria != null) {
                apagarPasta(pastaTemporaria);
            }
        }
    }

    /** Recusa arquivos que não são um banco do Sistema FMC (ex.: outro .db qualquer escolhido por engano). */
    void conferirQueEhDoSistema(Path arquivo) throws IOException {
        conferirQueEhDoSistema(arquivo, arquivo);
    }

    private void conferirQueEhDoSistema(Path banco, Path escolhido) throws IOException {
        if (!Files.isRegularFile(banco)) {
            throw new RegraNegocioException("O arquivo da cópia não foi encontrado: " + escolhido.getFileName());
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + banco.toAbsolutePath() + "?open_mode=1");
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' "
                        + "AND name IN ('usuario', 'imovel', 'inquilino', 'contrato')")) {
            if (!rs.next() || rs.getInt(1) < 4) {
                throw new RegraNegocioException(naoEhCopia(escolhido));
            }
        } catch (SQLException e) {
            throw new RegraNegocioException(naoEhCopia(escolhido));
        }
    }

    private static String naoEhCopia(Path arquivo) {
        return "O arquivo " + arquivo.getFileName() + " não é uma cópia do Sistema FMC. Nada foi alterado.";
    }

    // ---------- banco ----------

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

    private Path arquivoAutomatico(LocalDate dia) {
        return pasta().resolve(PREFIXO + dia.format(FORMATO_DIA) + "-" + Tipo.AUTOMATICA.sufixo + ".db");
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

    // ---------- documentos ----------

    /** Copia para a pasta espelho os documentos que ainda não estão lá (nenhum é apagado ou trocado). */
    void espelharDocumentos() throws IOException {
        completar(ServicoDocumentos.pastaDocumentos(), pastaEspelhoDocumentos());
    }

    /** Copia de origem para destino os arquivos que faltam no destino, mantendo as subpastas. */
    private static void completar(Path origem, Path destino) throws IOException {
        for (Path arquivo : arquivosDe(origem)) {
            Path alvo = destino.resolve(relativo(origem, arquivo));
            if (!Files.exists(alvo)) {
                Files.createDirectories(alvo.getParent());
                Files.copy(arquivo, alvo);
            }
        }
    }

    private static List<Path> arquivosDe(Path pasta) throws IOException {
        if (!Files.isDirectory(pasta)) {
            return List.of();
        }
        try (Stream<Path> todos = Files.walk(pasta)) {
            return todos.filter(Files::isRegularFile).toList();
        }
    }

    /** O caminho do arquivo dentro da pasta, sempre com "/" (igual em qualquer computador e no .zip). */
    private static String relativo(Path pasta, Path arquivo) {
        return pasta.relativize(arquivo).toString().replace('\\', '/');
    }

    // ---------- .zip ----------

    private static boolean ehZip(Path arquivo) {
        return arquivo.getFileName().toString().toLowerCase().endsWith(".zip");
    }

    private static void adicionar(ZipOutputStream zip, String nome, Path arquivo) throws IOException {
        zip.putNextEntry(new ZipEntry(nome));
        Files.copy(arquivo, zip);
        zip.closeEntry();
    }

    /** Extrai o .zip na pasta, recusando nomes que tentariam sair dela (proteção contra arquivo malfeito). */
    private static void extrair(Path zip, Path pasta) throws IOException {
        Path raiz = pasta.toAbsolutePath().normalize();
        try (InputStream entrada = Files.newInputStream(zip); ZipInputStream leitor = new ZipInputStream(entrada)) {
            for (ZipEntry item = leitor.getNextEntry(); item != null; item = leitor.getNextEntry()) {
                Path alvo = raiz.resolve(item.getName()).normalize();
                if (!alvo.startsWith(raiz)) {
                    throw new RegraNegocioException(naoEhCopia(zip));
                }
                if (item.isDirectory()) {
                    Files.createDirectories(alvo);
                } else {
                    Files.createDirectories(alvo.getParent());
                    try (OutputStream saida = Files.newOutputStream(alvo)) {
                        leitor.transferTo(saida);
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new RegraNegocioException(naoEhCopia(zip));
        }
    }

    private static void apagarPasta(Path pasta) {
        try (Stream<Path> todos = Files.walk(pasta)) {
            for (Path p : todos.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            // pasta temporária: se não der para apagar agora, o Windows limpa depois
        }
    }
}
