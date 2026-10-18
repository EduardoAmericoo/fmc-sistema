package br.org.fmc.model;

import java.math.BigDecimal;

/** Locatário: quem aluga o imóvel da Fundação. */
public class Inquilino extends Pessoa {

    private String profissao;
    private String localTrabalho;
    private BigDecimal outrasRendas = BigDecimal.ZERO;

    public Inquilino(String nome, Cpf cpf) {
        super(nome, cpf);
    }

    /** Renda familiar: renda própria + outras rendas + renda do cônjuge. */
    @Override
    public BigDecimal rendaConsiderada() {
        BigDecimal total = rendaMensal.add(outrasRendas);
        if (conjuge != null) {
            total = total.add(conjuge.getRenda());
        }
        return total;
    }

    public String getProfissao() {
        return profissao;
    }

    public void setProfissao(String profissao) {
        this.profissao = Valida.textoOpcional(profissao);
    }

    public String getLocalTrabalho() {
        return localTrabalho;
    }

    public void setLocalTrabalho(String localTrabalho) {
        this.localTrabalho = Valida.textoOpcional(localTrabalho);
    }

    public BigDecimal getOutrasRendas() {
        return outrasRendas;
    }

    public void setOutrasRendas(BigDecimal outrasRendas) {
        this.outrasRendas = Valida.naoNegativo(outrasRendas, "o valor de outras rendas");
    }
}
