package br.org.fmc.model;

/**
 * [D4] BOARD-43 · Onde o dinheiro da Fundação fica: no Caixa (dinheiro e cheque, na própria
 * Fundação) ou no Banco (PIX, transferência e boleto). O Banco é o total das contas bancárias:
 * por enquanto não separamos conta por conta.
 */
public enum Conta {
    CAIXA("Caixa"),
    BANCO("Banco");

    private final String descricao;

    Conta(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Dinheiro e cheque vão para o Caixa; o resto, para o Banco (regra da Fundação). */
    public static Conta de(FormaPagamento forma) {
        return forma.vaiParaCaixa() ? CAIXA : BANCO;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
