package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.RegraSenha;
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

/** BOARD-62 · Senha provisória, troca no 1º acesso, senha forte e bloqueio. Dados fictícios. */
class SenhaProvisoriaTest {

    private static final String EMAIL = "bruna@exemplo.org";

    @TempDir
    Path pasta;

    private final ServicoAcesso servico = new ServicoAcesso();
    private final UsuarioRepository repo = new UsuarioRepository();

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        servico.garantirAdministradorInicial();
        entrarComoAdministrador();
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void entrarComoAdministrador() throws Exception {
        servico.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    private ServicoAcesso.UsuarioCriado criarBruna() throws Exception {
        return servico.criarUsuario("Bruna Secretária", EMAIL, Perfil.SECRETARIA);
    }

    @Test
    void usuarioNovoRecebeSenhaProvisoriaForteEPrecisaTrocarNoPrimeiroAcesso() throws Exception {
        ServicoAcesso.UsuarioCriado criado = criarBruna();
        String provisoria = criado.senhaProvisoria();
        assertTrue(provisoria.matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}"), provisoria);
        assertNull(RegraSenha.problema(provisoria));

        servico.sair();
        Usuario entrou = servico.login(EMAIL, provisoria);
        assertTrue(entrou.temSenhaProvisoria());
        // Enquanto não troca a senha, não pode fazer nada no sistema.
        assertFalse(Sessao.pode(Acao.VER_IMOVEIS));
        assertThrows(AcessoNegadoException.class, () -> new ServicoImoveis().listar());

        servico.trocarSenha(provisoria, "MinhaSenha9", "MinhaSenha9");
        assertTrue(Sessao.pode(Acao.VER_IMOVEIS));

        servico.sair();
        assertThrows(RegraNegocioException.class, () -> servico.login(EMAIL, provisoria));
        assertFalse(servico.login(EMAIL, "MinhaSenha9").temSenhaProvisoria());
    }

    @Test
    void senhaNovaPrecisaSerForteEConfirmada() throws Exception {
        String provisoria = criarBruna().senhaProvisoria();
        servico.sair();
        servico.login(EMAIL, provisoria);

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.trocarSenha("errada", "abc", "abd"));

        assertEquals(List.of("A senha atual não confere.",
                "A senha precisa ter: pelo menos 8 caracteres, uma letra maiúscula e um número.",
                "A confirmação não é igual à senha nova."), e.getErros());
        assertTrue(repo.buscarPorEmail(EMAIL).orElseThrow().temSenhaProvisoria());
    }

    @Test
    void senhaNovaNaoPodeSerIgualAAtual() throws Exception {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.trocarSenha(
                ServicoAcesso.SENHA_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL));
        assertEquals(List.of("A senha nova precisa ser diferente da atual."), e.getErros());
    }

    @Test
    void quemEsqueceuASenhaRecebeUmaProvisoriaNova() throws Exception {
        Usuario bruna = servico.criarUsuario("Bruna Secretária", EMAIL, Perfil.SECRETARIA, "senhaAntiga1");

        String nova = servico.gerarSenhaProvisoria(bruna.getId());

        servico.sair();
        assertThrows(RegraNegocioException.class, () -> servico.login(EMAIL, "senhaAntiga1"));
        assertTrue(servico.login(EMAIL, nova).temSenhaProvisoria());
    }

    @Test
    void administradorNaoGeraSenhaProvisoriaParaSiMesmo() {
        long eu = Sessao.usuario().getId();
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.gerarSenhaProvisoria(eu));
        assertEquals(List.of("Para mudar a sua própria senha, use \"Trocar minha senha\", no menu."), e.getErros());
    }

    @Test
    void usuarioBloqueadoNaoEntraAteSerDesbloqueado() throws Exception {
        Usuario bruna = servico.criarUsuario("Bruna Secretária", EMAIL, Perfil.SECRETARIA, "Senha123");
        servico.bloquear(bruna.getId());

        servico.sair();
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.login(EMAIL, "Senha123"));
        assertEquals(List.of("Este acesso está bloqueado. Fale com o Administrador."), e.getErros());

        entrarComoAdministrador();
        servico.desbloquear(bruna.getId());
        servico.sair();
        assertEquals("Bruna Secretária", servico.login(EMAIL, "Senha123").getNome());
    }

    @Test
    void ninguemBloqueiaASiMesmo() {
        long eu = Sessao.usuario().getId();
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.bloquear(eu));
        assertEquals(List.of("Você não pode bloquear o seu próprio acesso."), e.getErros());
    }

    @Test
    void sistemaNuncaFicaSemAdministradorAtivo() throws Exception {
        // Uma secretária que recebeu a permissão de gerenciar acessos tenta bloquear o único Administrador.
        Usuario bruna = servico.criarUsuario("Bruna Secretária", EMAIL, Perfil.SECRETARIA, "Senha123");
        Set<Acao> comAcesso = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        comAcesso.add(Acao.GERENCIAR_ACESSO);
        servico.alterarPermissoes(bruna.getId(), comAcesso);
        long admin = Sessao.usuario().getId();
        servico.sair();
        servico.login(EMAIL, "Senha123");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.bloquear(admin));
        assertEquals(List.of("Não é possível bloquear o último Administrador ativo."), e.getErros());
    }

    @Test
    void secretariaNaoBloqueiaNemGeraSenha() throws Exception {
        Usuario bruna = servico.criarUsuario("Bruna Secretária", EMAIL, Perfil.SECRETARIA, "Senha123");
        long admin = Sessao.usuario().getId();
        servico.sair();
        servico.login(EMAIL, "Senha123");

        assertThrows(AcessoNegadoException.class, () -> servico.bloquear(admin));
        assertThrows(AcessoNegadoException.class, () -> servico.gerarSenhaProvisoria(admin));
        assertTrue(repo.buscarPorId(bruna.getId()).orElseThrow().isAtivo());
    }
}
