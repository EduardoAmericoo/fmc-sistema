package br.org.fmc.service;

import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.PrazoContrato;
import java.time.LocalDate;

/**
 * O que foi preenchido no formulário de contrato, ainda sem validar (BOARD-36).
 * Quem confere tudo é o ServicoContratos.
 *
 * A garantia vem em fiador OU em caucao (o outro fica null), conforme o que
 * foi escolhido na tela.
 */
public record DadosContrato(
        Long imovelId,
        Long inquilinoId,
        PrazoContrato prazo,
        LocalDate entregaChaves,
        Fiador fiador,
        Caucao caucao) {

    /** Dados do fiador. A renda vem como texto (ex.: "4.500,00"). */
    public record Fiador(
            String nome,
            String cpf,
            String telefone,
            EstadoCivil estadoCivil,
            String renda,
            String endereco,
            String conjugeNome,
            String conjugeCpf) {
    }

    /** Dados da caução. O valor vem como texto (ex.: "4.500,00"). O recibo é opcional. */
    public record Caucao(
            String valor,
            LocalDate recebidaEm,
            FormaPagamento forma,
            String recibo) {
    }
}
