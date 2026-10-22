package br.org.fmc.model;

/**
 * O que um usuário pode fazer no sistema. Cada perfil libera um conjunto de ações,
 * e o Administrador pode ajustar esse conjunto pessoa a pessoa (BOARD-41).
 *
 * A descrição é o texto da caixinha na tela de Usuários.
 */
public enum Acao {
    GERENCIAR_USUARIOS("Gerenciar usuários e permissões"),
    CADASTRAR_IMOVEL("Cadastrar e editar imóveis"),
    CADASTRAR_INQUILINO("Cadastrar e editar inquilinos"),
    CADASTRAR_CONTRATO("Cadastrar contratos"),
    GERAR_ALUGUEIS("Gerar os aluguéis do mês"),
    RECEBER_ALUGUEL("Receber aluguel e emitir recibo"),
    VER_CADASTROS("Ver imóveis, inquilinos e contratos"),
    VER_FINANCEIRO("Ver aluguéis e números financeiros");

    private final String descricao;

    Acao(String descricao) {
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
