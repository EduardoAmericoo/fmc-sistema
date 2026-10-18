package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.UsuarioRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-53 · Cadastro de usuários pelo Administrador. */
class CadastroUsuarioTest {

    @TempDir
    Path pasta;

    private final UsuarioRepository repo = new UsuarioRepository();
    private final ServicoAcesso servico = new ServicoAcesso(repo);

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        servico.garantirAdministradorInicial();
        servico.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    @Test
    void administradorCriaUsuarioQueConsegueEntrar() throws Exception {
        Usuario novo = servico.criarUsuario("Secretária", "Secretaria@Exemplo.org", Perfil.SECRETARIA, "senha1");
        assertNotNull(novo.getId());
        assertEquals("secretaria@exemplo.org", novo.getEmail());
        assertFalse(novo.getSenhaHash().contains("senha1"));

        servico.sair();
        Usuario entrou = servico.login("secretaria@exemplo.org", "senha1");
        assertEquals(Perfil.SECRETARIA, entrou.getPerfil());
    }

    @Test
    void naoPermiteDoisUsuariosComOMesmoEmail() throws Exception {
        servico.criarUsuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA, "senha1");
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.criarUsuario("Outra", "SECRETARIA@exemplo.org", Perfil.FINANCEIRO, "senha2"));
        assertTrue(e.getErros().contains("Já existe um usuário com este e-mail."));
        assertEquals(2, repo.contar()); // o Administrador e a Secretária
    }

    @Test
    void mostraTodosOsErrosDeUmaVez() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.criarUsuario(" ", "sem-arroba", null, "123"));
        assertEquals(List.of(
                "Informe o nome.",
                "Informe um e-mail válido.",
                "Escolha o perfil.",
                "A senha inicial precisa ter pelo menos 6 caracteres."), e.getErros());
    }

    @Test
    void secretariaNaoCriaNemListaUsuarios() throws Exception {
        servico.criarUsuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA, "senha1");
        servico.sair();
        servico.login("secretaria@exemplo.org", "senha1");

        assertThrows(AcessoNegadoException.class,
                () -> servico.criarUsuario("Intrusa", "intrusa@exemplo.org", Perfil.ADMINISTRADOR, "senha9"));
        assertThrows(AcessoNegadoException.class, servico::listarUsuarios);
        assertFalse(repo.existeEmail("intrusa@exemplo.org"));
    }

    @Test
    void semLoginNaoCriaUsuario() {
        servico.sair();
        assertThrows(AcessoNegadoException.class,
                () -> servico.criarUsuario("Alguém", "alguem@exemplo.org", Perfil.SECRETARIA, "senha1"));
    }

    @Test
    void listaUsuariosEmOrdemDeNome() throws Exception {
        servico.criarUsuario("Contabilidade", "contabilidade@exemplo.org", Perfil.FINANCEIRO, "senha1");
        servico.criarUsuario("Bruna Secretária", "bruna@exemplo.org", Perfil.SECRETARIA, "senha1");
        List<String> nomes = servico.listarUsuarios().stream().map(Usuario::getNome).toList();
        assertEquals(List.of("Administrador", "Bruna Secretária", "Contabilidade"), nomes);
    }
}
