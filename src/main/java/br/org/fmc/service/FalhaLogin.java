package br.org.fmc.service;

import java.time.LocalDateTime;
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

    /** Até quando o acesso está em pausa por tentativas erradas; null se não está. */
    private final LocalDateTime pausaAte;

    public FalhaLogin(Map<Campo, String> mensagens) {
        this(mensagens, null);
    }

    public FalhaLogin(Map<Campo, String> mensagens, LocalDateTime pausaAte) {
        super(new ArrayList<>(mensagens.values()));
        this.mensagens = new EnumMap<>(mensagens);
        this.pausaAte = pausaAte;
    }

    public FalhaLogin(Campo campo, String mensagem) {
        this(Map.of(campo, mensagem));
    }

    /** Até quando o acesso está em pausa (para a tela mostrar o tempo diminuindo); null se não está. */
    public LocalDateTime getPausaAte() {
        return pausaAte;
    }

    /** A mensagem deste campo, ou null se o campo está certo. */
    public String mensagem(Campo campo) {
        return mensagens.get(campo);
    }
}
