package br.org.fmc.model;

/** Vistorias com fotos: o imóvel na entrega das chaves (entrada) e na devolução (saída). */
public enum EtapaVistoria {
    ENTRADA("Vistoria de entrada", "Como a Fundação entregou o imóvel"),
    SAIDA("Vistoria de saída", "Como o inquilino devolveu o imóvel");

    private final String descricao;
    private final String explicacao;

    EtapaVistoria(String descricao, String explicacao) {
        this.descricao = descricao;
        this.explicacao = explicacao;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getExplicacao() {
        return explicacao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
