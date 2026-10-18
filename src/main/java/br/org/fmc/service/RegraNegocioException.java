package br.org.fmc.service;

import java.util.List;

/**
 * Erro de regra de negócio, com mensagens prontas para mostrar na tela.
 * Ex.: "A renda familiar do inquilino precisa ser pelo menos 3 vezes o aluguel."
 *
 * Os controllers capturam esta exceção e mostram getErros() para o usuário.
 */
public class RegraNegocioException extends RuntimeException {

    private final List<String> erros;

    public RegraNegocioException(String erro) {
        this(List.of(erro));
    }

    public RegraNegocioException(List<String> erros) {
        super(String.join(" ", erros));
        this.erros = List.copyOf(erros);
    }

    public List<String> getErros() {
        return erros;
    }
}
