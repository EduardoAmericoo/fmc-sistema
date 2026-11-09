package br.org.fmc.util;

import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Recibo;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * BOARD-60 · O recibo em PDF, numa folha A4 com as duas vias (Locatário e Fundação) e uma linha
 * de corte no meio. O conteúdo vem do ReciboImpresso, o mesmo da janela do recibo.
 */
public final class ReciboPdf {

    private static final Color AZUL = new Color(29, 77, 122);
    private static final Color TEXTO = new Color(19, 32, 46);
    private static final Color SUAVE = new Color(100, 109, 121);
    private static final Color BORDA = new Color(160, 168, 178);
    private static final float MARGEM = 36;

    private PDFont normal;
    private PDFont negrito;

    private ReciboPdf() {
    }

    /** Salva o recibo (2 vias) no arquivo escolhido. */
    public static void gerar(Recibo recibo, Path destino) throws IOException {
        Objects.requireNonNull(recibo, "o recibo");
        Objects.requireNonNull(destino, "o destino");
        new ReciboPdf().salvar(new ReciboImpresso(recibo), destino);
    }

    private void salvar(ReciboImpresso r, Path destino) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            normal = fonte(documento, "PlusJakartaSans-Regular.ttf");
            negrito = fonte(documento, "PlusJakartaSans-Bold.ttf");
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            float altura = pagina.getMediaBox().getHeight();
            float largura = pagina.getMediaBox().getWidth() - MARGEM * 2;
            float meio = altura / 2;
            try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                via(c, r, ReciboImpresso.VIAS[0], altura - MARGEM, meio + 14, largura);
                linhaDeCorte(c, meio, largura);
                via(c, r, ReciboImpresso.VIAS[1], meio - 14, MARGEM, largura);
            }
            documento.getDocumentInformation().setTitle("Recibo " + r.recibo().getNumero());
            documento.save(destino.toFile());
        }
    }

    private static PDFont fonte(PDDocument documento, String arquivo) throws IOException {
        try (InputStream in = ReciboPdf.class.getResourceAsStream("/br/org/fmc/fonts/" + arquivo)) {
            if (in == null) {
                throw new IOException("Fonte não encontrada: " + arquivo);
            }
            return PDType0Font.load(documento, in);
        }
    }

    /** Uma via do recibo, entre o topo e a base informados. */
    private void via(PDPageContentStream c, ReciboImpresso r, String nomeVia, float topo, float base, float largura)
            throws IOException {
        float x = MARGEM;
        float dentro = x + 14;
        float util = largura - 28;
        moldura(c, x, base, largura, topo - base);

        // cabeçalho: Fundação à esquerda, mês no meio, composição à direita
        float y = topo - 22;
        texto(c, negrito, 11, TEXTO, dentro, y, ReciboImpresso.FUNDACAO);
        texto(c, normal, 7.5f, SUAVE, dentro, y - 12, ReciboImpresso.ADMINISTRACAO);
        texto(c, normal, 7.5f, SUAVE, dentro, y - 22, ReciboImpresso.CIDADE);

        float caixaX = dentro + 175;
        float caixaL = 130;
        moldura(c, caixaX, y - 26, caixaL, 34);
        centralizado(c, normal, 7, SUAVE, caixaX, caixaL, y - 2, "MÊS");
        centralizado(c, negrito, 10, TEXTO, caixaX, caixaL, y - 17, r.mes());

        float colX = dentro + util - 165;
        float colL = 165;
        float yc = y + 2;
        for (ReciboImpresso.Item item : r.composicao()) {
            texto(c, normal, 7.5f, TEXTO, colX, yc, item.rotulo());
            direita(c, normal, 7.5f, TEXTO, colX, colL, yc, Formatos.moeda(item.valor()));
            yc -= 11;
        }
        linha(c, colX, yc + 7, colL, TEXTO);
        texto(c, negrito, 8, TEXTO, colX, yc - 3, "TOTAL");
        direita(c, negrito, 8, TEXTO, colX, colL, yc - 3, Formatos.moeda(r.total()));

        // título
        float yt = yc - 30;
        centralizado(c, negrito, 15, TEXTO, x, largura, yt, "RECIBO");
        centralizado(c, normal, 10, TEXTO, x, largura, yt - 15, r.numero());

        // corpo
        float yb = yt - 40;
        yb = paragrafo(c, dentro, yb, util, 9, List.of(new Parte("Recebemos de ", false), new Parte(r.pagador(), true)));
        yb = paragrafo(c, dentro, yb, util, 9, List.of(new Parte("a quantia de ", false),
                new Parte(Formatos.moeda(r.total()) + " " + r.quantiaPorExtenso(), true)));
        yb = paragrafo(c, dentro, yb, util, 9, List.of(new Parte(r.referente(), false)));

        // vencimento e forma de pagamento (a usada marcada com X)
        yb -= 4;
        texto(c, normal, 9, TEXTO, dentro, yb, "Vencimento:");
        float fx = dentro + larguraTexto(normal, 9, "Vencimento: ");
        texto(c, negrito, 9, TEXTO, fx, yb, r.vencimento());
        fx += larguraTexto(negrito, 9, r.vencimento()) + 16;
        for (FormaPagamento forma : r.formas()) {
            moldura(c, fx, yb - 1, 8, 8);
            if (r.foiPago(forma)) {
                texto(c, negrito, 8, TEXTO, fx + 1.6f, yb, "X");
            }
            String nome = forma.getDescricao().toUpperCase(java.util.Locale.forLanguageTag("pt-BR"));
            texto(c, normal, 8, TEXTO, fx + 12, yb, nome);
            fx += 12 + larguraTexto(normal, 8, nome) + 12;
        }
        yb -= 16;
        texto(c, normal, 8.5f, SUAVE, dentro, yb, r.pagamento());
        yb -= 20;
        centralizado(c, negrito, 9, TEXTO, x, largura, yb, r.localEData());
        yb -= 18;
        texto(c, normal, 8, TEXTO, dentro, yb, r.contrato());
        direita(c, normal, 8, SUAVE, dentro, util, yb, r.emitidoPor());

        // rodapé da via
        linha(c, dentro, base + 24, util, TEXTO);
        texto(c, normal, 7.5f, SUAVE, dentro, base + 11, nomeVia);
        direita(c, normal, 7.5f, SUAVE, dentro, util, base + 11, "Assinatura: ______________________________");
    }

    private record Parte(String texto, boolean negrito) {
    }

    /** Texto com partes normais e em negrito, quebrando a linha quando não cabe. Devolve o próximo y. */
    private float paragrafo(PDPageContentStream c, float x, float y, float largura, float tamanho, List<Parte> partes)
            throws IOException {
        List<Parte> palavras = new ArrayList<>();
        for (Parte p : partes) {
            for (String palavra : p.texto().split(" ")) {
                if (!palavra.isEmpty()) {
                    palavras.add(new Parte(palavra, p.negrito()));
                }
            }
        }
        float cx = x;
        for (Parte p : palavras) {
            PDFont f = p.negrito() ? negrito : normal;
            float w = larguraTexto(f, tamanho, p.texto());
            if (cx > x && cx + w > x + largura) {
                y -= tamanho + 4;
                cx = x;
            }
            texto(c, f, tamanho, TEXTO, cx, y, p.texto());
            cx += w + larguraTexto(normal, tamanho, " ");
        }
        return y - tamanho - 8;
    }

    private void linhaDeCorte(PDPageContentStream c, float y, float largura) throws IOException {
        c.setStrokingColor(BORDA);
        c.setLineDashPattern(new float[] {4, 3}, 0);
        c.moveTo(MARGEM, y);
        c.lineTo(MARGEM + largura, y);
        c.stroke();
        c.setLineDashPattern(new float[] {}, 0);
        centralizado(c, normal, 7, SUAVE, MARGEM, largura, y + 4, "corte aqui");
    }

    private static void moldura(PDPageContentStream c, float x, float y, float largura, float altura) throws IOException {
        c.setStrokingColor(BORDA);
        c.setLineWidth(0.8f);
        c.addRect(x, y, largura, altura);
        c.stroke();
    }

    private static void linha(PDPageContentStream c, float x, float y, float largura, Color cor) throws IOException {
        c.setStrokingColor(cor);
        c.setLineWidth(0.8f);
        c.moveTo(x, y);
        c.lineTo(x + largura, y);
        c.stroke();
    }

    private static void texto(PDPageContentStream c, PDFont fonte, float tamanho, Color cor, float x, float y,
            String valor) throws IOException {
        c.beginText();
        c.setFont(fonte, tamanho);
        c.setNonStrokingColor(cor);
        c.newLineAtOffset(x, y);
        c.showText(valor == null ? "" : valor);
        c.endText();
    }

    private static void centralizado(PDPageContentStream c, PDFont fonte, float tamanho, Color cor, float x,
            float largura, float y, String valor) throws IOException {
        texto(c, fonte, tamanho, cor, x + Math.max(0, (largura - larguraTexto(fonte, tamanho, valor)) / 2), y, valor);
    }

    private static void direita(PDPageContentStream c, PDFont fonte, float tamanho, Color cor, float x,
            float largura, float y, String valor) throws IOException {
        texto(c, fonte, tamanho, cor, x + largura - larguraTexto(fonte, tamanho, valor), y, valor);
    }

    private static float larguraTexto(PDFont fonte, float tamanho, String valor) throws IOException {
        return fonte.getStringWidth(valor) / 1000 * tamanho;
    }
}
