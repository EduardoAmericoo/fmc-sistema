package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SessaoTest {

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    @Test
    void semLoginNaoPodeNada() {
        assertFalse(Sessao.pode(Acao.VER_CADASTROS));
        assertThrows(AcessoNegadoException.class, Sessao::usuario);
    }

    @Test
    void exigirBarraQuemNaoTemPermissao() {
        Sessao.iniciar(new Usuario("Contabilidade", "contabilidade@exemplo.org", Perfil.FINANCEIRO));
        assertDoesNotThrow(() -> Sessao.exigir(Acao.VER_FINANCEIRO));
        assertThrows(AcessoNegadoException.class, () -> Sessao.exigir(Acao.RECEBER_ALUGUEL));
    }

    @Test
    void usuarioInativoNaoEntra() {
        Usuario u = new Usuario("Antigo", "antigo@exemplo.org", Perfil.SECRETARIA);
        u.setAtivo(false);
        assertThrows(AcessoNegadoException.class, () -> Sessao.iniciar(u));
    }
}
