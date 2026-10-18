package br.org.fmc.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Perfis fixos do MVP. Cada perfil já vem com as suas ações liberadas.
 * (Permissões editáveis pessoa a pessoa ficaram para depois do MVP.)
 */
public enum Perfil {
    ADMINISTRADOR("Administrador", EnumSet.allOf(Acao.class)),
    SECRETARIA("Secretaria", EnumSet.of(
            Acao.VER_CADASTROS, Acao.VER_FINANCEIRO,
            Acao.CADASTRAR_IMOVEL, Acao.CADASTRAR_INQUILINO, Acao.CADASTRAR_CONTRATO,
            Acao.GERAR_ALUGUEIS, Acao.RECEBER_ALUGUEL)),
    FINANCEIRO("Financeiro", EnumSet.of(Acao.VER_CADASTROS, Acao.VER_FINANCEIRO));

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
