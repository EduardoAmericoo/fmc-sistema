package br.org.fmc.model;

/**
 * [D7] BOARD-64 · Em que pé está a cobrança de um inquilino da lista, pelo último contato.
 * A ordem das constantes é a ordem de urgência da lista: primeiro quem quebrou a promessa,
 * depois quem nunca foi contatado, e por último quem prometeu pagar e ainda está no prazo.
 */
public enum SituacaoContato {
    PROMESSA_NAO_CUMPRIDA,
    NUNCA_CONTATADO,
    SEM_RETORNO,
    PROMESSA_VENCENDO,
    PROMESSA_NO_PRAZO
}
