package br.org.fmc.util;

import br.org.fmc.model.Contrato;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FotoVistoria;
import br.org.fmc.model.Imovel;
import java.util.function.Function;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Laudo de vistoria em PDF (A4): cabeçalho com o contrato, as fotos de cada cômodo (duas por
 * linha) com a observação, a data do registro, quem registrou e o lacre, e no fim as linhas de
 * assinatura da Fundação e do locatário. Para imprimir e assinar na entrega e na devolução das chaves.
 */
public final class LaudoVistoriaPdf {

    private static final float MARGEM = 40;
    private static final float ESPACO = 14;
    private static final float ALTURA_FOTO = 190;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Color AZUL = new Color(0x1D, 0x4D, 0x7A);
    private static final Color CINZA = new Color(0x5B, 0x64, 0x72);

    private PDDocument documento;
    private Function<FotoVistoria, Path> caminho;
    private PDFont normal;
    private PDFont negrito;
    private PDPageContentStream c;
    private float y;
    private float largura;

    private LaudoVistoriaPdf() {
    }

    public static void salvar(Contrato contrato, EtapaVistoria etapa, List<FotoVistoria> fotos,
            Function<FotoVistoria, Path> caminho, LocalDateTime geradoEm,
            Path destino) throws IOException {
        LaudoVistoriaPdf laudo = new LaudoVistoriaPdf();
        laudo.caminho = caminho;
        laudo.gerar(contrato, etapa, fotos, geradoEm, destino);
    }

    private void gerar(Contrato contrato, EtapaVistoria etapa, List<FotoVistoria> fotos, LocalDateTime geradoEm,
            Path destino) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            documento = doc;
            normal = fonte("PlusJakartaSans-Regular.ttf");
            negrito = fonte("PlusJakartaSans-Bold.ttf");
            novaPagina();
            cabecalho(contrato, etapa, fotos.size(), geradoEm);

            Map<String, List<FotoVistoria>> porComodo = new LinkedHashMap<>();
            for (String sugerido : FotoVistoria.COMODOS) {
                porComodo.put(sugerido, new ArrayList<>());
            }
            for (FotoVistoria f : fotos) {
                porComodo.computeIfAbsent(f.comodo(), k -> new ArrayList<>()).add(f);
            }
            for (Map.Entry<String, List<FotoVistoria>> comodo : porComodo.entrySet()) {
                if (!comodo.getValue().isEmpty()) {
                    secaoComodo(comodo.getKey(), comodo.getValue());
                }
            }
            assinaturas(contrato, etapa);
            c.close();
            documento.getDocumentInformation().setTitle(etapa.getDescricao() + " · contrato " + contrato.getNumero());
            documento.save(destino.toFile());
        }
    }

    private void cabecalho(Contrato contrato, EtapaVistoria etapa, int quantidade, LocalDateTime geradoEm)
            throws IOException {
        texto("FUNDAÇÃO MANUEL CRUZ", negrito, 10, MARGEM, AZUL);
        y -= 22;
        texto("Laudo de " + etapa.getDescricao().toLowerCase(), negrito, 18, MARGEM,
                Color.BLACK);
        y -= 20;
        Imovel imovel = contrato.getImovel();
        texto("Contrato " + contrato.getNumero() + " · " + imovel.getCodigo()
                + (imovel.getUnidade() == null || imovel.getUnidade().isBlank() ? "" : " · " + imovel.getUnidade()),
                normal, 10.5f, MARGEM, Color.BLACK);
        y -= 15;
        texto(imovel.getEndereco() + (imovel.getBairro() == null || imovel.getBairro().isBlank() ? ""
                : " · " + imovel.getBairro()), normal, 10.5f, MARGEM, Color.BLACK);
        y -= 15;
        texto("Locatário: " + contrato.getInquilino().getNome(), normal, 10.5f, MARGEM, Color.BLACK);
        y -= 15;
        texto(etapa.getExplicacao() + " · " + quantidade + (quantidade == 1 ? " foto" : " fotos")
                + " · gerado em " + geradoEm.format(DATA_HORA) + " pelo Sistema FMC", normal, 9, MARGEM, CINZA);
        y -= 10;
        linha(MARGEM, y, MARGEM + largura, y);
        y -= 18;
    }

    private void secaoComodo(String nome, List<FotoVistoria> fotos) throws IOException {
        precisa(24 + ALTURA_FOTO + 50);
        texto(nome, negrito, 13, MARGEM, AZUL);
        y -= 12;
        float coluna = (largura - ESPACO) / 2;
        for (int i = 0; i < fotos.size(); i += 2) {
            precisa(ALTURA_FOTO + 52);
            float topo = y;
            float menor = topo;
            for (int k = 0; k < 2 && i + k < fotos.size(); k++) {
                float x = MARGEM + k * (coluna + ESPACO);
                menor = Math.min(menor, foto(fotos.get(i + k), x, topo, coluna));
            }
            y = menor - 14;
        }
        y -= 6;
    }

    /** Desenha uma foto com a legenda embaixo; devolve o y do fim da legenda. */
    private float foto(FotoVistoria f, float x, float topo, float coluna) throws IOException {
        PDImageXObject imagem = PDImageXObject.createFromFile(caminho.apply(f).toString(), documento);
        float escala = Math.min(coluna / imagem.getWidth(), ALTURA_FOTO / imagem.getHeight());
        float w = imagem.getWidth() * escala;
        float h = imagem.getHeight() * escala;
        c.drawImage(imagem, x, topo - h, w, h);
        float base = topo - h - 11;
        if (f.observacao() != null) {
            base = escrever(cortar(f.observacao(), normal, 8.5f, coluna), normal, 8.5f, x, base, Color.BLACK);
        }
        String quando = "Registrada em " + f.registradaEm().format(DATA_HORA) + " por " + f.registradaPor();
        base = escrever(cortar(quando, normal, 7.5f, coluna), normal, 7.5f, x, base, CINZA);
        if (f.tiradaEm() != null) {
            base = escrever("Tirada em " + f.tiradaEm().format(DATA_HORA), normal, 7.5f, x, base, CINZA);
        }
        return escrever("Lacre " + f.hashSha256().substring(0, 16), normal, 7, x, base, CINZA);
    }

    private void assinaturas(Contrato contrato, EtapaVistoria etapa) throws IOException {
        precisa(150);
        y -= 10;
        String declaracao = etapa == EtapaVistoria.ENTRADA
                ? "As partes declaram que o imóvel foi entregue no estado registrado nas fotos acima."
                : "As partes declaram que o imóvel foi devolvido no estado registrado nas fotos acima.";
        texto(declaracao, normal, 10, MARGEM, Color.BLACK);
        y -= 26;
        texto("Aracaju, ____ de ________________ de ________", normal, 10, MARGEM, Color.BLACK);
        y -= 60;
        float coluna = (largura - 40) / 2;
        linha(MARGEM, y, MARGEM + coluna, y);
        linha(MARGEM + coluna + 40, y, MARGEM + largura, y);
        y -= 13;
        texto("Fundação Manuel Cruz", normal, 9, MARGEM, Color.BLACK);
        texto("Locatário: " + contrato.getInquilino().getNome(), normal, 9, MARGEM + coluna + 40, Color.BLACK);
    }

    // ---------- desenho ----------

    private void novaPagina() throws IOException {
        if (c != null) {
            c.close();
        }
        PDPage pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);
        c = new PDPageContentStream(documento, pagina);
        largura = pagina.getMediaBox().getWidth() - MARGEM * 2;
        y = pagina.getMediaBox().getHeight() - MARGEM;
    }

    /** Começa uma página nova se não couber a altura pedida. */
    private void precisa(float altura) throws IOException {
        if (y - altura < MARGEM) {
            novaPagina();
        }
    }

    private void texto(String s, PDFont fonte, float tamanho, float x, Color cor) throws IOException {
        c.beginText();
        c.setFont(fonte, tamanho);
        c.setNonStrokingColor(cor);
        c.newLineAtOffset(x, y);
        c.showText(s);
        c.endText();
    }

    private float escrever(String s, PDFont fonte, float tamanho, float x, float base, Color cor) throws IOException {
        c.beginText();
        c.setFont(fonte, tamanho);
        c.setNonStrokingColor(cor);
        c.newLineAtOffset(x, base);
        c.showText(s);
        c.endText();
        return base - tamanho - 3;
    }

    /** Corta o texto com "…" para caber na largura. */
    private static String cortar(String s, PDFont fonte, float tamanho, float maximo) throws IOException {
        if (fonte.getStringWidth(s) / 1000 * tamanho <= maximo) {
            return s;
        }
        String r = s;
        while (r.length() > 1 && fonte.getStringWidth(r + "…") / 1000 * tamanho > maximo) {
            r = r.substring(0, r.length() - 1);
        }
        return r + "…";
    }

    private void linha(float x1, float y1, float x2, float y2) throws IOException {
        c.setStrokingColor(Color.GRAY);
        c.setLineWidth(0.6f);
        c.moveTo(x1, y1);
        c.lineTo(x2, y2);
        c.stroke();
    }

    private PDFont fonte(String arquivo) throws IOException {
        try (InputStream in = LaudoVistoriaPdf.class.getResourceAsStream("/br/org/fmc/fonts/" + arquivo)) {
            if (in == null) {
                throw new IOException("Fonte não encontrada: " + arquivo);
            }
            return PDType0Font.load(documento, in);
        }
    }
}
