package br.org.fmc.model;

/** O que um usuário pode fazer no sistema. Cada perfil libera um conjunto de ações. */
public enum Acao {
    GERENCIAR_USUARIOS,
    CADASTRAR_IMOVEL,
    CADASTRAR_INQUILINO,
    CADASTRAR_CONTRATO,
    GERAR_ALUGUEIS,
    RECEBER_ALUGUEL,
    VER_CADASTROS,
    VER_FINANCEIRO
}
