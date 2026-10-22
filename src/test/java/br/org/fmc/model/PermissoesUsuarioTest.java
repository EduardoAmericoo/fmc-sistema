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
        acoes.add(Acao.EDITAR_PERMISSOES);
        secretaria.personalizarPermissoes(acoes);

        assertTrue(secretaria.temPermissoesPersonalizadas());
        assertFalse(secretaria.pode(Acao.RECEBER_ALUGUEL));
        assertTrue(secretaria.pode(Acao.EDITAR_PERMISSOES));
        assertTrue(secretaria.podeAbrir(Tela.USUARIOS));
    }

    @Test
    void tirarOVerDeUmaTelaEscondeSoEssaTela() {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.removeAll(Set.of(Acao.VER_INQUILINOS, Acao.CRIAR_INQUILINO, Acao.EDITAR_INQUILINO));
        secretaria.personalizarPermissoes(acoes);

        assertFalse(secretaria.podeAbrir(Tela.INQUILINOS));
        assertTrue(secretaria.podeAbrir(Tela.IMOVEIS));
        assertTrue(secretaria.podeAbrir(Tela.ALUGUEIS));
        assertTrue(secretaria.podeAbrir(Tela.PAINEL));
    }

    @Test
    void jovemAprendizVeEEditaMasNaoCriaInquilino() {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.remove(Acao.CRIAR_INQUILINO);
        secretaria.personalizarPermissoes(acoes);

        assertTrue(secretaria.podeAbrir(Tela.INQUILINOS));
        assertTrue(secretaria.pode(Acao.EDITAR_INQUILINO));
        assertFalse(secretaria.pode(Acao.CRIAR_INQUILINO));
    }

    @Test
    void criarOuEditarTrazJuntoOVerDaTela() {
        secretaria.personalizarPermissoes(Set.of(Acao.EDITAR_IMOVEL));
        assertTrue(secretaria.pode(Acao.VER_IMOVEIS));
        assertTrue(secretaria.podeAbrir(Tela.IMOVEIS));
        assertFalse(secretaria.pode(Acao.CRIAR_IMOVEL));
    }

    @Test
    void financeiroSoConsulta() {
        Usuario financeiro = new Usuario("Contabilidade", "contabilidade@exemplo.org", Perfil.FINANCEIRO);
        for (Tela tela : Tela.values()) {
            assertEquals(tela != Tela.USUARIOS, financeiro.podeAbrir(tela), tela.name());
        }
        for (Acao acao : Acao.values()) {
            assertEquals(acao.getRequisito() == null && acao != Acao.VER_USUARIOS, financeiro.pode(acao), acao.name());
        }
    }

    @Test
    void escolhaIgualAoPerfilVoltaAoPadrao() {
        secretaria.personalizarPermissoes(Set.of(Acao.VER_INQUILINOS));
        secretaria.personalizarPermissoes(Perfil.SECRETARIA.getPermissoes());
        assertFalse(secretaria.temPermissoesPersonalizadas());
    }

    @Test
    void restaurarEMudarDePerfilDescartamOAjuste() {
        secretaria.personalizarPermissoes(Set.of());
        assertFalse(secretaria.pode(Acao.VER_INQUILINOS));
        secretaria.restaurarPermissoesDoPerfil();
        assertTrue(secretaria.pode(Acao.VER_INQUILINOS));

        secretaria.personalizarPermissoes(Set.of());
        secretaria.setPerfil(Perfil.FINANCEIRO);
        assertEquals(Perfil.FINANCEIRO.getPermissoes(), secretaria.getPermissoes());
    }

    @Test
    void usuarioInativoNaoPodeNadaMesmoComAjuste() {
        secretaria.personalizarPermissoes(EnumSet.allOf(Acao.class));
        secretaria.setAtivo(false);
        assertFalse(secretaria.pode(Acao.VER_INQUILINOS));
    }
}
