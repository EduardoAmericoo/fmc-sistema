package br.org.fmc.service;

import br.org.fmc.model.EstadoCivil;

/**
 * O que foi digitado no formulário de inquilino, ainda sem validar (BOARD-35).
 * Os valores em dinheiro vêm como texto (ex.: "2.500,00"); quem confere e
 * converte é o ServicoInquilinos.
 *
 * Os campos conjuge* só são usados para casado(a) ou união estável.
 */
public record DadosInquilino(
        String nome,
        String cpf,
        String telefone,
        String email,
        EstadoCivil estadoCivil,
        String profissao,
        String localTrabalho,
        String rendaMensal,
        String outrasRendas,
        String conjugeNome,
        String conjugeCpf,
        String conjugeRenda) {
}
