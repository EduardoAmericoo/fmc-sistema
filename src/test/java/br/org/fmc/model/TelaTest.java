package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BOARD-54 · Cada perfil vê só as telas que precisa. */
class TelaTest {

    private List<Tela> telasDo(Perfil perfil) {
        return Arrays.stream(Tela.values()).filter(t -> t.liberadaPara(perfil)).toList();
    }

    @Test
    void existemTresPerfisFixos() {
        assertEquals(List.of(Perfil.ADMINISTRADOR, Perfil.SECRETARIA, Perfil.FINANCEIRO), List.of(Perfil.values()));
    }

    @Test
    void administradorVeTodasAsTelas() {
        assertEquals(List.of(Tela.values()), telasDo(Perfil.ADMINISTRADOR));
    }

    @Test
    void secretariaEFinanceiroNaoVeemUsuarios() {
        List<Tela> esperadas = List.of(Tela.PAINEL, Tela.ALUGUEIS, Tela.IMOVEIS, Tela.INQUILINOS, Tela.CONTRATOS);
        assertEquals(esperadas, telasDo(Perfil.SECRETARIA));
        assertEquals(esperadas, telasDo(Perfil.FINANCEIRO));
    }

    @Test
    void financeiroSoConsultaSecretariaOpera() {
        assertFalse(Perfil.FINANCEIRO.permite(Acao.CADASTRAR_IMOVEL));
        assertFalse(Perfil.FINANCEIRO.permite(Acao.RECEBER_ALUGUEL));
        assertTrue(Perfil.SECRETARIA.permite(Acao.CADASTRAR_IMOVEL));
        assertTrue(Perfil.SECRETARIA.permite(Acao.RECEBER_ALUGUEL));
    }

    @Test
    void usuarioDesativadoNaoAbreNemOPainel() {
        Usuario u = new Usuario("Admin", "admin@exemplo.org", Perfil.ADMINISTRADOR);
        assertTrue(u.podeAbrir(Tela.USUARIOS));
        u.setAtivo(false);
        assertFalse(u.podeAbrir(Tela.PAINEL));
    }

    @Test
    void achaATelaPeloNomeDoArquivo() {
        assertEquals(Tela.IMOVEIS, Tela.porArquivo("imoveis").orElseThrow());
        assertTrue(Tela.porArquivo("nao-existe").isEmpty());
    }
}
