package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um dos imóveis do patrimônio da Fundação. */
public class Imovel {

    private Long id;
    private String codigo;
    private String unidade;
    private TipoImovel tipo;
    private String endereco;
    private String bairro;
    private String grupo;
    private BigDecimal valorAluguel;
    private BigDecimal taxaLuz = BigDecimal.ZERO;
    private BigDecimal valorIptu = BigDecimal.ZERO;
    private SituacaoImovel situacao = SituacaoImovel.VAGO;

    /**
     * Arquivado: o imóvel saiu do patrimônio (ex.: vendido) ou de uso. Some das listas do dia a dia
     * e dos formulários, mas continua guardado com o histórico, e pode ser reativado.
     * null = não está arquivado.
     */
    private LocalDate arquivadoEm;
    private String motivoArquivamento;

    public Imovel(String codigo, TipoImovel tipo, String endereco, BigDecimal valorAluguel) {
        setCodigo(codigo);
        setTipo(tipo);
        setEndereco(endereco);
        setValorAluguel(valorAluguel);
    }

    /** Pode receber um contrato novo: só se estiver vago e não arquivado. */
    public boolean estaDisponivel() {
        return situacao == SituacaoImovel.VAGO && !estaArquivado();
    }

    public boolean estaArquivado() {
        return arquivadoEm != null;
    }

    /** Arquiva o imóvel na data informada. O motivo é opcional (ex.: "Vendido"). */
    public void arquivar(LocalDate data, String motivo) {
        arquivadoEm = Valida.obrigatorio(data, "a data do arquivamento");
        motivoArquivamento = Valida.textoOpcional(motivo);
    }

    /** Volta para as listas do dia a dia. */
    public void reativar() {
        arquivadoEm = null;
        motivoArquivamento = null;
    }

    public LocalDate getArquivadoEm() {
        return arquivadoEm;
    }

    public String getMotivoArquivamento() {
        return motivoArquivamento;
    }

    /** Marca como ocupado. Usado quando um contrato é cadastrado. */
    public void ocupar() {
        if (!estaDisponivel()) {
            throw new IllegalStateException("O imóvel " + codigo + " não está vago.");
        }
        situacao = SituacaoImovel.OCUPADO;
    }

    /** Volta a ficar vago (fim do contrato). */
    public void desocupar() {
        situacao = SituacaoImovel.VAGO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Pode ser nulo. Ex.: "Apto 101 · Bloco A". Se for informada, não pode se repetir. */
    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = Valida.textoOpcional(unidade);
    }

    public void setCodigo(String codigo) {
        this.codigo = Valida.textoObrigatorio(codigo, "o código do imóvel");
    }

    public TipoImovel getTipo() {
        return tipo;
    }

    public void setTipo(TipoImovel tipo) {
        this.tipo = Valida.obrigatorio(tipo, "o tipo do imóvel");
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = Valida.textoObrigatorio(endereco, "o endereço");
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = Valida.textoOpcional(bairro);
    }

    public String getGrupo() {
        return grupo;
    }

    /** Ex.: "Residencial Dom Luciano". Usado para filtrar a lista. */
    public void setGrupo(String grupo) {
        this.grupo = Valida.textoOpcional(grupo);
    }

    public BigDecimal getValorAluguel() {
        return valorAluguel;
    }

    public void setValorAluguel(BigDecimal valorAluguel) {
        this.valorAluguel = Valida.positivo(valorAluguel, "o valor do aluguel");
    }

    public BigDecimal getTaxaLuz() {
        return taxaLuz;
    }

    public void setTaxaLuz(BigDecimal taxaLuz) {
        this.taxaLuz = Valida.naoNegativo(taxaLuz, "a taxa de luz");
    }

    public BigDecimal getValorIptu() {
        return valorIptu;
    }

    public void setValorIptu(BigDecimal valorIptu) {
        this.valorIptu = Valida.naoNegativo(valorIptu, "o valor do IPTU");
    }

    public SituacaoImovel getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoImovel situacao) {
        this.situacao = Valida.obrigatorio(situacao, "a situação do imóvel");
    }

    @Override
    public String toString() {
        return codigo + " · " + endereco;
    }
}
