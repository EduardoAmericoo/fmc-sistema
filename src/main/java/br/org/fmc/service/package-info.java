/**
 * Regras de negócio: o que o sistema decide e calcula.
 * Ex.: ServicoContratos.cadastrar() (renda 3x o aluguel, imóvel vago, garantia),
 * ServicoAlugueis.gerarMes(), ServicoRecebimento.receber() (encargos e recibo).
 *
 * As telas chamam os services; os services usam os repositories.
 * Um service nunca abre conexão com o banco diretamente.
 */
package br.org.fmc.service;
