package br.org.fmc.service;

import br.org.fmc.model.RegraSenha;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.RecuperacaoSenhaRepository;
import br.org.fmc.repository.RecuperacaoSenhaRepository.Pedido;
import br.org.fmc.repository.TentativaLoginRepository;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.Correio;
import br.org.fmc.util.SenhaUtil;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Esqueci a senha · A pessoa recebe no e-mail cadastrado um código de 6 números e, com ele,
 * cria uma senha nova sozinha, sem depender do Administrador.
 *
 * Por que é seguro:
 * - só quem abre a caixa de e-mail vê o código;
 * - o código vale 15 minutos, serve uma vez só e fica guardado apenas como hash;
 * - 5 códigos errados e o pedido é cancelado (não dá para ir chutando números);
 * - a resposta ao pedir o código é sempre a mesma, exista o e-mail ou não;
 * - usuário bloqueado pelo Administrador não recupera a senha;
 * - depois da troca, chega um e-mail avisando, para a pessoa perceber se não foi ela.
 */
public class ServicoRecuperacaoSenha {

    public static final Duration VALIDADE = Duration.ofMinutes(15);
    public static final int MAX_TENTATIVAS = 5;
    /** Intervalo mínimo entre dois e-mails para a mesma pessoa (evita encher a caixa de e-mail). */
    public static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);

    public static final String MSG_ENVIADO = "Se este e-mail estiver cadastrado, enviamos um código de 6 números. "
            + "Ele vale por 15 minutos.";
    static final String MSG_INVALIDO = "Código inválido ou vencido. Peça um código novo.";

    private static final SecureRandom SORTEIO = new SecureRandom();

    private final UsuarioRepository usuarios = new UsuarioRepository();
    private final RecuperacaoSenhaRepository pedidos = new RecuperacaoSenhaRepository();
    private final TentativaLoginRepository tentativasLogin = new TentativaLoginRepository();
    private final Correio correio;
    private Clock relogio = Clock.systemDefaultZone();

    public ServicoRecuperacaoSenha() {
        this(Correio.doSistema());
    }

    public ServicoRecuperacaoSenha(Correio correio) {
        this.correio = correio;
    }

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /** false quando não há e-mail configurado neste computador: aí só o Administrador gera senha nova. */
    public boolean disponivel() {
        return correio != null;
    }

    public Correio correio() {
        return correio;
    }

    /**
     * Passo 1: manda o código para o e-mail, se ele for de um usuário ativo. A resposta é sempre
     * a mesma (MSG_ENVIADO), para ninguém descobrir pela tela quais e-mails existem.
     */
    public String pedirCodigo(String email) throws SQLException {
        if (!disponivel()) {
            throw new RegraNegocioException("A recuperação por e-mail não está configurada neste computador. "
                    + "Peça uma senha provisória ao Administrador.");
        }
        String problema = ServicoAcesso.problemaEmail(email);
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(email.trim().toLowerCase());
        if (encontrado.isEmpty() || !encontrado.get().isAtivo()) {
            return MSG_ENVIADO;
        }
        Usuario usuario = encontrado.get();
        LocalDateTime agora = LocalDateTime.now(relogio);
        Optional<Pedido> anterior = pedidos.buscar(usuario.getId());
        if (anterior.isPresent() && agora.isBefore(anterior.get().criadoEm().plus(INTERVALO_REENVIO))) {
            return MSG_ENVIADO; // acabou de mandar um: não manda outro ainda
        }
        String codigo = String.format("%06d", SORTEIO.nextInt(1_000_000));
        pedidos.salvar(new Pedido(usuario.getId(), SenhaUtil.gerarHash(codigo), agora, agora.plus(VALIDADE), 0));
        try {
            correio.enviar(usuario.getEmail(), "Código para criar uma senha nova · Sistema FMC", textoCodigo(usuario, codigo));
        } catch (IOException e) {
            pedidos.apagar(usuario.getId());
            throw new RegraNegocioException("Não foi possível enviar o e-mail agora. Confira a internet e tente de novo "
                    + "em alguns minutos, ou peça uma senha provisória ao Administrador.");
        }
        return MSG_ENVIADO;
    }

    /**
     * Passo 2: confere o código e troca a senha. A senha nova segue a regra de senha forte.
     * Depois disso a pessoa entra normalmente, com o e-mail e a senha nova.
     */
    public void redefinir(String email, String codigo, String senhaNova, String confirmacao) throws SQLException {
        List<String> erros = new ArrayList<>();
        String digitado = codigo == null ? "" : codigo.replaceAll("\\s", "");
        if (!digitado.matches("\\d{6}")) {
            erros.add("Digite o código de 6 números que chegou no seu e-mail.");
        }
        String problema = RegraSenha.problema(senhaNova);
        if (problema != null) {
            erros.add(problema);
        }
        if (senhaNova != null && !senhaNova.isEmpty() && !senhaNova.equals(confirmacao)) {
            erros.add("A confirmação não é igual à senha nova.");
        }
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        String chave = email == null ? "" : email.trim().toLowerCase();
        Optional<Usuario> encontrado = usuarios.buscarPorEmail(chave);
        if (encontrado.isEmpty()) {
            throw new RegraNegocioException(MSG_INVALIDO);
        }
        Usuario usuario = encontrado.get();
        Optional<Pedido> pedido = pedidos.buscar(usuario.getId());
        LocalDateTime agora = LocalDateTime.now(relogio);
        if (pedido.isEmpty() || agora.isAfter(pedido.get().expiraEm())) {
            pedidos.apagar(usuario.getId());
            throw new RegraNegocioException(MSG_INVALIDO);
        }
        if (!SenhaUtil.confere(digitado, pedido.get().codigoHash())) {
            int restantes = MAX_TENTATIVAS - (pedido.get().tentativas() + 1);
            if (restantes <= 0) {
                pedidos.apagar(usuario.getId());
                throw new RegraNegocioException("Código errado " + MAX_TENTATIVAS + " vezes. Por segurança, "
                        + "este código foi cancelado. Peça um código novo.");
            }
            pedidos.contarErro(usuario.getId());
            throw new RegraNegocioException("Código incorreto. " + (restantes == 1 ? "Resta 1 tentativa."
                    : "Restam " + restantes + " tentativas."));
        }
        if (!usuario.isAtivo()) {
            pedidos.apagar(usuario.getId());
            throw new RegraNegocioException(ServicoAcesso.MSG_BLOQUEADO);
        }

        usuario.setSenhaHash(SenhaUtil.gerarHash(senhaNova));
        usuario.setSenhaProvisoria(false);
        usuarios.salvar(usuario);
        pedidos.apagar(usuario.getId());
        tentativasLogin.apagar(chave); // tira a pausa de senhas erradas, se houver
        try {
            correio.enviar(usuario.getEmail(), "Sua senha foi trocada · Sistema FMC", textoAviso(usuario, agora));
        } catch (IOException e) {
            // a senha já foi trocada; o aviso é só uma cortesia
        }
    }

    private static String textoCodigo(Usuario usuario, String codigo) {
        return "Olá, " + usuario.getNome() + ".\n\n"
                + "Recebemos um pedido para criar uma senha nova no Sistema da Fundação Manuel Cruz com este e-mail.\n\n"
                + "Seu código: " + codigo + "\n\n"
                + "Ele vale por " + VALIDADE.toMinutes() + " minutos e só pode ser usado uma vez. "
                + "Digite-o na tela \"Esqueci a senha\".\n\n"
                + "Se não foi você, ignore este e-mail: sua senha continua a mesma. "
                + "Nunca passe este código para ninguém, nem para quem diga ser da Fundação.";
    }

    private static String textoAviso(Usuario usuario, LocalDateTime quando) {
        return "Olá, " + usuario.getNome() + ".\n\n"
                + "A senha do seu acesso ao Sistema da Fundação Manuel Cruz foi trocada em "
                + quando.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")) + ".\n\n"
                + "Se não foi você, avise o Administrador agora para ele bloquear o acesso.";
    }
}
