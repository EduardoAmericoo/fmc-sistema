package br.org.fmc.model;

import java.math.BigDecimal;

/** Fiador: garante o contrato se o inquilino não pagar. */
public class Fiador extends Pessoa {

    private String endereco;

    public Fiador(String nome, Cpf cpf) {
        super(nome, cpf);
    }

    /** Para o fiador, conta só a renda dele. */
    @Override
    public BigDecimal rendaConsiderada() {
        return rendaMensal;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = Valida.textoOpcional(endereco);
    }
}
