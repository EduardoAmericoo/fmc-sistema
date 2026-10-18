package br.org.fmc.model;

import java.math.BigDecimal;

/** Cônjuge do inquilino ou do fiador. A renda dele entra na renda familiar do inquilino. */
public class Conjuge {

    private String nome;
    private Cpf cpf;
    private BigDecimal renda = BigDecimal.ZERO;

    public Conjuge(String nome) {
        setNome(nome);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = Valida.textoObrigatorio(nome, "o nome do cônjuge");
    }

    /** Pode ser nulo: o CPF do cônjuge não é obrigatório. */
    public Cpf getCpf() {
        return cpf;
    }

    public void setCpf(Cpf cpf) {
        this.cpf = cpf;
    }

    public BigDecimal getRenda() {
        return renda;
    }

    public void setRenda(BigDecimal renda) {
        this.renda = Valida.naoNegativo(renda, "a renda do cônjuge");
    }
}
