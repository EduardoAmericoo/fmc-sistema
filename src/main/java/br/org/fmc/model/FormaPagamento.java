package br.org.fmc.model;

public enum FormaPagamento {
    DINHEIRO("Dinheiro"),
    CHEQUE("Cheque"),
    BOLETO_BANCARIO("Boleto bancário"),
    PIX("PIX"),
    TRANSFERENCIA("Transferência");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    /**
     * Regra da Fundação: dinheiro e cheque entram no Caixa; boleto, PIX e transferência, no Banco.
     * (Cheque está no protótipo aprovado; boleto foi incluído pela BOARD-39. Confirmar com o cliente.)
     */
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
