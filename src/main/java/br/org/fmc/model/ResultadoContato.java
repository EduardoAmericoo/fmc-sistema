package br.org.fmc.model;

/** [D7] BOARD-64 · O que deu uma ligação de cobrança, escolhido numa lista. */
public enum ResultadoContato {
    NAO_ATENDEU("Não atendeu"),
    PROMETEU_PAGAR("Prometeu pagar"),
    SEM_ACORDO("Sem acordo"),
    TELEFONE_ERRADO("Telefone errado");

    private final String rotulo;

    ResultadoContato(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
