package br.org.fmc.model;

/**
 * [D7] BOARD-64 · O que deu uma ligação de cobrança, escolhido numa lista.
 * BOARD-189: também as mensagens mandadas pela janela de contato (e-mail e WhatsApp), que o
 * sistema registra sozinho no histórico e por isso não aparecem entre as opções da ligação.
 */
public enum ResultadoContato {
    NAO_ATENDEU("Não atendeu", true),
    PROMETEU_PAGAR("Prometeu pagar", true),
    SEM_ACORDO("Sem acordo", true),
    TELEFONE_ERRADO("Telefone errado", true),
    EMAIL_ENVIADO("E-mail enviado", false),
    WHATSAPP_ENVIADO("WhatsApp enviado", false);

    private final String rotulo;
    private final boolean daLigacao;

    ResultadoContato(String rotulo, boolean daLigacao) {
        this.rotulo = rotulo;
        this.daLigacao = daLigacao;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** true para as opções que a pessoa escolhe depois de ligar; false para as mensagens. */
    public boolean isDaLigacao() {
        return daLigacao;
    }

    @Override
    public String toString() {
        return rotulo;
    }
}
