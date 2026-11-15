package br.org.fmc.model;

import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Recibo de pagamento de um aluguel, com número sequencial por ano (00816/2026).
 * O número é dado pelo ServicoRecebimento, que garante que nunca se repete.
 */
public class Recibo {

    private Long id;
    private final int ano;
    private final int sequencial;
    private final Aluguel aluguel;
    private final LocalDate dataPagamento;
    private final FormaPagamento formaPagamento;
    private final Encargos encargos;
    private final BigDecimal valorTotal;
    private final String documento;
    private final Usuario emitidoPor;
    private final DispensaEncargos dispensa;
    private LocalDateTime dataEmissao = LocalDateTime.now();

    public Recibo(int ano, int sequencial, Aluguel aluguel, LocalDate dataPagamento,
                  FormaPagamento formaPagamento, Encargos encargos, Usuario emitidoPor) {
        this(ano, sequencial, aluguel, dataPagamento, formaPagamento, encargos, null, emitidoPor);
    }

    public Recibo(int ano, int sequencial, Aluguel aluguel, LocalDate dataPagamento,
                  FormaPagamento formaPagamento, Encargos encargos, String documento, Usuario emitidoPor) {
        this(ano, sequencial, aluguel, dataPagamento, formaPagamento, encargos, documento, emitidoPor, null);
    }

    /**
     * Recibo com dispensa de encargos (BOARD-66). Os encargos são os COBRADOS (zerados quando
     * houve dispensa); os valores que deixaram de ser cobrados ficam na dispensa.
     *
     * @param dispensa null quando não houve dispensa
     */
    public Recibo(int ano, int sequencial, Aluguel aluguel, LocalDate dataPagamento,
                  FormaPagamento formaPagamento, Encargos encargos, String documento, Usuario emitidoPor,
                  DispensaEncargos dispensa) {
        if (sequencial <= 0) {
            throw new IllegalArgumentException("O número do recibo precisa ser maior que zero.");
        }
        this.ano = ano;
        this.sequencial = sequencial;
        this.aluguel = Valida.obrigatorio(aluguel, "o aluguel");
        this.dataPagamento = Valida.obrigatorio(dataPagamento, "a data do pagamento");
        this.formaPagamento = Valida.obrigatorio(formaPagamento, "a forma de pagamento");
        this.encargos = Valida.obrigatorio(encargos, "os encargos");
        this.documento = Valida.textoOpcional(documento);
        this.emitidoPor = Valida.obrigatorio(emitidoPor, "quem emitiu o recibo");
        this.dispensa = dispensa;
        this.valorTotal = aluguel.base().add(encargos.total());
    }

    /** Ex.: 00816/2026. */
    public String getNumero() {
        return String.format("%05d/%d", sequencial, ano);
    }

    /** Ex.: "mil e quinhentos reais". */
    public String valorPorExtenso() {
        return Formatos.porExtenso(valorTotal);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getAno() {
        return ano;
    }

    public int getSequencial() {
        return sequencial;
    }

    public Aluguel getAluguel() {
        return aluguel;
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Encargos getEncargos() {
        return encargos;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getDocumento() {
        return documento;
    }

    public Usuario getEmitidoPor() {
        return emitidoPor;
    }

    /** Encargos dispensados neste recebimento (BOARD-66), ou null se não houve dispensa. */
    public DispensaEncargos getDispensa() {
        return dispensa;
    }

    /** [D4] BOARD-43 · Para onde o dinheiro deste recibo foi: Caixa (dinheiro e cheque) ou Banco. */
    public Conta getConta() {
        return Conta.de(formaPagamento);
    }

    public boolean teveDispensa() {
        return dispensa != null;
    }

    public LocalDateTime getDataEmissao() {
        return dataEmissao;
    }

    /** Usado pelo repositório ao carregar um recibo do banco. */
    public void setDataEmissao(LocalDateTime dataEmissao) {
        this.dataEmissao = Valida.obrigatorio(dataEmissao, "a data de emissão");
    }
}
