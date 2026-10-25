package br.org.fmc.model;

/**
 * O que um usuário pode fazer no sistema, tela por tela (BOARD-41).
 * Cada perfil libera um conjunto de ações, e o Administrador pode ajustar esse
 * conjunto pessoa a pessoa, marcando ou desmarcando as caixinhas na tela de Usuários.
 *
 * Regra de ouro: cada botão de uma tela corresponde a UMA ação. A tela chama
 * Permissoes.mostrarSe(botao, Acao.X) e o service chama Sessao.exigir(Acao.X).
 *
 * Toda ação que não é "ver" depende do "ver" da mesma tela (requisito): não faz
 * sentido poder criar inquilino sem conseguir abrir a tela de Inquilinos.
 */
public enum Acao {
    VER_IMOVEIS("Imóveis", "Ver a lista de imóveis", null),
    CRIAR_IMOVEL("Imóveis", "Cadastrar imóvel novo", VER_IMOVEIS),
    EDITAR_IMOVEL("Imóveis", "Editar dados de imóvel", VER_IMOVEIS),
    EXCLUIR_IMOVEL("Imóveis", "Excluir imóvel", VER_IMOVEIS),

    VER_INQUILINOS("Inquilinos", "Ver a lista de inquilinos", null),
    CRIAR_INQUILINO("Inquilinos", "Cadastrar inquilino novo", VER_INQUILINOS),
    EDITAR_INQUILINO("Inquilinos", "Editar dados de inquilino", VER_INQUILINOS),

    VER_CONTRATOS("Contratos", "Ver a lista de contratos", null),
    CRIAR_CONTRATO("Contratos", "Cadastrar contrato novo", VER_CONTRATOS),

    VER_ALUGUEIS("Aluguéis", "Ver aluguéis e números financeiros", null),
    GERAR_ALUGUEIS("Aluguéis", "Gerar os aluguéis do mês", VER_ALUGUEIS),
    RECEBER_ALUGUEL("Aluguéis", "Receber aluguel e emitir recibo", VER_ALUGUEIS),

    VER_USUARIOS("Usuários", "Ver a lista de usuários", null),
    CRIAR_USUARIO("Usuários", "Criar usuário novo", VER_USUARIOS),
    EDITAR_PERMISSOES("Usuários", "Ajustar permissões de usuários", VER_USUARIOS),
    GERENCIAR_ACESSO("Usuários", "Bloquear usuário e gerar senha provisória", VER_USUARIOS);

    private final String tela;
    private final String descricao;
    private final Acao requisito;

    Acao(String tela, String descricao, Acao requisito) {
        this.tela = tela;
        this.descricao = descricao;
        this.requisito = requisito;
    }

    /** Nome da tela a que a ação pertence (agrupa as caixinhas na tela de Usuários). */
    public String getTela() {
        return tela;
    }

    /** Texto da caixinha. */
    public String getDescricao() {
        return descricao;
    }

    /** O "ver" de que esta ação depende; null quando ela própria é o "ver". */
    public Acao getRequisito() {
        return requisito;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
