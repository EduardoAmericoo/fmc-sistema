package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.SenhaUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServicoAcessoTest {

    @TempDir
    Path pasta;

    private final UsuarioRepository repo = new UsuarioRepository();
    private final ServicoAcesso servico = new ServicoAcesso(repo);

    @BeforeEach
    void bancoNovo() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    @Test
    void sistemaNasceComUmAdministradorSoUmaVez() throws Exception {
        assertTrue(servico.garantirAdministradorInicial());
        assertFalse(servico.garantirAdministradorInicial());
        assertEquals(1, repo.contar());
        Usuario admin = repo.buscarPorEmail(ServicoAcesso.EMAIL_ADMIN_INICIAL).orElseThrow();
        assertEquals(Perfil.ADMINISTRADOR, admin.getPerfil());
        assertFalse(admin.getSenhaHash().contains(ServicoAcesso.SENHA_ADMIN_INICIAL));
    }

    @Test
    void loginCertoIniciaASessao() throws Exception {
        servico.garantirAdministradorInicial();
        Usuario u = servico.login(" ADMIN@fmc.org ", ServicoAcesso.SENHA_ADMIN_INICIAL);
        assertEquals(Perfil.ADMINISTRADOR, u.getPerfil());
        assertTrue(Sessao.estaLogado());
        servico.sair();
        assertFalse(Sessao.estaLogado());
    }

    @Test
    void senhaErradaOuEmailInexistenteDaAMesmaMensagemENaoEntra() throws Exception {
        servico.garantirAdministradorInicial();
        RegraNegocioException senhaErrada = assertThrows(RegraNegocioException.class,
                () -> servico.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, "errada"));
        RegraNegocioException emailErrado = assertThrows(RegraNegocioException.class,
                () -> servico.login("ninguem@fmc.org", ServicoAcesso.SENHA_ADMIN_INICIAL));
        assertEquals(ServicoAcesso.MSG_INCORRETOS, senhaErrada.getMessage());
        assertEquals(ServicoAcesso.MSG_INCORRETOS, emailErrado.getMessage());
        assertFalse(Sessao.estaLogado());
    }

    @Test
    void camposVaziosPedemParaPreencher() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.login("", ""));
        assertEquals("Informe o e-mail e a senha.", e.getMessage());
    }

    @Test
    void usuarioBloqueadoNaoEntra() throws Exception {
        Usuario u = new Usuario("Antiga", "antiga@fmc.org", Perfil.SECRETARIA);
        u.setSenhaHash(SenhaUtil.gerarHash("Senha123"));
        u.setAtivo(false);
        repo.salvar(u);
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.login("antiga@fmc.org", "Senha123"));
        assertTrue(e.getMessage().contains("bloqueado"));
        assertFalse(Sessao.estaLogado());
    }
}
