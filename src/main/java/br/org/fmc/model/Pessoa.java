package br.org.fmc.model;

import java.math.BigDecimal;

/**
 * Dados comuns a inquilino e fiador.
 *
 * É abstrata: não existe "uma pessoa" solta no sistema, só Inquilino ou Fiador.
 * Cada filha decide como calcula a renda considerada (polimorfismo).
 */
public abstract class Pessoa {

    protected Long id;
    protected String nome;
    protected Cpf cpf;
    protected String telefone;
    protected String email;
    protected EstadoCivil estadoCivil = EstadoCivil.SOLTEIRO;
    protected BigDecimal rendaMensal = BigDecimal.ZERO;
    protected Conjuge conjuge;

    protected Pessoa(String nome, Cpf cpf) {
        setNome(nome);
        setCpf(cpf);
    }

    /**
     * Renda usada nas regras da Fundação.
     * Inquilino: renda familiar. Fiador: renda própria.
     */
    public abstract BigDecimal rendaConsiderada();

    public boolean ehCasado() {
        return estadoCivil.temConjuge();
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
        this.nome = Valida.textoObrigatorio(nome, "o nome completo");
    }

    public Cpf getCpf() {
        return cpf;
    }

    public void setCpf(Cpf cpf) {
        this.cpf = Valida.obrigatorio(cpf, "o CPF");
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = Valida.textoOpcional(telefone);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = Valida.textoOpcional(email);
    }

    public EstadoCivil getEstadoCivil() {
        return estadoCivil;
    }

    /** Ao deixar de ser casado(a), o cônjuge é removido. */
    public void setEstadoCivil(EstadoCivil estadoCivil) {
        this.estadoCivil = Valida.obrigatorio(estadoCivil, "o estado civil");
        if (!estadoCivil.temConjuge()) {
            this.conjuge = null;
        }
    }

    public BigDecimal getRendaMensal() {
        return rendaMensal;
    }

    public void setRendaMensal(BigDecimal rendaMensal) {
        this.rendaMensal = Valida.naoNegativo(rendaMensal, "a renda mensal");
    }

    /** Pode ser nulo. Só existe para casado(a) ou união estável. */
    public Conjuge getConjuge() {
        return conjuge;
    }

    public void setConjuge(Conjuge conjuge) {
        if (conjuge != null && !ehCasado()) {
            throw new IllegalArgumentException("Só é possível informar cônjuge para casado(a) ou união estável.");
        }
        this.conjuge = conjuge;
    }

    @Override
    public String toString() {
        return nome + " (" + cpf + ")";
    }
}
