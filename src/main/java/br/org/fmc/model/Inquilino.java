package br.org.fmc.model;

import br.org.fmc.util.TextoBusca;
import java.math.BigDecimal;
import java.util.List;

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

    /**
     * Este inquilino aparece na busca por este texto? (BOARD-56)
     * - pelo nome: todas as palavras digitadas, em qualquer ordem, sem ligar para acentos
     *   ("silva maria" encontra "Maria da Silva");
     * - pelo CPF: com ou sem pontos e traço, inteiro ou só um pedaço de pelo menos 3 números.
     * Busca vazia: todos aparecem.
     */
    public boolean atendeBusca(String busca) {
        List<String> palavras = TextoBusca.palavras(busca);
        if (palavras.isEmpty()) {
            return true;
        }
        String digitos = TextoBusca.digitos(busca);
        boolean pareceCpf = digitos.length() >= 3 && busca.trim().matches("[\\d.\\-\\s]+");
        if (pareceCpf) {
            return cpf.getNumero().contains(digitos);
        }
        String nomeNormalizado = TextoBusca.normalizar(nome);
        return palavras.stream().allMatch(nomeNormalizado::contains);
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
