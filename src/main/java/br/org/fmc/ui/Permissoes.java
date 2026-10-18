package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.service.Sessao;
import javafx.scene.Node;

/**
 * Mostra ou esconde partes da tela conforme o perfil de quem entrou (BOARD-54).
 *
 * Uso no initialize() do controller da sua tela:
 *
 * <pre>
 *     Permissoes.mostrarSe(botaoNovoImovel, Acao.CADASTRAR_IMOVEL);
 *     Permissoes.mostrarSe(botaoReceber, Acao.RECEBER_ALUGUEL);
 * </pre>
 *
 * Esconder o botão é só para a tela ficar limpa. A proteção de verdade fica no
 * service, que chama Sessao.exigir(acao) antes de fazer a ação.
 */
public final class Permissoes {

    private Permissoes() {
    }

    /** O componente aparece só se o usuário logado puder fazer a ação; senão some sem deixar espaço vazio. */
    public static void mostrarSe(Node componente, Acao acao) {
        boolean pode = Sessao.pode(acao);
        componente.setVisible(pode);
        componente.setManaged(pode);
    }
}
