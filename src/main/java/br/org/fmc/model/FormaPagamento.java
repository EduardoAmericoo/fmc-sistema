package br.org.fmc.model;

public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    BOLETO("Boleto"),
    PIX("PIX"),
    TRANSFERENCIA("Transferência"),
    /** Mantido para leitura de pagamentos históricos. */
    CHEQUE("Cheque");

    public static java.util.List<FormaPagamento> disponiveis() {
        return java.util.List.of(DINHEIRO, BOLETO, PIX, TRANSFERENCIA);
    }

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    /** Dinheiro e cheques históricos entram no Caixa; boleto, PIX e transferência, no Banco. */
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
