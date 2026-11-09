package br.org.fmc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro imutável de uma tentativa de contato durante a cobrança de um contrato ([D7] BOARD-64).
 * O tipo diz o que deu (não atendeu, prometeu pagar...); quem prometeu pagar tem a data prometida.
 * Contatos registrados antes das opções existirem têm tipo nulo e só a observação.
 */
public record ContatoCobranca(Long id, long contratoId, LocalDateTime registradoEm, ResultadoContato tipo,
        LocalDate dataPrometida, String observacao, Usuario feitoPor) {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ContatoCobranca {
        if (contratoId <= 0) {
            throw new IllegalArgumentException("O contrato do contato é inválido.");
        }
        registradoEm = Valida.obrigatorio(registradoEm, "a data do contato");
        feitoPor = Valida.obrigatorio(feitoPor, "quem registrou o contato");
        observacao = observacao == null ? "" : observacao.strip();
        if (tipo == null && observacao.isEmpty()) {
            throw new IllegalArgumentException("Informe o resultado do contato.");
        }
        if (tipo == ResultadoContato.PROMETEU_PAGAR && dataPrometida == null) {
            throw new IllegalArgumentException("Informe a data que o inquilino prometeu pagar.");
        }
        if (tipo != ResultadoContato.PROMETEU_PAGAR) {
            dataPrometida = null;
        }
    }

    public boolean ehPromessa() {
        return tipo == ResultadoContato.PROMETEU_PAGAR;
    }

    /** Ex.: "Prometeu pagar até 15/11/2026 · vai receber o 13º" ou "Não atendeu". */
    public String descricao() {
        String base = tipo == null ? "" : tipo.getRotulo()
                + (ehPromessa() ? " até " + dataPrometida.format(DATA) : "");
        if (observacao.isEmpty()) {
            return base;
        }
        return base.isEmpty() ? observacao : base + " · " + observacao;
    }
}
