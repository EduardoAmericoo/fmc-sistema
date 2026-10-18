package br.org.fmc.service;

import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.SenhaUtil;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Entrada e saída do sistema.
 *
 * O sistema nasce com um único usuário, o Administrador inicial (e-mail e
 * senha abaixo, também no README). É ele quem cadastra os demais usuários.
 */
public class ServicoAcesso {

    public static final String EMAIL_ADMIN_INICIAL = "admin@fmc.org";
    public static final String SENHA_ADMIN_INICIAL = "Fmc@2026";

    /** Mesma mensagem para e-mail ou senha errados: não revela quais e-mails existem. */
    static final String MSG_INCORRETOS = "E-mail ou senha incorretos.";

    private final UsuarioRepository usuarios;

    public ServicoAcesso() {
        this(new UsuarioRepository());
    }

    public ServicoAcesso(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * Confere e-mail e senha e, se estiverem certos, inicia a sessão.
     *
     * @return o usuário que entrou
     * @throws RegraNegocioException com a mensagem para mostrar na tela
     */
    public Usuario login(String email, String senha) throws SQLException {
        if (email == null || email.isBlank() || senha == null || senha.isEmpty()) {
            throw new RegraNegocioException("Informe o e-mail e a senha.");
        }
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(email);
        if (encontrado.isEmpty() || !SenhaUtil.confere(senha, encontrado.get().getSenhaHash())) {
            throw new RegraNegocioException(MSG_INCORRETOS);
        }
        Usuario usuario = encontrado.get();
        if (!usuario.isAtivo()) {
            throw new RegraNegocioException("Este acesso está desativado. Fale com o Administrador.");
        }
        Sessao.iniciar(usuario);
        return usuario;
    }

    /** Sai do sistema. */
    public void sair() {
        Sessao.encerrar();
    }

    /**
     * Se ainda não existe nenhum usuário, cria o Administrador inicial.
     * Chamado toda vez que o sistema abre; nas próximas vezes não faz nada.
     *
     * @return true se o Administrador foi criado agora
     */
    public boolean garantirAdministradorInicial() throws SQLException {
        if (usuarios.contar() > 0) {
            return false;
        }
        Usuario admin = new Usuario("Administrador", EMAIL_ADMIN_INICIAL, Perfil.ADMINISTRADOR);
        admin.setSenhaHash(SenhaUtil.gerarHash(SENHA_ADMIN_INICIAL));
        usuarios.salvar(admin);
        return true;
    }
}
