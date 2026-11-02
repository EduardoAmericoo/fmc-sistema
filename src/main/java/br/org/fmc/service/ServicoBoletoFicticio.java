package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Encargos;
import br.org.fmc.util.Formatos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Documento de demonstração. Não registra boleto bancário nem altera o pagamento do aluguel. */
public class ServicoBoletoFicticio {
    public byte[] gerar(Aluguel aluguel, LocalDate emissao) throws IOException {
        Sessao.exigir(Acao.GERAR_ALUGUEIS);
        Objects.requireNonNull(aluguel, "Escolha uma cobrança.");
        Objects.requireNonNull(emissao, "Informe a data de emissão.");
        if (aluguel.estaPago()) {
            throw new RegraNegocioException("Esta cobrança já foi paga. Escolha uma cobrança em aberto.");
        }
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            documento.getDocumentInformation().setTitle("Boleto fictício - " + aluguel.getContrato().getNumero());
            try (var fonteArquivo = getClass().getResourceAsStream("/br/org/fmc/fonts/PlusJakartaSans-Regular.ttf")) {
                PDFont fonte = PDType0Font.load(documento, fonteArquivo);
                try (PDPageContentStream desenho = new PDPageContentStream(documento, pagina)) {
                    desenho.setNonStrokingColor(29 / 255f, 77 / 255f, 122 / 255f);
                    desenho.addRect(36, 720, 523, 86);
                    desenho.fill();
                    desenho.setNonStrokingColor(1f, 1f, 1f);
                    texto(desenho, fonte, 19, 52, 774, "Fundação Manuel Cruz");
                    texto(desenho, fonte, 13, 52, 748, "BOLETO FICTÍCIO • DEMONSTRAÇÃO");
                    desenho.setNonStrokingColor(0.7f, 0.1f, 0.1f);
                    texto(desenho, fonte, 12, 40, 695, "SEM VALIDADE PARA PAGAMENTO — NÃO PAGAR");
                    desenho.setNonStrokingColor(0.12f, 0.16f, 0.20f);
                    linha(desenho, fonte, 660, "Beneficiário", "Fundação Manuel Cruz (demonstração)");
                    linha(desenho, fonte, 618, "Pagador", aluguel.getContrato().getInquilino().getNome());
                    linha(desenho, fonte, 576, "Imóvel", aluguel.getContrato().getImovel().getCodigo()
                            + " • " + aluguel.getContrato().getImovel().getEndereco());
                    linha(desenho, fonte, 534, "Contrato / competência", aluguel.getContrato().getNumero()
                            + " / " + aluguel.getCompetencia());
                    linha(desenho, fonte, 492, "Emissão / vencimento original", Formatos.data(emissao)
                            + " / " + Formatos.data(aluguel.getVencimento()));
                    Encargos encargos = aluguel.encargosEm(emissao);
                    linha(desenho, fonte, 440, "Aluguel", Formatos.moeda(aluguel.getValorAluguel()));
                    linha(desenho, fonte, 402, "Luz / IPTU / outros", Formatos.moeda(aluguel.getTaxaLuz())
                            + " / " + Formatos.moeda(aluguel.getValorIptu()) + " / " + Formatos.moeda(aluguel.getOutros()));
                    linha(desenho, fonte, 364, "Multa / juros / correção", Formatos.moeda(encargos.getMulta())
                            + " / " + Formatos.moeda(encargos.getJuros()) + " / " + Formatos.moeda(encargos.getCorrecao()));
                    desenho.setNonStrokingColor(0.91f, 0.95f, 0.98f);
                    desenho.addRect(36, 272, 523, 60);
                    desenho.fill();
                    desenho.setNonStrokingColor(0.11f, 0.30f, 0.48f);
                    texto(desenho, fonte, 12, 52, 309, "TOTAL SIMULADO EM " + Formatos.data(emissao));
                    texto(desenho, fonte, 20, 52, 285, Formatos.moeda(aluguel.base().add(encargos.total())));
                    desenho.setNonStrokingColor(0.25f, 0.25f, 0.25f);
                    texto(desenho, fonte, 10, 40, 239, "Encargos calculados pelas regras de demonstração do sistema.");
                    texto(desenho, fonte, 10, 40, 219, "Este PDF não registra pagamento nem altera a situação da cobrança.");
                    desenho.setLineDashPattern(new float[]{4, 4}, 0);
                    desenho.moveTo(36, 188);
                    desenho.lineTo(559, 188);
                    desenho.stroke();
                    texto(desenho, fonte, 12, 40, 160, "DOCUMENTO DE TESTE — NÃO É BOLETO BANCÁRIO");
                    texto(desenho, fonte, 10, 40, 138, "Sem linha digitável, código de barras ou QR Code para pagamento.");
                    texto(desenho, fonte, 10, 40, 118, "Uso exclusivo para testar a apresentação de uma cobrança ao cliente.");
                }
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private static void linha(PDPageContentStream desenho, PDFont fonte, float y, String rotulo, String valor)
            throws IOException {
        texto(desenho, fonte, 9, 40, y, rotulo);
        texto(desenho, fonte, 12, 40, y - 18, valor);
    }

    private static void texto(PDPageContentStream desenho, PDFont fonte, float tamanho, float x, float y, String valor)
            throws IOException {
        String seguro = valor.replaceAll("[\\p{Cntrl}]", " ");
        while (!seguro.isEmpty() && fonte.getStringWidth(seguro) / 1000 * tamanho > 510) {
            seguro = seguro.substring(0, seguro.length() - 1);
        }
        desenho.beginText();
        desenho.setFont(fonte, tamanho);
        desenho.newLineAtOffset(x, y);
        desenho.showText(seguro);
        desenho.endText();
    }
}
