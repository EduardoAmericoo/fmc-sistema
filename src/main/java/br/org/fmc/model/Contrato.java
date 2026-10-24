package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrato de locação: liga um imóvel a um inquilino, com uma garantia.
 *
 * No MVP o contrato já nasce ativo (sem etapa de aprovação). Os valores do
 * aluguel são copiados do imóvel no momento do cadastro, para que um reajuste
 * futuro no imóvel não mude contratos antigos.
 */
public class Contrato {

    private Long id;
    private String numero;
    private Imovel imovel;
    private Inquilino inquilino;
    private Garantia garantia;
    private BigDecimal valorAluguel;
    private BigDecimal taxaLuz;
    private BigDecimal valorIptu;
    private PrazoContrato prazo;
    private LocalDate dataEntregaChaves;
    private LocalDate dataTermino;
    private int diaVencimento;
    private StatusContrato status = StatusContrato.ATIVO;

    public Contrato(Imovel imovel, Inquilino inquilino, Garantia garantia,
                    PrazoContrato prazo, LocalDate dataEntregaChaves) {
        this.imovel = Valida.obrigatorio(imovel, "o imóvel");
        this.inquilino = Valida.obrigatorio(inquilino, "o inquilino");
        this.garantia = Valida.obrigatorio(garantia, "a garantia (fiador ou caução)");
        this.prazo = Valida.obrigatorio(prazo, "o prazo");
        this.dataEntregaChaves = Valida.obrigatorio(dataEntregaChaves, "a data de entrega das chaves");
        this.valorAluguel = imovel.getValorAluguel();
        this.taxaLuz = imovel.getTaxaLuz();
        this.valorIptu = imovel.getValorIptu();
        this.dataTermino = dataEntregaChaves.plusMonths(prazo.getMeses());
        this.diaVencimento = primeiroVencimento().getDayOfMonth();
    }

    /**
     * Confere as regras do próprio contrato: renda e garantia.
     * (Imóvel vago e inquilino sem outro contrato são conferidos pelo ServicoContratos,
     * porque dependem do banco.)
     */
    public List<String> validar() {
        List<String> erros = new ArrayList<>();
        if (!atendeRegraRenda()) {
            erros.add("A renda familiar do inquilino precisa ser pelo menos "
                    + RegrasNegocio.MULTIPLO_RENDA + " vezes o aluguel.");
        }
        erros.addAll(garantia.validar(this));
        return erros;
    }

    /** Renda familiar do inquilino ≥ 3 × o aluguel. */
    public boolean atendeRegraRenda() {
        BigDecimal minimo = valorAluguel.multiply(BigDecimal.valueOf(RegrasNegocio.MULTIPLO_RENDA));
        return inquilino.rendaConsiderada().compareTo(minimo) >= 0;
    }

    /** O 1º aluguel vence 30 dias depois da entrega das chaves. */
    public LocalDate primeiroVencimento() {
        return dataEntregaChaves.plusDays(RegrasNegocio.DIAS_1O_VENCIMENTO);
    }

    /**
     * Data de vencimento do aluguel que vence no mês informado.
     * Em meses mais curtos (ex.: dia 31 em fevereiro), vence no último dia do mês.
     */
    public LocalDate vencimentoDe(YearMonth mes) {
        return mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
    }

    /** Este contrato tem aluguel vencendo no mês informado? */
    public boolean cobreCompetencia(YearMonth mes) {
        boolean depoisDoInicio = !mes.isBefore(YearMonth.from(primeiroVencimento()));
        boolean antesDoFim = !vencimentoDe(mes).isAfter(dataTermino);
        return status == StatusContrato.ATIVO && depoisDoInicio && antesDoFim;
    }

    public boolean estaVigente(LocalDate hoje) {
        return status == StatusContrato.ATIVO && !hoje.isBefore(dataEntregaChaves);
    }

    /** Encerra o contrato e libera o imóvel. */
    public void encerrar() {
        status = StatusContrato.ENCERRADO;
        imovel.desocupar();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = Valida.textoOpcional(numero);
    }

    public Imovel getImovel() {
        return imovel;
    }

    public Inquilino getInquilino() {
        return inquilino;
    }

    public Garantia getGarantia() {
        return garantia;
    }

    public BigDecimal getValorAluguel() {
        return valorAluguel;
    }

    /**
     * Usado pelo repositório ao carregar um contrato do banco: volta os valores
     * do dia do cadastro (o imóvel pode ter sido reajustado depois).
     */
    public void setValores(BigDecimal valorAluguel, BigDecimal taxaLuz, BigDecimal valorIptu) {
        this.valorAluguel = Valida.positivo(valorAluguel, "o valor do aluguel");
        this.taxaLuz = Valida.naoNegativo(taxaLuz, "a taxa de luz");
        this.valorIptu = Valida.naoNegativo(valorIptu, "o IPTU");
    }

    public BigDecimal getTaxaLuz() {
        return taxaLuz;
    }

    public BigDecimal getValorIptu() {
        return valorIptu;
    }

    public PrazoContrato getPrazo() {
        return prazo;
    }

    public LocalDate getDataEntregaChaves() {
        return dataEntregaChaves;
    }

    public LocalDate getDataTermino() {
        return dataTermino;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public StatusContrato getStatus() {
        return status;
    }

    /** Usado pelo repositório ao carregar um contrato do banco. */
    public void setStatus(StatusContrato status) {
        this.status = Valida.obrigatorio(status, "a situação do contrato");
    }

    @Override
    public String toString() {
        return (numero != null ? numero : "Contrato") + " · " + imovel.getCodigo() + " · " + inquilino.getNome();
    }
}
