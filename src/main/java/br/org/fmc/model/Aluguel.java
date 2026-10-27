package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * O aluguel de um mês de um contrato.
 *
 * Competência = o mês em que o aluguel vence. "Gerar os aluguéis de outubro"
 * cria os aluguéis que vencem em outubro.
 */
public class Aluguel {

    private Long id;
    private Contrato contrato;
    private YearMonth competencia;
    private LocalDate vencimento;
    private BigDecimal valorAluguel;
    private BigDecimal taxaLuz;
    private BigDecimal valorIptu;
    private BigDecimal outros = BigDecimal.ZERO;
    private LocalDate dataPagamento; // nulo enquanto não for pago

    public Aluguel(Contrato contrato, YearMonth competencia) {
        this(contrato, competencia, true);
    }

    private Aluguel(Contrato contrato, YearMonth competencia, boolean conferirContrato) {
        this.contrato = Valida.obrigatorio(contrato, "o contrato");
        this.competencia = Valida.obrigatorio(competencia, "o mês");
        if (conferirContrato && !contrato.cobreCompetencia(competencia)) {
            throw new IllegalArgumentException("O contrato não tem aluguel vencendo em " + competencia + ".");
        }
        this.vencimento = contrato.vencimentoDe(competencia);
        this.valorAluguel = contrato.getValorAluguel();
        this.taxaLuz = contrato.getTaxaLuz();
        this.valorIptu = contrato.getValorIptu();
    }

    /**
     * Usado pelo repositório ao carregar do banco (BOARD-38): um aluguel já gravado volta como foi gerado,
     * com os valores daquele mês e o pagamento, se houver, mesmo que o contrato tenha sido
     * encerrado ou reajustado depois.
     */
    public static Aluguel doBanco(Contrato contrato, YearMonth competencia, LocalDate vencimento,
            BigDecimal valorAluguel, BigDecimal taxaLuz, BigDecimal valorIptu, BigDecimal outros,
            LocalDate dataPagamento) {
        Aluguel a = new Aluguel(contrato, competencia, false);
        a.vencimento = Valida.obrigatorio(vencimento, "o vencimento");
        a.valorAluguel = Valida.positivo(valorAluguel, "o valor do aluguel");
        a.taxaLuz = Valida.naoNegativo(taxaLuz, "a taxa de luz");
        a.valorIptu = Valida.naoNegativo(valorIptu, "o IPTU");
        a.setOutros(outros);
        a.dataPagamento = dataPagamento;
        return a;
    }

    /** Valor devido sem encargos: aluguel + luz + IPTU + outros. */
    public BigDecimal base() {
        return valorAluguel.add(taxaLuz).add(valorIptu).add(outros);
    }

    public SituacaoAluguel getSituacao(LocalDate hoje) {
        if (estaPago()) {
            return SituacaoAluguel.PAGO;
        }
        if (!hoje.isAfter(vencimento)) {
            return SituacaoAluguel.A_VENCER;
        }
        return diasAtraso(hoje) <= RegrasNegocio.TOLERANCIA_DIAS
                ? SituacaoAluguel.EM_TOLERANCIA
                : SituacaoAluguel.EM_ATRASO;
    }

    /** Dias passados do vencimento até a data informada (zero se ainda não venceu). */
    public long diasAtraso(LocalDate data) {
        return Math.max(0, ChronoUnit.DAYS.between(vencimento, data));
    }

    /** Encargos se for pago na data informada. */
    public Encargos encargosEm(LocalDate dataPagamento) {
        return Encargos.calcular(base(), vencimento, dataPagamento);
    }

    public boolean estaPago() {
        return dataPagamento != null;
    }

    public void registrarPagamento(LocalDate data) {
        if (estaPago()) {
            throw new IllegalStateException("Este aluguel já foi pago.");
        }
        this.dataPagamento = Valida.obrigatorio(data, "a data do pagamento");
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Contrato getContrato() {
        return contrato;
    }

    public YearMonth getCompetencia() {
        return competencia;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    public BigDecimal getValorAluguel() {
        return valorAluguel;
    }

    public BigDecimal getTaxaLuz() {
        return taxaLuz;
    }

    public BigDecimal getValorIptu() {
        return valorIptu;
    }

    public BigDecimal getOutros() {
        return outros;
    }

    public void setOutros(BigDecimal outros) {
        this.outros = Valida.naoNegativo(outros, "o valor de outros");
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }
}
