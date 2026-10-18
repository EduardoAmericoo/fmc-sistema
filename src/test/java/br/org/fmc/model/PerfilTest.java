package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerfilTest {

    @Test
    void cadaPerfilTemSuasPermissoes() {
        assertTrue(Perfil.ADMINISTRADOR.permite(Acao.GERENCIAR_USUARIOS));
        assertTrue(Perfil.SECRETARIA.permite(Acao.RECEBER_ALUGUEL));
        assertFalse(Perfil.SECRETARIA.permite(Acao.GERENCIAR_USUARIOS));
        assertTrue(Perfil.FINANCEIRO.permite(Acao.VER_FINANCEIRO));
        assertFalse(Perfil.FINANCEIRO.permite(Acao.RECEBER_ALUGUEL));
    }

    @Test
    void usuarioInativoNaoPodeNadaEEmailFicaMinusculo() {
        Usuario u = new Usuario("Admin", "Admin@Exemplo.org", Perfil.ADMINISTRADOR);
        assertEquals("admin@exemplo.org", u.getEmail());
        u.setAtivo(false);
        assertFalse(u.pode(Acao.VER_CADASTROS));
        assertThrows(IllegalArgumentException.class, () -> u.setEmail("sem-arroba"));
    }

    @Test
    void formaDePagamentoDefineCaixaOuBanco() {
        assertTrue(FormaPagamento.DINHEIRO.vaiParaCaixa());
        assertTrue(FormaPagamento.CHEQUE.vaiParaCaixa());
        assertFalse(FormaPagamento.PIX.vaiParaCaixa());
        assertFalse(FormaPagamento.TRANSFERENCIA.vaiParaCaixa());
    }
}
