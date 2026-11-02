package br.org.fmc.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pessoa que entra no sistema (Administrador, Secretaria ou Financeiro).
 *
 * A senha nunca é guardada: só o hash dela. Conferir a senha é trabalho do
 * ServicoAcesso (história de login).
 */
public class Usuario {

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private Perfil perfil;
    private boolean ativo = true;

    /**
     * A senha atual é provisória (BOARD-62): a pessoa precisa trocá-la antes de usar o sistema.
     * Enquanto isso, não pode fazer nada nem abrir nenhuma tela.
     */
    private boolean senhaProvisoria;

    /**
     * Permissões escolhidas pelo Administrador só para esta pessoa (BOARD-41).
     * null quando o usuário segue o padrão do perfil.
     */
    private Set<Acao> permissoesPersonalizadas;

    public Usuario(String nome, String email, Perfil perfil) {
        setNome(nome);
        setEmail(email);
        setPerfil(perfil);
    }

    /**
     * O usuário só pode fazer algo se estiver ativo (não bloqueado), já tiver trocado
     * a senha provisória e as permissões dele permitirem.
     */
    public boolean pode(Acao acao) {
        return liberado() && getPermissoes().contains(acao);
    }

    /** O usuário pode abrir esta tela do menu? */
    public boolean podeAbrir(Tela tela) {
        return liberado() && (tela.getAcaoExigida() == null || pode(tela.getAcaoExigida()));
    }

    private boolean liberado() {
        return ativo && !senhaProvisoria;
    }

    /** O que este usuário pode fazer: as permissões personalizadas ou, se não houver, as do perfil. */
    public Set<Acao> getPermissoes() {
        return permissoesPersonalizadas == null
                ? perfil.getPermissoes()
                : Collections.unmodifiableSet(permissoesPersonalizadas);
    }

    /** O Administrador mudou alguma permissão desta pessoa em relação ao perfil? */
    public boolean temPermissoesPersonalizadas() {
        return permissoesPersonalizadas != null;
    }

    /**
     * Define exatamente o que esta pessoa pode fazer. Se a escolha for igual
     * ao padrão do perfil, o usuário volta a seguir o perfil.
     */
    public void personalizarPermissoes(Set<Acao> acoes) {
        Valida.obrigatorio(acoes, "as permissões");
        Set<Acao> escolhidas = acoes.isEmpty() ? EnumSet.noneOf(Acao.class) : EnumSet.copyOf(acoes);
        // Quem pode criar ou editar numa tela precisa poder abrir a tela: o "ver" entra junto.
        for (Acao acao : EnumSet.copyOf(escolhidas.isEmpty() ? EnumSet.noneOf(Acao.class) : escolhidas)) {
            if (acao.getRequisito() != null) {
                escolhidas.add(acao.getRequisito());
            }
        }
        permissoesPersonalizadas = escolhidas.equals(perfil.getPermissoes()) ? null : escolhidas;
    }

    /** Volta a seguir as permissões do perfil. */
    public void restaurarPermissoesDoPerfil() {
        permissoesPersonalizadas = null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Valida.textoObrigatorio(nome, "o nome");
    }

    public String getEmail() {
        return email;
    }

    /** O e-mail é guardado em minúsculas, para o login não depender de maiúsculas. */
    public void setEmail(String email) {
        String e = Valida.textoObrigatorio(email, "o e-mail").toLowerCase();
        if (!e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        this.email = e;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    /** Ao trocar de perfil, as permissões personalizadas são descartadas: vale o padrão do novo perfil. */
    public void setPerfil(Perfil perfil) {
        Valida.obrigatorio(perfil, "o perfil");
        if (perfil != this.perfil) {
            permissoesPersonalizadas = null;
        }
        this.perfil = perfil;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** false = bloqueado pelo Administrador: não consegue entrar. */
    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    /** A senha atual é provisória e precisa ser trocada no próximo acesso? */
    public boolean temSenhaProvisoria() {
        return senhaProvisoria;
    }

    public void setSenhaProvisoria(boolean senhaProvisoria) {
        this.senhaProvisoria = senhaProvisoria;
    }

    @Override
    public String toString() {
        return nome + " (" + perfil + ")";
    }
}
