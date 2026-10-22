package br.org.fmc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsuarioRepositoryTest {

    @TempDir
    Path pasta;

    private final UsuarioRepository repo = new UsuarioRepository();

    @BeforeEach
    void bancoNovo() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
    }

    private Usuario novo(String email) {
        Usuario u = new Usuario("Secretária", email, Perfil.SECRETARIA);
        u.setSenhaHash("hash-qualquer");
        return u;
    }

    @Test
    void salvaEBuscaPorEmailSemDiferenciarMaiusculas() throws Exception {
        Usuario u = novo("secretaria@exemplo.org");
        repo.salvar(u);
        assertNotNull(u.getId());

        Usuario lido = repo.buscarPorEmail(" Secretaria@Exemplo.org ").orElseThrow();
        assertEquals("Secretária", lido.getNome());
        assertEquals(Perfil.SECRETARIA, lido.getPerfil());
        assertEquals("hash-qualquer", lido.getSenhaHash());
        assertTrue(lido.isAtivo());
    }

    @Test
    void atualizaQuandoJaTemId() throws Exception {
        Usuario u = novo("secretaria@exemplo.org");
        repo.salvar(u);
        u.setAtivo(false);
        u.setPerfil(Perfil.FINANCEIRO);
        repo.salvar(u);

        Usuario lido = repo.buscarPorId(u.getId()).orElseThrow();
        assertFalse(lido.isAtivo());
        assertEquals(Perfil.FINANCEIRO, lido.getPerfil());
        assertEquals(1, repo.contar());
    }

    @Test
    void ajusteGravadoComNomesAntigosContinuaValendo() throws Exception {
        Usuario u = novo("secretaria@exemplo.org");
        repo.salvar(u);
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "INSERT INTO usuario_permissoes (usuario_id, acoes) VALUES (?, 'VER_CADASTROS,CADASTRAR_INQUILINO')")) {
            st.setLong(1, u.getId());
            st.executeUpdate();
        }

        Usuario lido = repo.buscarPorId(u.getId()).orElseThrow();
        assertEquals(EnumSet.of(Acao.VER_IMOVEIS, Acao.VER_INQUILINOS, Acao.VER_CONTRATOS,
                Acao.CRIAR_INQUILINO, Acao.EDITAR_INQUILINO), lido.getPermissoes());
    }

    @Test
    void naoAceitaDoisUsuariosComOMesmoEmail() throws Exception {
        repo.salvar(novo("secretaria@exemplo.org"));
        assertThrows(SQLException.class, () -> repo.salvar(novo("secretaria@exemplo.org")));
    }

    @Test
    void emailInexistenteVoltaVazio() throws Exception {
        assertTrue(repo.buscarPorEmail("ninguem@exemplo.org").isEmpty());
        assertTrue(repo.listarTodos().isEmpty());
    }
}
