package br.org.fmc.model;

public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    CHEQUE("Cheque"),
    PIX("PIX"),
    TRANSFERENCIA("Transferência");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    /** Regra da Fundação: dinheiro e cheque entram no Caixa; PIX e transferência, no Banco. */
    public boolean vaiParaCaixa() {
        return this == DINHEIRO || this == CHEQUE;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
