package br.org.fmc.service;

/** O usuário logado não tem permissão para a ação pedida. */
public class AcessoNegadoException extends RegraNegocioException {

    public AcessoNegadoException(String erro) {
        super(erro);
    }
}
