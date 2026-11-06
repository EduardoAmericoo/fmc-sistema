package br.org.fmc.model;

import java.time.LocalDateTime;

/** Registro imutável de uma tentativa de contato durante a cobrança de um contrato. */
public record ContatoCobranca(Long id, long contratoId, LocalDateTime registradoEm, String resultado, Usuario feitoPor) {

    public ContatoCobranca {
        if (contratoId <= 0) {
            throw new IllegalArgumentException("O contrato do contato é inválido.");
        }
        registradoEm = Valida.obrigatorio(registradoEm, "a data do contato");
        resultado = Valida.textoObrigatorio(resultado, "o resultado do contato");
        feitoPor = Valida.obrigatorio(feitoPor, "quem registrou o contato");
    }
}
