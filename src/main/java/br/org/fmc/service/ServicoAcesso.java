package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.RegraSenha;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.TentativaLoginRepository;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.SenhaUtil;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** Limite de tentativas no login: 5 senhas erradas seguidas deixam o e-mail 2 minutos em pausa. */
    public static final int MAX_TENTATIVAS = 5;
    public static final Duration PAUSA = Duration.ofMinutes(2);

    static final String MSG_BLOQUEADO = "Este acesso está bloqueado. Fale com o Administrador.";

    /** Usuário recém-criado e a senha provisória dele, para o Administrador entregar à pessoa (BOARD-62). */
    public record UsuarioCriado(Usuario usuario, String senhaProvisoria) {
    }

    private final UsuarioRepository usuarios;
    private final TentativaLoginRepository tentativas = new TentativaLoginRepository();

    /** Relógio do sistema; os testes trocam por um relógio parado para testar a pausa. */
    private Clock relogio = Clock.systemDefaultZone();

    public ServicoAcesso() {
        this(new UsuarioRepository());
    }

    public ServicoAcesso(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Só para os testes: faz o serviço usar outro relógio. */
    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /**
     * Confere e-mail e senha e, se estiverem certos, inicia a sessão.
     * Se a senha for provisória (usuario.temSenhaProvisoria()), a tela leva a pessoa
     * direto para a troca de senha: até trocar, ela não pode fazer nada no sistema.
     *
     * Cada problema vem com o campo dele (FalhaLogin), para a tela mostrar o aviso
     * embaixo do campo certo. Dizer "não existe usuário com este e-mail" revelaria quais
     * e-mails existem; num sistema interno isso é aceitável porque há o limite de
     * tentativas: depois de 5 senhas erradas seguidas, o e-mail fica 2 minutos em pausa.
     *
     * @return o usuário que entrou
     * @throws FalhaLogin com a mensagem de cada campo para mostrar na tela
     */
    public Usuario login(String email, String senha) throws SQLException {
        Map<FalhaLogin.Campo, String> erros = new EnumMap<>(FalhaLogin.Campo.class);
        String problemaEmail = problemaEmail(email);
        if (problemaEmail != null) {
            erros.put(FalhaLogin.Campo.EMAIL, problemaEmail);
        }
        if (senha == null || senha.isEmpty()) {
            erros.put(FalhaLogin.Campo.SENHA, "Informe a senha.");
        }
        if (!erros.isEmpty()) {
            throw new FalhaLogin(erros);
        }
        String chave = email.trim().toLowerCase();
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(chave);
        if (encontrado.isEmpty()) {
            throw new FalhaLogin(FalhaLogin.Campo.EMAIL, "Nenhum usuário com este e-mail. Confira se digitou certo.");
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        TentativaLoginRepository.Tentativas antes = tentativas.buscar(chave).orElse(null);
        if (antes != null && antes.pausaAte() != null && agora.isBefore(antes.pausaAte())) {
            throw new FalhaLogin(Map.of(FalhaLogin.Campo.GERAL, "Acesso em pausa por segurança, depois de "
                    + MAX_TENTATIVAS + " senhas erradas. Tente de novo " + quantoFalta(agora, antes.pausaAte()) + "."),
                    antes.pausaAte());
        }
        Usuario usuario = encontrado.get();
        if (!SenhaUtil.confere(senha, usuario.getSenhaHash())) {
            registrarSenhaErrada(chave, antes, agora);
        }
        tentativas.apagar(chave);
        if (!usuario.isAtivo()) {
            throw new FalhaLogin(FalhaLogin.Campo.GERAL, MSG_BLOQUEADO);
        }
        Sessao.iniciar(usuario);
        return usuario;
    }

    /** Conta mais uma senha errada; na 5ª seguida, coloca o e-mail em pausa. Sempre lança FalhaLogin. */
    private void registrarSenhaErrada(String email, TentativaLoginRepository.Tentativas antes, LocalDateTime agora)
            throws SQLException {
        // Depois de uma pausa que já acabou, a contagem recomeça.
        int erros = (antes == null || antes.pausaAte() != null ? 0 : antes.erros()) + 1;
        if (erros >= MAX_TENTATIVAS) {
            LocalDateTime pausaAte = agora.plus(PAUSA);
            tentativas.salvar(email, new TentativaLoginRepository.Tentativas(erros, pausaAte));
            throw new FalhaLogin(Map.of(FalhaLogin.Campo.SENHA, "Senha incorreta.",
                    FalhaLogin.Campo.GERAL, "Senha errada " + MAX_TENTATIVAS + " vezes seguidas. Por segurança, "
                            + "o acesso fica em pausa por " + PAUSA.toMinutes() + " minutos."), pausaAte);
        }
        tentativas.salvar(email, new TentativaLoginRepository.Tentativas(erros, null));
        int restantes = MAX_TENTATIVAS - erros;
        String aviso = restantes > 2 ? ""
                : restantes == 1 ? " Mais 1 tentativa errada e o acesso fica em pausa por " + PAUSA.toMinutes() + " minutos."
                : " Mais " + restantes + " tentativas erradas e o acesso fica em pausa por " + PAUSA.toMinutes() + " minutos.";
        throw new FalhaLogin(FalhaLogin.Campo.SENHA, "Senha incorreta." + aviso);
    }

    private static String quantoFalta(LocalDateTime agora, LocalDateTime ate) {
        long segundos = Duration.between(agora, ate).getSeconds();
        long minutos = (segundos + 59) / 60; // arredonda para cima
        return minutos <= 1 ? "em 1 minuto" : "em " + minutos + " minutos";
    }

    /**
     * Explica o que está errado no e-mail digitado, para mostrar embaixo do campo; null se estiver certo.
     * Ex.: "E-mail incompleto: falta o domínio depois do @, como exemplo.org."
     */
    public static String problemaEmail(String email) {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) {
            return "Informe o e-mail.";
        }
        if (e.contains(" ")) {
            return "O e-mail não pode ter espaços.";
        }
        int arroba = e.indexOf('@');
        if (arroba < 0) {
            return "Falta o @ no e-mail. Exemplo: nome@exemplo.org";
        }
        if (arroba == 0) {
            return "Falta o nome antes do @. Exemplo: nome@exemplo.org";
        }
        String dominio = e.substring(arroba + 1);
        if (dominio.contains("@")) {
            return "O e-mail só pode ter um @.";
        }
        if (!dominio.matches("[^.]+(\\.[^.]+)+")) {
            return "E-mail incompleto: falta o domínio depois do @, como exemplo.org.";
        }
        return null;
    }

    /**
     * Cria um usuário novo (BOARD-53) com uma senha provisória gerada pelo sistema (BOARD-62).
     * O Administrador entrega a senha à pessoa, que é obrigada a trocá-la no 1º acesso.
     * Junta todos os problemas encontrados numa única RegraNegocioException,
     * para a tela mostrar tudo de uma vez.
     *
     * @return o usuário criado (já com id) e a senha provisória dele
     */
    public UsuarioCriado criarUsuario(String nome, String email, Perfil perfil) throws SQLException {
        Sessao.exigir(Acao.CRIAR_USUARIO);
        validarNovoUsuario(nome, email, perfil);
        String provisoria = SenhaUtil.gerarProvisoria();
        Usuario novo = new Usuario(nome, email, perfil);
        novo.setSenhaHash(SenhaUtil.gerarHash(provisoria));
        novo.setSenhaProvisoria(true);
        usuarios.salvar(novo);
        return new UsuarioCriado(novo, provisoria);
    }

    /**
     * Cria um usuário com uma senha já escolhida, sem troca obrigatória.
     * Só para a carga de dados de teste e para os testes automáticos (por isso não é public).
     */
    Usuario criarUsuario(String nome, String email, Perfil perfil, String senha) throws SQLException {
        Sessao.exigir(Acao.CRIAR_USUARIO);
        validarNovoUsuario(nome, email, perfil);
        Usuario novo = new Usuario(nome, email, perfil);
        novo.setSenhaHash(SenhaUtil.gerarHash(senha));
        usuarios.salvar(novo);
        return novo;
    }

    private void validarNovoUsuario(String nome, String email, Perfil perfil) throws SQLException {
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
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }
    }

    /**
     * A pessoa logada troca a própria senha: no 1º acesso (obrigatório) ou quando quiser.
     * A senha nova precisa ser forte (RegraSenha) e diferente da atual.
     */
    public void trocarSenha(String senhaAtual, String senhaNova, String confirmacao) throws SQLException {
        Usuario usuario = buscarParaAlterar(Sessao.usuario().getId());
        List<String> erros = new ArrayList<>();
        if (senhaAtual == null || senhaAtual.isEmpty()) {
            erros.add("Informe a senha atual.");
        } else if (!SenhaUtil.confere(senhaAtual, usuario.getSenhaHash())) {
            erros.add("A senha atual não confere.");
        }
        String problema = RegraSenha.problema(senhaNova);
        if (problema != null) {
            erros.add(problema);
        } else if (senhaNova.equals(senhaAtual)) {
            erros.add("A senha nova precisa ser diferente da atual.");
        }
        if (senhaNova != null && !senhaNova.isEmpty() && !senhaNova.equals(confirmacao)) {
            erros.add("A confirmação não é igual à senha nova.");
        }
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }
        usuario.setSenhaHash(SenhaUtil.gerarHash(senhaNova));
        usuario.setSenhaProvisoria(false);
        usuarios.salvar(usuario);
        Sessao.iniciar(usuario); // a sessão passa a valer sem a trava da senha provisória
    }

    /**
     * Gera uma senha provisória nova para alguém que esqueceu a senha (BOARD-62).
     * A senha antiga deixa de valer e a pessoa troca no próximo acesso.
     *
     * @return a senha provisória, para o Administrador entregar à pessoa
     */
    public String gerarSenhaProvisoria(long usuarioId) throws SQLException {
        Sessao.exigir(Acao.GERENCIAR_ACESSO);
        Usuario usuario = buscarParaAlterar(usuarioId);
        if (usuario.getId().equals(Sessao.usuario().getId())) {
            throw new RegraNegocioException("Para mudar a sua própria senha, use \"Trocar minha senha\", no menu.");
        }
        String provisoria = SenhaUtil.gerarProvisoria();
        usuario.setSenhaHash(SenhaUtil.gerarHash(provisoria));
        usuario.setSenhaProvisoria(true);
        usuarios.salvar(usuario);
        return provisoria;
    }

    /**
     * Bloqueia o acesso de alguém (BOARD-62): a pessoa não consegue mais entrar.
     * Ninguém bloqueia a si mesmo, e o sistema nunca fica sem um Administrador ativo.
     */
    public Usuario bloquear(long usuarioId) throws SQLException {
        Sessao.exigir(Acao.GERENCIAR_ACESSO);
        Usuario usuario = buscarParaAlterar(usuarioId);
        if (usuario.getId().equals(Sessao.usuario().getId())) {
            throw new RegraNegocioException("Você não pode bloquear o seu próprio acesso.");
        }
        if (usuario.getPerfil() == Perfil.ADMINISTRADOR && usuario.isAtivo() && administradoresAtivos() <= 1) {
            throw new RegraNegocioException("Não é possível bloquear o último Administrador ativo.");
        }
        usuario.setAtivo(false);
        usuarios.salvar(usuario);
        return usuario;
    }

    /** Libera de novo o acesso de quem estava bloqueado. A senha continua a mesma. */
    public Usuario desbloquear(long usuarioId) throws SQLException {
        Sessao.exigir(Acao.GERENCIAR_ACESSO);
        Usuario usuario = buscarParaAlterar(usuarioId);
        usuario.setAtivo(true);
        usuarios.salvar(usuario);
        return usuario;
    }

    private long administradoresAtivos() throws SQLException {
        return usuarios.listarTodos().stream()
                .filter(u -> u.isAtivo() && u.getPerfil() == Perfil.ADMINISTRADOR)
                .count();
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
