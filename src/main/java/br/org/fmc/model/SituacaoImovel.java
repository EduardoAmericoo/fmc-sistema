package br.org.fmc.model;

public enum SituacaoImovel {
    VAGO("Vago"),
    OCUPADO("Ocupado"),
    EM_MANUTENCAO("Em manutenção");

    private final String descricao;

    SituacaoImovel(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
