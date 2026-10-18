package br.org.fmc.model;

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

    public Usuario(String nome, String email, Perfil perfil) {
        setNome(nome);
        setEmail(email);
        setPerfil(perfil);
    }

    /** O usuário só pode fazer algo se estiver ativo e o perfil dele permitir. */
    public boolean pode(Acao acao) {
        return ativo && perfil.permite(acao);
    }

    /** O usuário pode abrir esta tela do menu? */
    public boolean podeAbrir(Tela tela) {
        return ativo && tela.liberadaPara(perfil);
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

    public void setPerfil(Perfil perfil) {
        this.perfil = Valida.obrigatorio(perfil, "o perfil");
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    @Override
    public String toString() {
        return nome + " (" + perfil + ")";
    }
}
