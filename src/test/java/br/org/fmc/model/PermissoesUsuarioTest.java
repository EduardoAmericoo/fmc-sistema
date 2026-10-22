package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** BOARD-41 · Permissões ajustadas pessoa a pessoa. */
class PermissoesUsuarioTest {

    private final Usuario secretaria = new Usuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA);

    @Test
    void semAjusteSegueOPerfil() {
        assertFalse(secretaria.temPermissoesPersonalizadas());
        assertEquals(Perfil.SECRETARIA.getPermissoes(), secretaria.getPermissoes());
    }

    @Test
    void ajusteTiraEDaPermissoesAlemDoPerfil() {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.remove(Acao.RECEBER_ALUGUEL);
        acoes.add(Acao.GERENCIAR_USUARIOS);
        secretaria.personalizarPermissoes(acoes);

        assertTrue(secretaria.temPermissoesPersonalizadas());
        assertFalse(secretaria.pode(Acao.RECEBER_ALUGUEL));
        assertTrue(secretaria.pode(Acao.GERENCIAR_USUARIOS));
        assertTrue(secretaria.podeAbrir(Tela.USUARIOS));
    }

    @Test
    void tirarVerCadastrosEscondeAsTelasDeCadastro() {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.remove(Acao.VER_CADASTROS);
        secretaria.personalizarPermissoes(acoes);

        assertFalse(secretaria.podeAbrir(Tela.IMOVEIS));
        assertTrue(secretaria.podeAbrir(Tela.ALUGUEIS));
        assertTrue(secretaria.podeAbrir(Tela.PAINEL));
    }

    @Test
    void escolhaIgualAoPerfilVoltaAoPadrao() {
        secretaria.personalizarPermissoes(Set.of(Acao.VER_CADASTROS));
        secretaria.personalizarPermissoes(Perfil.SECRETARIA.getPermissoes());
        assertFalse(secretaria.temPermissoesPersonalizadas());
    }

    @Test
    void restaurarEMudarDePerfilDescartamOAjuste() {
        secretaria.personalizarPermissoes(Set.of());
        assertFalse(secretaria.pode(Acao.VER_CADASTROS));
        secretaria.restaurarPermissoesDoPerfil();
        assertTrue(secretaria.pode(Acao.VER_CADASTROS));

        secretaria.personalizarPermissoes(Set.of());
        secretaria.setPerfil(Perfil.FINANCEIRO);
        assertEquals(Perfil.FINANCEIRO.getPermissoes(), secretaria.getPermissoes());
    }

    @Test
    void usuarioInativoNaoPodeNadaMesmoComAjuste() {
        secretaria.personalizarPermissoes(EnumSet.allOf(Acao.class));
        secretaria.setAtivo(false);
        assertFalse(secretaria.pode(Acao.VER_CADASTROS));
    }
}
