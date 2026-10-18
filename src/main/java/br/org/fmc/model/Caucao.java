package br.org.fmc.model;

import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Garantia por caução: 3 meses de aluguel pagos na entrada e devolvidos no fim. */
public class Caucao extends Garantia {

    private BigDecimal valor;
    private LocalDate dataRecebimento;
    private String numeroRecibo;
    private FormaPagamento formaPagamento;

    public Caucao(BigDecimal valor, LocalDate dataRecebimento, FormaPagamento formaPagamento) {
        setValor(valor);
        setDataRecebimento(dataRecebimento);
        setFormaPagamento(formaPagamento);
    }

    /** Valor que a Fundação exige: 3 vezes o aluguel. */
    public static BigDecimal valorExigido(BigDecimal aluguel) {
        return aluguel.multiply(BigDecimal.valueOf(RegrasNegocio.MESES_CAUCAO));
    }

    /** O valor precisa cobrir 3 aluguéis e ser recebido até a entrega das chaves. */
    @Override
    public List<String> validar(Contrato contrato) {
        List<String> erros = new ArrayList<>();
        BigDecimal exigido = valorExigido(contrato.getValorAluguel());
        if (valor.compareTo(exigido) < 0) {
            erros.add("A caução precisa ser de pelo menos 3 meses de aluguel (" + Formatos.moeda(exigido) + ").");
        }
        if (dataRecebimento.isAfter(contrato.getDataEntregaChaves())) {
            erros.add("A caução precisa ser recebida até a entrega das chaves.");
        }
        return erros;
    }

    @Override
    public String getDescricao() {
        return "Caução de " + Formatos.moeda(valor);
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = Valida.positivo(valor, "o valor da caução");
    }

    public LocalDate getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(LocalDate dataRecebimento) {
        this.dataRecebimento = Valida.obrigatorio(dataRecebimento, "a data em que a caução foi recebida");
    }

    public String getNumeroRecibo() {
        return numeroRecibo;
    }

    public void setNumeroRecibo(String numeroRecibo) {
        this.numeroRecibo = Valida.textoOpcional(numeroRecibo);
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = Valida.obrigatorio(formaPagamento, "a forma de pagamento da caução");
    }
}
