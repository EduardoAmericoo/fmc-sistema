package br.org.fmc.model;

/** [D4] BOARD-43 · De onde veio um lançamento do Caixa ou do Banco. */
public enum TipoLancamento {
    /** O valor que o Administrador informa uma vez, para o saldo começar certo. */
    SALDO_INICIAL("Saldo inicial"),
    /** Recebimento de aluguel: gerado sozinho junto com o recibo. */
    RECEBIMENTO_ALUGUEL("Recebimento de aluguel"),
    /** Saída que desfaz um recebimento (cancelamento de recibo, D10). */
    ESTORNO("Estorno");

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
