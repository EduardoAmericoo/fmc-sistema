package br.org.fmc.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Um reajuste anual do aluguel ([D9] BOARD-46). Todo ano, no aniversário do contrato (a data
 * da entrega das chaves + 12, 24, 36… meses), o aluguel é corrigido pela média do INPC e do
 * IGP-DI dos últimos 12 meses. O percentual é digitado pela Fundação: o sistema não busca o
 * índice na internet.
 *
 * Fica guardado no histórico do contrato: de qual aniversário, quando foi aplicado, o
 * percentual, o valor antes e depois e quem aplicou.
 */
public class Reajuste {

    private Long id;
    private final LocalDate aniversario;
    private final LocalDate aplicadoEm;
    private final BigDecimal percentual;
    private final BigDecimal valorAnterior;
    private final BigDecimal valorNovo;
    private final String feitoPor;

    /** Reajuste novo: o valor novo é o anterior mais o percentual, arredondado nos centavos. */
    public Reajuste(LocalDate aniversario, LocalDate aplicadoEm, BigDecimal percentual, BigDecimal valorAnterior,
                    String feitoPor) {
        this(aniversario, aplicadoEm, percentual, valorAnterior, novoValor(valorAnterior, percentual), feitoPor);
    }

    /** Reajuste já gravado, como veio do banco. */
    public Reajuste(LocalDate aniversario, LocalDate aplicadoEm, BigDecimal percentual, BigDecimal valorAnterior,
                    BigDecimal valorNovo, String feitoPor) {
        this.aniversario = Valida.obrigatorio(aniversario, "a data do aniversário do contrato");
        this.aplicadoEm = Valida.obrigatorio(aplicadoEm, "a data do reajuste");
        this.percentual = Valida.obrigatorio(percentual, "o percentual do reajuste");
        this.valorAnterior = Valida.positivo(valorAnterior, "o aluguel antes do reajuste");
        this.valorNovo = Valida.positivo(valorNovo, "o aluguel depois do reajuste");
        this.feitoPor = Valida.textoObrigatorio(feitoPor, "quem aplicou o reajuste");
    }

    /** Ex.: R$ 1.000,00 com 4,80% vira R$ 1.048,00. */
    public static BigDecimal novoValor(BigDecimal valor, BigDecimal percentual) {
        return valor.multiply(BigDecimal.ONE.add(percentual.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** O aniversário do contrato a que este reajuste se refere. */
    public LocalDate getAniversario() {
        return aniversario;
    }

    public LocalDate getAplicadoEm() {
        return aplicadoEm;
    }

    /** O percentual aplicado, ex.: 4.80 para 4,80%. */
    public BigDecimal getPercentual() {
        return percentual;
    }

    public BigDecimal getValorAnterior() {
        return valorAnterior;
    }

    public BigDecimal getValorNovo() {
        return valorNovo;
    }

    public String getFeitoPor() {
        return feitoPor;
    }
}
