package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Tela;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.UsuarioRepository;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-41 · O Administrador ajusta as permissões de cada pessoa. */
class PermissoesEditaveisTest {

    @TempDir
    Path pasta;

    private final UsuarioRepository repo = new UsuarioRepository();
    private final ServicoAcesso servico = new ServicoAcesso(repo);
    private Usuario secretaria;

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        servico.garantirAdministradorInicial();
        servico.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        secretaria = servico.criarUsuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA, "senha1");
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private static Set<Acao> secretariaSemReceber() {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.remove(Acao.RECEBER_ALUGUEL);
        return acoes;
    }

    @Test
    void ajusteValeNoProximoLoginDaPessoa() throws Exception {
        servico.alterarPermissoes(secretaria.getId(), secretariaSemReceber());

        servico.sair();
        servico.login("secretaria@exemplo.org", "senha1");
        assertFalse(Sessao.pode(Acao.RECEBER_ALUGUEL));
        assertTrue(Sessao.pode(Acao.CADASTRAR_INQUILINO));
        assertThrows(AcessoNegadoException.class, () -> Sessao.exigir(Acao.RECEBER_ALUGUEL));
    }

    @Test
    void ajusteFicaGravadoNoBanco() throws Exception {
        servico.alterarPermissoes(secretaria.getId(), Set.of(Acao.VER_CADASTROS));

        Usuario lido = repo.buscarPorId(secretaria.getId()).orElseThrow();
        assertTrue(lido.temPermissoesPersonalizadas());
        assertEquals(Set.of(Acao.VER_CADASTROS), lido.getPermissoes());
        assertFalse(lido.podeAbrir(Tela.ALUGUEIS));
    }

    @Test
    void voltarAoPadraoDoPerfil() throws Exception {
        servico.alterarPermissoes(secretaria.getId(), Set.of());
        servico.restaurarPermissoesDoPerfil(secretaria.getId());

        Usuario lido = repo.buscarPorEmail("secretaria@exemplo.org").orElseThrow();
        assertFalse(lido.temPermissoesPersonalizadas());
        assertTrue(lido.pode(Acao.RECEBER_ALUGUEL));
    }

    @Test
    void pessoaPodeFicarSemNenhumaPermissao() throws Exception {
        servico.alterarPermissoes(secretaria.getId(), Set.of());
        Usuario lido = repo.buscarPorId(secretaria.getId()).orElseThrow();
        assertTrue(lido.getPermissoes().isEmpty());
        assertTrue(lido.podeAbrir(Tela.PAINEL)); // o Painel abre para qualquer usuário logado
    }

    @Test
    void administradorNaoTiraDeSiMesmoGerenciarUsuarios() throws Exception {
        Usuario admin = Sessao.usuario();
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.alterarPermissoes(admin.getId(), Set.of(Acao.VER_CADASTROS)));
        assertEquals(List.of("Você não pode tirar de si mesmo a permissão de gerenciar usuários."), e.getErros());
        assertFalse(repo.buscarPorId(admin.getId()).orElseThrow().temPermissoesPersonalizadas());
    }

    @Test
    void administradorAjustaASiMesmoEASessaoAcompanha() throws Exception {
        Usuario admin = Sessao.usuario();
        Set<Acao> semReceber = EnumSet.allOf(Acao.class);
        semReceber.remove(Acao.RECEBER_ALUGUEL);

        servico.alterarPermissoes(admin.getId(), semReceber);

        assertFalse(Sessao.pode(Acao.RECEBER_ALUGUEL));
        assertTrue(Sessao.pode(Acao.GERENCIAR_USUARIOS));
    }

    @Test
    void secretariaComPermissaoDeUsuariosPassaAGerenciar() throws Exception {
        Set<Acao> acoes = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        acoes.add(Acao.GERENCIAR_USUARIOS);
        servico.alterarPermissoes(secretaria.getId(), acoes);

        servico.sair();
        servico.login("secretaria@exemplo.org", "senha1");
        assertEquals(2, servico.listarUsuarios().size());
    }

    @Test
    void soQuemGerenciaUsuariosAlteraPermissoes() throws Exception {
        Usuario admin = Sessao.usuario();
        servico.sair();
        servico.login("secretaria@exemplo.org", "senha1");

        assertThrows(AcessoNegadoException.class,
                () -> servico.alterarPermissoes(secretaria.getId(), EnumSet.allOf(Acao.class)));
        assertThrows(AcessoNegadoException.class, () -> servico.restaurarPermissoesDoPerfil(admin.getId()));
    }

    @Test
    void usuarioInexistente() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.alterarPermissoes(999, Set.of()));
        assertEquals(List.of("Usuário não encontrado."), e.getErros());
    }
}
