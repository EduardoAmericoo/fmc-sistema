package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FotoVistoria;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.VistoriaRepository;
import br.org.fmc.util.ExifFoto;
import br.org.fmc.util.LaudoVistoriaPdf;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Vistorias com fotos (revisão da SR2, ideia da reunião com o professor).
 *
 * Na entrega das chaves (vistoria de entrada) e na devolução (vistoria de saída), as fotos de cada
 * cômodo ficam guardadas no contrato. Elas servem de prova: "o imóvel foi entregue assim, e está
 * voltando assim". Por isso:
 * - a data e quem registrou são gravados pelo sistema na hora (não dá para mudar);
 * - a data em que a foto foi tirada é lida do próprio arquivo, quando o celular a gravou;
 * - cada foto ganha um lacre (hash SHA-256): se alguém editar o arquivo depois, o sistema mostra;
 * - não existe excluir: foto enviada por engano é arquivada.
 *
 * A foto é endireitada (o celular grava deitada e anota a rotação) e reduzida para no máximo
 * {@value #LADO_MAXIMO} pixels de lado (cerca de 300 KB), e fica em data/documentos/vistorias,
 * que entra na cópia de segurança junto com os outros documentos.
 */
public class ServicoVistorias {

    /** Lado maior da foto guardada: nítida para ver um estrago, leve para guardar milhares. */
    public static final int LADO_MAXIMO = 1600;
    /** Tamanho máximo do arquivo original escolhido. */
    public static final long MAXIMO_BYTES = 25L * 1024 * 1024;

    private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** As fotos de um cômodo, lado a lado: entrada e saída. */
    public record Comodo(String nome, List<FotoVistoria> entrada, List<FotoVistoria> saida) {
    }

    private final VistoriaRepository fotos = new VistoriaRepository();
    private final ContratoRepository contratos = new ContratoRepository();
    private Clock relogio = Clock.systemDefaultZone();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /** O arquivo da foto dentro de data/documentos. */
    public static Path caminho(FotoVistoria f) {
        return ServicoDocumentos.pastaDocumentos().resolve(f.arquivo());
    }

    /**
     * Guarda as fotos escolhidas, todas do mesmo cômodo e da mesma etapa. Confere tudo antes:
     * se uma foto não puder entrar, nenhuma entra e a mensagem diz qual e por quê.
     */
    public List<FotoVistoria> adicionar(long contratoId, EtapaVistoria etapa, String comodo, String observacao,
            List<Path> arquivos) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_VISTORIA);
        contratos.buscarPorId(contratoId).orElseThrow(() -> new RegraNegocioException("Contrato não encontrado."));
        String nomeComodo = comodo == null ? "" : comodo.trim();
        String obs = observacao == null || observacao.isBlank() ? null : observacao.trim();
        List<String> erros = new ArrayList<>();
        if (etapa == null) {
            erros.add("Escolha se é a vistoria de entrada ou de saída.");
        }
        if (nomeComodo.isEmpty()) {
            erros.add("Informe o cômodo das fotos (ex.: Sala, Cozinha, Banheiro).");
        } else if (nomeComodo.length() > 40) {
            erros.add("O nome do cômodo pode ter no máximo 40 caracteres.");
        }
        if (obs != null && obs.length() > 300) {
            erros.add("A observação pode ter no máximo 300 caracteres.");
        }
        if (arquivos == null || arquivos.isEmpty()) {
            erros.add("Escolha pelo menos uma foto.");
        }
        List<BufferedImage> imagens = new ArrayList<>();
        List<ExifFoto.Dados> exifs = new ArrayList<>();
        if (arquivos != null) {
            for (Path arquivo : arquivos) {
                BufferedImage imagem = lerImagem(arquivo, erros);
                if (imagem != null) {
                    ExifFoto.Dados exif = ExifFoto.ler(arquivo);
                    imagens.add(girar(imagem, exif.grausParaGirar()));
                    exifs.add(exif);
                }
            }
        }
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        List<FotoVistoria> salvas = new ArrayList<>();
        List<Path> gravados = new ArrayList<>();
        try {
            for (int i = 0; i < imagens.size(); i++) {
                String relativo = "vistorias/contrato-" + contratoId + "/" + etapa.name().toLowerCase(Locale.ROOT)
                        + "/" + agora.format(NOME) + "-" + (i + 1) + ".jpg";
                Path destino = ServicoDocumentos.pastaDocumentos().resolve(relativo);
                for (int n = 2; Files.exists(destino); n++) { // dois envios no mesmo segundo
                    relativo = relativo.replace(".jpg", "-" + n + ".jpg");
                    destino = ServicoDocumentos.pastaDocumentos().resolve(relativo);
                }
                Files.createDirectories(destino.getParent());
                gravarJpeg(reduzir(imagens.get(i)), destino);
                gravados.add(destino);
                salvas.add(fotos.salvar(new FotoVistoria(null, contratoId, etapa, nomeComodo, obs, relativo,
                        arquivos.get(i).getFileName().toString(), hash(destino), agora, Sessao.usuario().getNome(),
                        exifs.get(i).tiradaEm(), null)));
            }
        } catch (IOException e) {
            gravados.forEach(ServicoVistorias::apagarSemErro);
            throw new RegraNegocioException("Não foi possível guardar as fotos na pasta do sistema (" + e.getMessage()
                    + "). Nada foi gravado.");
        } catch (SQLException | RuntimeException e) {
            gravados.forEach(ServicoVistorias::apagarSemErro);
            throw e;
        }
        return salvas;
    }

    /** As fotos do contrato (ativas e arquivadas), na ordem em que foram registradas. */
    public List<FotoVistoria> listar(long contratoId) throws SQLException {
        Sessao.exigir(Acao.VER_CONTRATOS);
        return fotos.listarPorContrato(contratoId);
    }

    /** Só as ativas de uma etapa. */
    public List<FotoVistoria> ativas(long contratoId, EtapaVistoria etapa) throws SQLException {
        return listar(contratoId).stream().filter(f -> f.etapa() == etapa && !f.arquivada()).toList();
    }

    /** Foto enviada por engano: sai das listas e do laudo, mas continua guardada. */
    public void arquivar(long fotoId) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_VISTORIA);
        fotos.buscarPorId(fotoId).orElseThrow(() -> new RegraNegocioException("Foto não encontrada."));
        fotos.marcarArquivada(fotoId, LocalDateTime.now(relogio));
    }

    public void reativar(long fotoId) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_VISTORIA);
        fotos.buscarPorId(fotoId).orElseThrow(() -> new RegraNegocioException("Foto não encontrada."));
        fotos.marcarArquivada(fotoId, null);
    }

    /** O lacre confere? false se o arquivo foi editado ou sumiu depois do registro. */
    public static boolean lacreConfere(FotoVistoria f) {
        try {
            return f.hashSha256().equals(hash(caminho(f)));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Os cômodos com fotos ativas, cada um com as fotos da entrada e da saída, para comparar.
     * Na ordem da lista de cômodos sugeridos; os outros, depois, na ordem em que apareceram.
     */
    public List<Comodo> comparacao(long contratoId) throws SQLException {
        List<FotoVistoria> ativas = listar(contratoId).stream().filter(f -> !f.arquivada()).toList();
        Map<String, List<FotoVistoria>> porComodo = new LinkedHashMap<>();
        for (String sugerido : FotoVistoria.COMODOS) {
            porComodo.put(sugerido, new ArrayList<>());
        }
        for (FotoVistoria f : ativas) {
            porComodo.computeIfAbsent(f.comodo(), k -> new ArrayList<>()).add(f);
        }
        List<Comodo> lista = new ArrayList<>();
        porComodo.forEach((nome, doComodo) -> {
            if (!doComodo.isEmpty()) {
                lista.add(new Comodo(nome, doComodo.stream().filter(f -> f.etapa() == EtapaVistoria.ENTRADA).toList(),
                        doComodo.stream().filter(f -> f.etapa() == EtapaVistoria.SAIDA).toList()));
            }
        });
        return lista;
    }

    /** O laudo da vistoria em PDF, com as fotos de cada cômodo e espaço para as assinaturas. */
    public void laudo(long contratoId, EtapaVistoria etapa, Path destino) throws SQLException, IOException {
        Sessao.exigir(Acao.VER_CONTRATOS);
        Contrato contrato = contratos.buscarPorId(contratoId)
                .orElseThrow(() -> new RegraNegocioException("Contrato não encontrado."));
        List<FotoVistoria> daEtapa = ativas(contratoId, etapa);
        if (daEtapa.isEmpty()) {
            throw new RegraNegocioException("A " + etapa.getDescricao().toLowerCase() + " ainda não tem fotos.");
        }
        LaudoVistoriaPdf.salvar(contrato, etapa, daEtapa, ServicoVistorias::caminho, LocalDateTime.now(relogio), destino);
    }

    // ---------- imagem ----------

    private static BufferedImage lerImagem(Path arquivo, List<String> erros) {
        String nome = arquivo.getFileName().toString();
        String minusculo = nome.toLowerCase(Locale.ROOT);
        if (minusculo.endsWith(".heic") || minusculo.endsWith(".heif")) {
            erros.add(nome + ": fotos HEIC do iPhone não abrem no sistema. No iPhone, em Ajustes > Câmera > "
                    + "Formatos, escolha \"Mais Compatível\" (JPG), ou envie a foto pelo WhatsApp para o computador.");
            return null;
        }
        try {
            if (!Files.isRegularFile(arquivo)) {
                erros.add(nome + ": arquivo não encontrado (o pen drive ainda está conectado?).");
                return null;
            }
            if (Files.size(arquivo) > MAXIMO_BYTES) {
                erros.add(nome + ": arquivo maior que 25 MB.");
                return null;
            }
            BufferedImage imagem;
            try (InputStream in = Files.newInputStream(arquivo)) {
                imagem = ImageIO.read(in);
            }
            if (imagem == null) {
                erros.add(nome + ": não é uma foto (use JPG ou PNG).");
            }
            return imagem;
        } catch (IOException e) {
            erros.add(nome + ": não foi possível ler a foto (" + e.getMessage() + ").");
            return null;
        }
    }

    /** Gira a foto em 90, 180 ou 270 graus, conforme o celular anotou. */
    static BufferedImage girar(BufferedImage imagem, int graus) {
        if (graus == 0) {
            return imagem;
        }
        boolean deitada = graus == 90 || graus == 270;
        int largura = deitada ? imagem.getHeight() : imagem.getWidth();
        int altura = deitada ? imagem.getWidth() : imagem.getHeight();
        BufferedImage girada = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = girada.createGraphics();
        AffineTransform t = new AffineTransform();
        t.translate(largura / 2.0, altura / 2.0);
        t.rotate(Math.toRadians(graus));
        t.translate(-imagem.getWidth() / 2.0, -imagem.getHeight() / 2.0);
        g.drawImage(imagem, t, null);
        g.dispose();
        return girada;
    }

    /** Reduz para no máximo LADO_MAXIMO de lado e tira a transparência (JPG não tem). */
    static BufferedImage reduzir(BufferedImage imagem) {
        double escala = Math.min(1.0, (double) LADO_MAXIMO / Math.max(imagem.getWidth(), imagem.getHeight()));
        int largura = Math.max(1, (int) Math.round(imagem.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(imagem.getHeight() * escala));
        BufferedImage nova = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nova.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, largura, altura);
        g.drawImage(imagem, 0, 0, largura, altura, null);
        g.dispose();
        return nova;
    }

    private static void gravarJpeg(BufferedImage imagem, Path destino) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(0.85f);
        try (ImageOutputStream saida = ImageIO.createImageOutputStream(destino.toFile())) {
            escritor.setOutput(saida);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } finally {
            escritor.dispose();
        }
    }

    /** SHA-256 do arquivo, em hexadecimal: o lacre da foto. */
    static String hash(Path arquivo) throws IOException {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(Files.readAllBytes(arquivo)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private static void apagarSemErro(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            // o arquivo solto não atrapalha: não está registrado em nenhuma vistoria
        }
    }
}
