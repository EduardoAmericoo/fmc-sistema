package br.org.fmc.util;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.DispensaEncargos;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Recibo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BOARD-60 · Os textos do recibo, montados uma vez só. A janela do recibo e o PDF usam esta
 * classe, para os dois mostrarem exatamente o mesmo conteúdo (o desenho do protótipo aprovado).
 */
public final class ReciboImpresso {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_LONGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", BRASIL);

    public static final String FUNDACAO = "FUNDAÇÃO MANUEL CRUZ";
    public static final String ADMINISTRACAO = "Administrada pela Arquidiocese de Aracaju";
    public static final String CIDADE = "Aracaju — Sergipe";
    public static final String[] VIAS = {"1ª via: Locatário", "2ª via: Fundação"};

    /**
     * [D11] BOARD-66 · Como a dispensa de encargos aparece no recibo impresso (tela e PDF).
     *
     * true: multa, juros e correção com os valores do contrato e mais a linha
     * "Encargos dispensados" com o total dispensado (negativo), e o TOTAL fica igual ao valor devido.
     * false: multa, juros e correção aparecem zerados, sem a linha da dispensa.
     *
     * TODO BOARD-66: formato ainda a confirmar com o cliente. Para mudar, basta trocar aqui.
     */
    public static final boolean MOSTRAR_ENCARGOS_DISPENSADOS = true;

    public static final String ROTULO_DISPENSA = "Encargos dispensados";

    /** Uma linha da composição do valor, ex.: "Aluguel" / R$ 1.530,00. */
    public record Item(String rotulo, BigDecimal valor) {
    }

    private final Recibo recibo;

    public ReciboImpresso(Recibo recibo) {
        this.recibo = recibo;
    }

    public Recibo recibo() {
        return recibo;
    }

    /** "Nº 00814/2026". */
    public String numero() {
        return "Nº " + recibo.getNumero();
    }

    /** "Agosto de 2026". */
    public String mes() {
        return mesPorExtenso(recibo.getAluguel().getCompetencia());
    }

    public String pagador() {
        return recibo.getAluguel().getContrato().getInquilino().getNome().toUpperCase(BRASIL);
    }

    /** "(MIL, SETECENTOS E VINTE E OITO REAIS E SESSENTA E CINCO CENTAVOS)". */
    public String quantiaPorExtenso() {
        return "(" + recibo.valorPorExtenso().toUpperCase(BRASIL) + ")";
    }

    /** "proveniente do aluguel referente a agosto de 2026, do imóvel IM-005 · Apto 202 — Rua X, 10 — Centro." */
    public String referente() {
        Imovel imovel = recibo.getAluguel().getContrato().getImovel();
        StringBuilder texto = new StringBuilder("proveniente do aluguel referente a ")
                .append(mes().toLowerCase(BRASIL)).append(", do imóvel ").append(imovel.getCodigo());
        if (imovel.getUnidade() != null && !imovel.getUnidade().isBlank()) {
            texto.append(" · ").append(imovel.getUnidade());
        }
        if (imovel.getEndereco() != null && !imovel.getEndereco().isBlank()) {
            texto.append(" — ").append(imovel.getEndereco());
        }
        if (imovel.getBairro() != null && !imovel.getBairro().isBlank()) {
            texto.append(" — ").append(imovel.getBairro());
        }
        return texto.append(", contrato ").append(recibo.getAluguel().getContrato().getNumero()).append('.').toString();
    }

    public String vencimento() {
        return Formatos.data(recibo.getAluguel().getVencimento());
    }

    /** "Pago em 05/11/2026 por PIX · documento TX-123". */
    public String pagamento() {
        String doc = recibo.getDocumento();
        return "Pago em " + Formatos.data(recibo.getDataPagamento()) + " por " + recibo.getFormaPagamento().getDescricao()
                + (doc == null || doc.isBlank() ? "" : " · documento " + doc);
    }

    /** As formas de pagamento, para marcar a usada com um X (como no recibo em papel). */
    public List<FormaPagamento> formas() {
        return List.of(FormaPagamento.values());
    }

    public boolean foiPago(FormaPagamento forma) {
        return recibo.getFormaPagamento() == forma;
    }

    /** "Aracaju (SE), 23 de setembro de 2026" (dia da emissão). */
    public String localEData() {
        LocalDate dia = recibo.getDataEmissao().toLocalDate();
        return "Aracaju (SE), " + dia.format(DATA_LONGA);
    }

    /** "Venc. contrato: 03/05/2027 · Reajuste anual: 03/05/2027". */
    public String contrato() {
        Contrato c = recibo.getAluguel().getContrato();
        return "Venc. contrato: " + Formatos.data(c.getDataTermino())
                + " · Reajuste anual: " + Formatos.data(c.proximoReajuste());
    }

    public String emitidoPor() {
        return "Emitido por " + recibo.getEmitidoPor().getNome();
    }

    /**
     * Aluguel, taxa de luz, IPTU, outros, correção, juros e multa. Se houve dispensa (BOARD-66),
     * ver MOSTRAR_ENCARGOS_DISPENSADOS. A soma das linhas é sempre igual ao total().
     */
    public List<Item> composicao() {
        Aluguel a = recibo.getAluguel();
        Encargos e = recibo.getEncargos();
        DispensaEncargos d = recibo.getDispensa();
        List<Item> itens = new ArrayList<>(List.of(
                new Item("Aluguel", a.getValorAluguel()),
                new Item("Taxa de luz", a.getTaxaLuz()),
                new Item("IPTU", a.getValorIptu()),
                new Item("Outros", a.getOutros())));
        if (d != null && MOSTRAR_ENCARGOS_DISPENSADOS) {
            itens.add(new Item("Correção", d.getCorrecao()));
            itens.add(new Item("Juros", d.getJuros()));
            itens.add(new Item("Multa", d.getMulta()));
            itens.add(new Item(ROTULO_DISPENSA, d.total().negate()));
        } else {
            itens.add(new Item("Correção", e.getCorrecao()));
            itens.add(new Item("Juros", e.getJuros()));
            itens.add(new Item("Multa", e.getMulta()));
        }
        return itens;
    }

    public BigDecimal total() {
        return recibo.getValorTotal();
    }

    /** "outubro de 2026" vira "Outubro de 2026". */
    public static String mesPorExtenso(YearMonth mes) {
        String nome = mes.getMonth().getDisplayName(TextStyle.FULL, BRASIL);
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1) + " de " + mes.getYear();
    }

    /** Nome sugerido para o arquivo PDF: "recibo-00814-2026-CT-1004.pdf". */
    public String nomeArquivo() {
        return "recibo-" + recibo.getNumero().replace('/', '-') + "-" + recibo.getAluguel().getContrato().getNumero() + ".pdf";
    }
}
