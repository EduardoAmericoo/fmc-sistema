package br.org.fmc.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Perfis do sistema. Cada perfil é o ponto de partida: já vem com as suas ações
 * liberadas, e o Administrador pode ajustar pessoa a pessoa (BOARD-41).
 *
 * Administrador: tudo. Secretaria: tudo, menos a tela de Usuários.
 * Financeiro: consulta e gera as cobranças mensais, sem receber pagamentos ou editar cadastros.
 */
public enum Perfil {
    ADMINISTRADOR("Administrador", EnumSet.allOf(Acao.class)),
    SECRETARIA("Secretaria", EnumSet.of(
            Acao.VER_IMOVEIS, Acao.CRIAR_IMOVEL, Acao.EDITAR_IMOVEL, Acao.ARQUIVAR_IMOVEL, Acao.EXCLUIR_IMOVEL,
            Acao.VER_INQUILINOS, Acao.CRIAR_INQUILINO, Acao.EDITAR_INQUILINO, Acao.ARQUIVAR_INQUILINO,
            Acao.VER_CONTRATOS, Acao.CRIAR_CONTRATO, Acao.PRORROGAR_CONTRATO, Acao.ENCERRAR_CONTRATO,
            Acao.VER_ALUGUEIS, Acao.GERAR_ALUGUEIS, Acao.RECEBER_ALUGUEL,
            Acao.REGISTRAR_CONTATO_COBRANCA)),
    FINANCEIRO("Financeiro", EnumSet.of(
            Acao.VER_IMOVEIS, Acao.VER_INQUILINOS, Acao.VER_CONTRATOS, Acao.VER_ALUGUEIS,
            Acao.GERAR_ALUGUEIS, Acao.REGISTRAR_CONTATO_COBRANCA));

    private final String descricao;
    private final Set<Acao> permissoes;

    Perfil(String descricao, Set<Acao> permissoes) {
        this.descricao = descricao;
        this.permissoes = Collections.unmodifiableSet(permissoes);
    }

    public boolean permite(Acao acao) {
        return permissoes.contains(acao);
    }

    public Set<Acao> getPermissoes() {
        return permissoes;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
