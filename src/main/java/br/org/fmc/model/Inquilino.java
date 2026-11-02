package br.org.fmc.model;

import br.org.fmc.util.TextoBusca;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Locatário: quem aluga o imóvel da Fundação. */
public class Inquilino extends Pessoa {

    private String profissao;
    private String localTrabalho;
    private BigDecimal outrasRendas = BigDecimal.ZERO;

    /**
     * Arquivado: o inquilino não aluga mais com a Fundação (ex.: saiu e devolveu as chaves).
     * Some da lista do dia a dia e do formulário de contrato, mas o cadastro e o histórico
     * (contratos, aluguéis, recibos) ficam guardados. null = não está arquivado.
     */
    private LocalDate arquivadoEm;
    private String motivoArquivamento;

    public Inquilino(String nome, Cpf cpf) {
        super(nome, cpf);
    }

    public boolean estaArquivado() {
        return arquivadoEm != null;
    }

    /** Arquiva o inquilino na data informada. O motivo é opcional (ex.: "Mudou de cidade"). */
    public void arquivar(LocalDate data, String motivo) {
        arquivadoEm = Valida.obrigatorio(data, "a data do arquivamento");
        motivoArquivamento = Valida.textoOpcional(motivo);
    }

    /** Volta para a lista do dia a dia (ex.: vai alugar de novo). */
    public void reativar() {
        arquivadoEm = null;
        motivoArquivamento = null;
    }

    public LocalDate getArquivadoEm() {
        return arquivadoEm;
    }

    public String getMotivoArquivamento() {
        return motivoArquivamento;
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
