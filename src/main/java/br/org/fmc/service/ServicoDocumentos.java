package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.DocumentoContrato;
import br.org.fmc.model.TipoDocumento;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.DocumentoRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Documentos digitalizados do contrato: contrato assinado, RG/CPF, comprovante de renda,
 * documentos do fiador... (resolve o "papel perdido" das pastas físicas).
 *
 * Regras:
 * - só PDF, JPG, JPEG e PNG, com até 10 MB;
 * - tipo e data do documento são obrigatórios, e a data não pode ser no futuro;
 * - o arquivo é COPIADO para data/documentos (pasta do contrato): se o original for apagado
 *   ou estiver num pendrive, o documento continua abrindo;
 * - não existe excluir (decisão da equipe com o professor): o documento enviado errado é
 *   arquivado, some da lista e pode ser reativado.
 */
public class ServicoDocumentos {

    public static final Set<String> EXTENSOES = Set.of("pdf", "jpg", "jpeg", "png");
    public static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    public static final String PASTA = "documentos";

    private final DocumentoRepository documentos = new DocumentoRepository();
    private final ContratoRepository contratos = new ContratoRepository();
    private Clock relogio = Clock.systemDefaultZone();

    /** Para os testes: fixa o "hoje" num dia conhecido. */
    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /** Pasta onde ficam as cópias: data/documentos (ao lado do banco). */
    public static Path pastaDocumentos() {
        return Database.pastaDados().resolve(PASTA);
    }

    /**
     * Copia o arquivo para dentro do sistema e registra o documento no contrato.
     *
     * @param origem     o arquivo escolhido (pode estar num pendrive)
     * @param observacao opcional
     */
    public DocumentoContrato enviar(long contratoId, TipoDocumento tipo, LocalDate data, Path origem,
            String observacao) throws SQLException {
        Sessao.exigir(Acao.ANEXAR_DOCUMENTOS);
        Contrato contrato = contratos.buscarPorId(contratoId)
                .orElseThrow(() -> new RegraNegocioException("Contrato não encontrado."));

        List<String> erros = new ArrayList<>();
        if (tipo == null) {
            erros.add("Escolha o tipo de documento.");
        }
        if (data == null) {
            erros.add("Informe a data do documento.");
        } else if (data.isAfter(hoje())) {
            erros.add("A data do documento não pode ser no futuro.");
        }
        long tamanho = conferirArquivo(origem, erros);
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        String nomeOriginal = origem.getFileName().toString();
        String relativo = caminhoLivre(contrato, nomeOriginal);
        Path destino = pastaDocumentos().resolve(relativo);
        try {
            Files.createDirectories(destino.getParent());
            Files.copy(origem, destino);
        } catch (IOException e) {
            throw new RegraNegocioException("Não foi possível copiar o arquivo para a pasta do sistema ("
                    + e.getMessage() + "). Confira se o arquivo ainda está lá (ex.: pendrive conectado).");
        }
        try {
            return documentos.salvar(new DocumentoContrato(null, contratoId, tipo, data, relativo, nomeOriginal,
                    tamanho, observacao, LocalDateTime.now(relogio), Sessao.usuario().getNome(), null));
        } catch (SQLException | RuntimeException e) {
            apagarCopia(destino); // não deixa arquivo solto na pasta se não deu para registrar
            throw e;
        }
    }

    /** Documentos do contrato (ativos e arquivados), do mais recente para o mais antigo. */
    public List<DocumentoContrato> listar(long contratoId) throws SQLException {
        Sessao.exigir(Acao.VER_CONTRATOS);
        return documentos.listarPorContrato(contratoId);
    }

    /** Caminho da cópia guardada no sistema, para abrir no programa padrão do computador. */
    public Path arquivoParaAbrir(long documentoId) throws SQLException {
        Sessao.exigir(Acao.VER_CONTRATOS);
        DocumentoContrato doc = buscar(documentoId);
        Path caminho = dentroDaPasta(doc.arquivo());
        if (!Files.isRegularFile(caminho)) {
            throw new RegraNegocioException("O arquivo deste documento não foi encontrado na pasta "
                    + pastaDocumentos() + ". Ele pode ter sido apagado ou movido fora do sistema.");
        }
        return caminho;
    }

    /** Tira o documento da lista (ex.: enviado errado). O arquivo continua guardado. */
    public void arquivar(long documentoId) throws SQLException {
        Sessao.exigir(Acao.ANEXAR_DOCUMENTOS);
        if (buscar(documentoId).arquivado()) {
            throw new RegraNegocioException("Este documento já está arquivado.");
        }
        documentos.marcarArquivado(documentoId, LocalDateTime.now(relogio));
    }

    /** Volta o documento arquivado para a lista. */
    public void reativar(long documentoId) throws SQLException {
        Sessao.exigir(Acao.ANEXAR_DOCUMENTOS);
        if (!buscar(documentoId).arquivado()) {
            throw new RegraNegocioException("Este documento não está arquivado.");
        }
        documentos.marcarArquivado(documentoId, null);
    }

    // ---------- regras do arquivo ----------

    /** Confere existência, formato e tamanho; devolve o tamanho em bytes (ou -1 se houve erro). */
    private static long conferirArquivo(Path origem, List<String> erros) {
        if (origem == null) {
            erros.add("Escolha o arquivo do documento (PDF ou foto).");
            return -1;
        }
        if (!Files.isRegularFile(origem)) {
            erros.add("O arquivo escolhido não foi encontrado. Se ele está num pendrive, confira se o pendrive "
                    + "está conectado.");
            return -1;
        }
        String extensao = extensao(origem.getFileName().toString());
        if (!EXTENSOES.contains(extensao)) {
            erros.add("O arquivo \"" + origem.getFileName() + "\" não é aceito. Envie um PDF (do scanner) "
                    + "ou uma foto JPG, JPEG ou PNG (do celular).");
            return -1;
        }
        long tamanho;
        try {
            tamanho = Files.size(origem);
        } catch (IOException e) {
            erros.add("Não foi possível ler o arquivo escolhido.");
            return -1;
        }
        if (tamanho == 0) {
            erros.add("O arquivo escolhido está vazio.");
        } else if (tamanho > TAMANHO_MAXIMO) {
            erros.add("O arquivo tem " + megas(tamanho) + " e o limite é 10 MB. Digitalize com qualidade menor "
                    + "ou divida o documento em partes.");
        }
        return tamanho;
    }

    static String extensao(String nome) {
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    /** Tamanho legível, ex.: "12,4 MB" ou "350 KB". */
    public static String megas(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(Locale.of("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024));
    }

    // ---------- onde a cópia fica ----------

    /**
     * Caminho (relativo a data/documentos) ainda não usado: "CT-1001/rg-maria.pdf".
     * Se o nome já existe, acrescenta "-2", "-3"... para nunca sobrescrever outro documento.
     */
    private String caminhoLivre(Contrato contrato, String nomeOriginal) throws SQLException {
        String pasta = seguro(contrato.getNumero() != null ? contrato.getNumero() : "contrato-" + contrato.getId());
        String extensao = extensao(nomeOriginal);
        String base = seguro(nomeOriginal.substring(0, nomeOriginal.length() - extensao.length() - 1));
        if (base.isEmpty()) {
            base = "documento";
        }
        for (int n = 1; ; n++) {
            String relativo = pasta + "/" + base + (n == 1 ? "" : "-" + n) + "." + extensao;
            if (!Files.exists(pastaDocumentos().resolve(relativo)) && !documentos.arquivoEmUso(relativo)) {
                return relativo;
            }
        }
    }

    /** Tira acentos e caracteres que dão problema em nome de arquivo (no Windows ou no pendrive). */
    static String seguro(String nome) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpo = semAcento.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        limpo = limpo.replaceAll("^[-.]+|[-.]+$", "");
        return limpo.length() > 80 ? limpo.substring(0, 80) : limpo;
    }

    /** Resolve o caminho guardado no banco, sem deixar sair da pasta de documentos. */
    private static Path dentroDaPasta(String relativo) {
        Path pasta = pastaDocumentos().toAbsolutePath().normalize();
        Path caminho = pasta.resolve(relativo).normalize();
        if (!caminho.startsWith(pasta)) {
            throw new RegraNegocioException("Caminho de documento inválido.");
        }
        return caminho;
    }

    private DocumentoContrato buscar(long documentoId) throws SQLException {
        return documentos.buscarPorId(documentoId)
                .orElseThrow(() -> new RegraNegocioException("Documento não encontrado."));
    }

    private static void apagarCopia(Path destino) {
        try {
            Files.deleteIfExists(destino);
        } catch (IOException ignorado) {
            // fica só um arquivo a mais na pasta; o erro original é o que importa
        }
    }
}
