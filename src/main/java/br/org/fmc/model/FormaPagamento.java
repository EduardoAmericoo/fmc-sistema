package br.org.fmc.model;

public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    BOLETO_BANCARIO("Boleto bancário"),
    PIX("PIX"),
    TRANSFERENCIA("Transferência");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    /** Regra da Fundação: dinheiro entra no Caixa; boleto, PIX e transferência, no Banco. */
    public boolean vaiParaCaixa() {
        return this == DINHEIRO;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
