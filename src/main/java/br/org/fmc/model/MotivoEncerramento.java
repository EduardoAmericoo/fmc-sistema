package br.org.fmc.model;

/** Por que o contrato foi encerrado (BOARD-174). */
public enum MotivoEncerramento {
    FIM_DO_PRAZO("Fim do prazo, sem renovação"),
    SAIDA_DO_INQUILINO("Inquilino pediu para sair antes do fim"),
    DECISAO_DA_FUNDACAO("Decisão da Fundação (ex.: falta de pagamento)"),
    OUTRO("Outro motivo");

    private final String descricao;

    MotivoEncerramento(String descricao) {
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
