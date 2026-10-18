package br.org.fmc.model;

import java.math.BigDecimal;

/** Um dos imóveis do patrimônio da Fundação. */
public class Imovel {

    private Long id;
    private String codigo;
    private TipoImovel tipo;
    private String endereco;
    private String bairro;
    private String grupo;
    private BigDecimal valorAluguel;
    private BigDecimal taxaLuz = BigDecimal.ZERO;
    private BigDecimal valorIptu = BigDecimal.ZERO;
    private SituacaoImovel situacao = SituacaoImovel.VAGO;

    public Imovel(String codigo, TipoImovel tipo, String endereco, BigDecimal valorAluguel) {
        setCodigo(codigo);
        setTipo(tipo);
        setEndereco(endereco);
        setValorAluguel(valorAluguel);
    }

    /** Pode receber um contrato novo: só se estiver vago. */
    public boolean estaDisponivel() {
        return situacao == SituacaoImovel.VAGO;
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
