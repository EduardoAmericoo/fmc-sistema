package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.SenhaUtil;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Entrada e saída do sistema e cadastro de usuários.
 *
 * O sistema nasce com um único usuário, o Administrador inicial (e-mail e
 * senha abaixo, também no README). É ele quem cadastra os demais usuários.
 */
public class ServicoAcesso {

    public static final String EMAIL_ADMIN_INICIAL = "admin@fmc.org";
    public static final String SENHA_ADMIN_INICIAL = "Fmc@2026";

    /** Tamanho mínimo da senha inicial que o Administrador define. */
    public static final int SENHA_MINIMA = 6;

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

    /**
     * Cria um usuário novo (BOARD-53). Só quem pode criar usuários (o Administrador).
     * Junta todos os problemas encontrados numa única RegraNegocioException,
     * para a tela mostrar tudo de uma vez.
     *
     * @return o usuário criado, já com id
     */
    public Usuario criarUsuario(String nome, String email, Perfil perfil, String senhaInicial) throws SQLException {
        Sessao.exigir(Acao.CRIAR_USUARIO);

        List<String> erros = new ArrayList<>();
        if (nome == null || nome.isBlank()) {
            erros.add("Informe o nome.");
        }
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            erros.add("Informe um e-mail válido.");
        } else if (usuarios.existeEmail(email)) {
            erros.add("Já existe um usuário com este e-mail.");
        }
        if (perfil == null) {
            erros.add("Escolha o perfil.");
        }
        if (senhaInicial == null || senhaInicial.length() < SENHA_MINIMA) {
            erros.add("A senha inicial precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        Usuario novo = new Usuario(nome, email, perfil);
        novo.setSenhaHash(SenhaUtil.gerarHash(senhaInicial));
        usuarios.salvar(novo);
        return novo;
    }

    /** Todos os usuários, em ordem de nome. Só para quem pode ver a tela de Usuários. */
    public List<Usuario> listarUsuarios() throws SQLException {
        Sessao.exigir(Acao.VER_USUARIOS);
        return usuarios.listarTodos();
    }

    /**
     * Define exatamente o que esta pessoa pode fazer, além do perfil (BOARD-41).
     * Só quem pode ajustar permissões. Ninguém pode tirar de si mesmo essa permissão,
     * para o sistema não ficar sem ninguém que consiga desfazer o ajuste.
     *
     * @return o usuário já com as permissões novas
     */
    public Usuario alterarPermissoes(long usuarioId, Set<Acao> acoes) throws SQLException {
        Sessao.exigir(Acao.EDITAR_PERMISSOES);
        if (acoes == null) {
            throw new RegraNegocioException("Escolha as permissões.");
        }
        Usuario usuario = buscarParaAlterar(usuarioId);
        usuario.personalizarPermissoes(acoes);
        return salvarPermissoes(usuario);
    }

    /** Desfaz os ajustes: a pessoa volta a ter só as permissões do perfil dela. */
    public Usuario restaurarPermissoesDoPerfil(long usuarioId) throws SQLException {
        Sessao.exigir(Acao.EDITAR_PERMISSOES);
        Usuario usuario = buscarParaAlterar(usuarioId);
        usuario.restaurarPermissoesDoPerfil();
        return salvarPermissoes(usuario);
    }

    private Usuario buscarParaAlterar(long usuarioId) throws SQLException {
        return usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado."));
    }

    private Usuario salvarPermissoes(Usuario usuario) throws SQLException {
        boolean ehQuemEstaLogado = usuario.getId().equals(Sessao.usuario().getId());
        if (ehQuemEstaLogado && !usuario.pode(Acao.EDITAR_PERMISSOES)) {
            throw new RegraNegocioException("Você não pode tirar de si mesmo a permissão de ajustar permissões.");
        }
        usuarios.salvar(usuario);
        if (ehQuemEstaLogado) {
            Sessao.iniciar(usuario); // a sessão passa a valer com as permissões novas
        }
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
