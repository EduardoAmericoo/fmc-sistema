package br.org.fmc.util;

import br.org.fmc.model.Aluguel;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Gera um demonstrativo de cobrança de teste, sem qualquer meio de pagamento. */
public final class DocumentoCobrancaPdf {

    private static final Color AZUL = new Color(29, 77, 122);
    private static final Color VERMELHO = new Color(164, 34, 34);
    private static final Color FUNDO_AVISO = new Color(255, 235, 235);
    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private DocumentoCobrancaPdf() {
    }

    /** Salva o demonstrativo no destino informado. O arquivo não permite pagamento. */
    public static void gerar(Aluguel aluguel, LocalDate emitidoEm, Path destino) throws IOException {
        Objects.requireNonNull(aluguel, "o aluguel");
        Objects.requireNonNull(emitidoEm, "a data de emissão");
        Objects.requireNonNull(destino, "o destino");

        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                desenhar(conteudo, pagina, aluguel, emitidoEm);
            }
            documento.save(destino.toFile());
        }
    }

    private static void desenhar(PDPageContentStream c, PDPage pagina, Aluguel aluguel, LocalDate emitidoEm)
            throws IOException {
        float esquerda = 52;
        float largura = pagina.getMediaBox().getWidth() - esquerda * 2;
        float topo = pagina.getMediaBox().getHeight() - 48;
        PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        retangulo(c, esquerda, topo - 58, largura, 58, AZUL);
        texto(c, negrito, 17, Color.WHITE, esquerda + 18, topo - 25, "FUNDACAO MANUEL CRUZ");
        texto(c, normal, 10, Color.WHITE, esquerda + 18, topo - 43, "Gestao de alugueis");

        texto(c, negrito, 18, AZUL, esquerda, topo - 91, "DEMONSTRATIVO DE COBRANCA");
        retangulo(c, esquerda, topo - 151, largura, 42, FUNDO_AVISO);
        textoCentralizado(c, negrito, 14, VERMELHO, esquerda, largura, topo - 127,
                "SIMULACAO - SEM VALOR PARA PAGAMENTO");
        textoCentralizado(c, negrito, 8, VERMELHO, esquerda, largura, topo - 143,
                "NAO E BOLETO BANCARIO. NAO PAGAR.");

        float y = topo - 187;
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Pagador",
                aluguel.getContrato().getInquilino().getNome());
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Contrato",
                aluguel.getContrato().getNumero());
        String unidade = aluguel.getContrato().getImovel().getUnidade();
        String endereco = aluguel.getContrato().getImovel().getEndereco();
        String imovel = aluguel.getContrato().getImovel().getCodigo()
                + (unidade == null ? "" : " - " + unidade) + " - " + endereco;
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Imovel", imovel);
        String mes = aluguel.getCompetencia().getMonth().getDisplayName(TextStyle.FULL, BRASIL);
        String competencia = Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " de "
                + aluguel.getCompetencia().getYear();
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Competencia", competencia);
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Vencimento",
                Formatos.data(aluguel.getVencimento()));
        y = rotuloValor(c, normal, negrito, esquerda, y, largura, "Data de emissao", Formatos.data(emitidoEm));

        y -= 14;
        texto(c, negrito, 11, AZUL, esquerda, y, "COMPOSICAO DO VALOR");
        y -= 22;
        y = linhaValor(c, normal, negrito, esquerda, y, largura, "Aluguel", aluguel.getValorAluguel());
        y = linhaValor(c, normal, negrito, esquerda, y, largura, "Taxa de luz", aluguel.getTaxaLuz());
        y = linhaValor(c, normal, negrito, esquerda, y, largura, "IPTU", aluguel.getValorIptu());
        y = linhaValor(c, normal, negrito, esquerda, y, largura, "Outros", aluguel.getOutros());
        y -= 4;
        linhaHorizontal(c, esquerda, y, largura, AZUL);
        y -= 21;
        texto(c, negrito, 12, Color.BLACK, esquerda, y, "TOTAL DEMONSTRATIVO");
        textoAlinhadoDireita(c, negrito, 14, AZUL, esquerda, largura, y, Formatos.moeda(aluguel.base()));

        retangulo(c, esquerda, 50, largura, 35, FUNDO_AVISO);
        textoCentralizado(c, negrito, 10, VERMELHO, esquerda, largura, 72,
                "SIMULACAO - SEM VALOR PARA PAGAMENTO");
        textoCentralizado(c, normal, 8, VERMELHO, esquerda, largura, 59,
                "Sem linha digitavel, codigo de barras, PIX ou identificacao bancaria.");
        textoCentralizado(c, normal, 8, Color.DARK_GRAY, esquerda, largura, 34,
                "Documento demonstrativo para apresentacao. Nao constitui cobranca bancaria.");
    }

    private static float rotuloValor(PDPageContentStream c, PDType1Font normal, PDType1Font negrito,
            float x, float y, float largura, String rotulo, String valor) throws IOException {
        texto(c, negrito, 9, Color.DARK_GRAY, x, y, rotulo.toUpperCase(BRASIL));
        float proximaLinha = textoQuebrado(c, normal, 12, Color.BLACK, x, y - 16, largura, valor);
        return proximaLinha - 16;
    }

    private static float linhaValor(PDPageContentStream c, PDType1Font normal, PDType1Font negrito,
            float x, float y, float largura, String rotulo, java.math.BigDecimal valor) throws IOException {
        texto(c, normal, 11, Color.BLACK, x, y, rotulo);
        textoAlinhadoDireita(c, negrito, 11, Color.BLACK, x, largura, y, Formatos.moeda(valor));
        return y - 19;
    }

    private static float textoQuebrado(PDPageContentStream c, PDType1Font fonte, float tamanho, Color cor,
            float x, float y, float largura, String valor) throws IOException {
        float cursorX = x;
        float cursorY = y;
        for (String palavra : valor.split("\\s+")) {
            String parte = cursorX == x ? palavra : " " + palavra;
            if (cursorX + larguraTexto(fonte, tamanho, parte) > x + largura) {
                cursorY -= tamanho + 4;
                cursorX = x;
                parte = palavra;
            }
            texto(c, fonte, tamanho, cor, cursorX, cursorY, parte);
            cursorX += larguraTexto(fonte, tamanho, parte);
        }
        return cursorY;
    }

    private static void texto(PDPageContentStream c, PDType1Font fonte, float tamanho, Color cor,
            float x, float y, String valor) throws IOException {
        c.beginText();
        c.setFont(fonte, tamanho);
        c.setNonStrokingColor(cor);
        c.newLineAtOffset(x, y);
        c.showText(valor == null ? "-" : valor);
        c.endText();
    }

    private static void textoCentralizado(PDPageContentStream c, PDType1Font fonte, float tamanho,
            Color cor, float x, float largura, float y, String valor) throws IOException {
        float textoLargura = larguraTexto(fonte, tamanho, valor);
        texto(c, fonte, tamanho, cor, x + Math.max(0, (largura - textoLargura) / 2), y, valor);
    }

    private static void textoAlinhadoDireita(PDPageContentStream c, PDType1Font fonte, float tamanho,
            Color cor, float x, float largura, float y, String valor) throws IOException {
        texto(c, fonte, tamanho, cor, x + largura - larguraTexto(fonte, tamanho, valor), y, valor);
    }

    private static float larguraTexto(PDType1Font fonte, float tamanho, String valor) throws IOException {
        return fonte.getStringWidth(valor) / 1000 * tamanho;
    }

    private static void retangulo(PDPageContentStream c, float x, float y, float largura, float altura,
            Color cor) throws IOException {
        c.setNonStrokingColor(cor);
        c.addRect(x, y, largura, altura);
        c.fill();
    }

    private static void linhaHorizontal(PDPageContentStream c, float x, float y, float largura,
            Color cor) throws IOException {
        c.setStrokingColor(cor);
        c.moveTo(x, y);
        c.lineTo(x + largura, y);
        c.stroke();
    }
}
