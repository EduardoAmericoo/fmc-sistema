package br.org.fmc.model;

public enum SituacaoAluguel {
    A_VENCER("A vencer"),
    EM_TOLERANCIA("Em tolerância"),
    EM_ATRASO("Em atraso"),
    PAGO("Pago");

    private final String descricao;

    SituacaoAluguel(String descricao) {
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
