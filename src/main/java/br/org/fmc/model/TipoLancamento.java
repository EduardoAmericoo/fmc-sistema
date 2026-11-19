package br.org.fmc.model;

/** [D4] BOARD-43 · De onde veio um lançamento do Caixa ou do Banco. */
public enum TipoLancamento {
    /** O valor que o Administrador informa uma vez, para o saldo começar certo. */
    SALDO_INICIAL("Saldo inicial"),
    /** Recebimento de aluguel: gerado sozinho junto com o recibo. */
    RECEBIMENTO_ALUGUEL("Recebimento de aluguel"),
    /** Saída que desfaz um recebimento (cancelamento de recibo, D10). */
    ESTORNO("Estorno"),
    /**
     * Dinheiro e cheques levados do Caixa para o Banco: uma saída no Caixa e uma entrada no
     * Banco, de mesmo valor, gravadas juntas. O total da Fundação não muda.
     */
    DEPOSITO("Depósito do Caixa no Banco"),
    /** O contrário do depósito: dinheiro sacado do Banco para o Caixa (troco, pagamento pequeno). */
    SAQUE("Saque do Banco para o Caixa");

    private final String descricao;

    TipoLancamento(String descricao) {
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
