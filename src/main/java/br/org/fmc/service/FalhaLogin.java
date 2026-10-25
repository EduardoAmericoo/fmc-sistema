package br.org.fmc.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * Login recusado, com a mensagem de cada campo separada: a tela mostra cada uma
 * embaixo do campo dela (e-mail ou senha) ou, quando vale para os dois, embaixo do botão.
 * Ex.: { EMAIL: "Informe o e-mail.", SENHA: "Informe a senha." }
 */
public class FalhaLogin extends RegraNegocioException {

    /** Onde a mensagem aparece na tela de login. */
    public enum Campo { EMAIL, SENHA, GERAL }

    private final Map<Campo, String> mensagens;

    public FalhaLogin(Map<Campo, String> mensagens) {
        super(new ArrayList<>(mensagens.values()));
        this.mensagens = new EnumMap<>(mensagens);
    }

    public FalhaLogin(Campo campo, String mensagem) {
        this(Map.of(campo, mensagem));
    }

    /** A mensagem deste campo, ou null se o campo está certo. */
    public String mensagem(Campo campo) {
        return mensagens.get(campo);
    }
}
