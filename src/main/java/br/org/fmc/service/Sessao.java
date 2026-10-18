package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Tela;
import br.org.fmc.model.Usuario;

/**
 * Guarda quem está logado no sistema.
 *
 * O login (ServicoAcesso) chama iniciar(); os services chamam exigir() antes
 * de uma ação protegida; as telas usam usuario() para esconder menus e botões.
 */
public final class Sessao {

    private static Usuario usuarioAtual;

    private Sessao() {
    }

    public static void iniciar(Usuario usuario) {
        if (usuario == null || !usuario.isAtivo()) {
            throw new AcessoNegadoException("Usuário inativo ou inexistente.");
        }
        usuarioAtual = usuario;
    }

    public static void encerrar() {
        usuarioAtual = null;
    }

    public static boolean estaLogado() {
        return usuarioAtual != null;
    }

    /** Usuário logado. Lança AcessoNegadoException se ninguém estiver logado. */
    public static Usuario usuario() {
        if (usuarioAtual == null) {
            throw new AcessoNegadoException("Faça login para continuar.");
        }
        return usuarioAtual;
    }

    /** O usuário logado pode fazer esta ação? (false se ninguém estiver logado) */
    public static boolean pode(Acao acao) {
        return usuarioAtual != null && usuarioAtual.pode(acao);
    }

    /** O usuário logado pode abrir esta tela do menu? (false se ninguém estiver logado) */
    public static boolean podeAbrir(Tela tela) {
        return usuarioAtual != null && usuarioAtual.podeAbrir(tela);
    }

    /** Para no service se o usuário logado não tiver a permissão. */
    public static void exigir(Acao acao) {
        if (!usuario().pode(acao)) {
            throw new AcessoNegadoException("O seu perfil não tem permissão para esta ação.");
        }
    }
}
