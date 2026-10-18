package br.org.fmc.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Multa, juros e correção de um aluguel pago com atraso, pelo contrato-modelo:
 * - até 5 dias depois do vencimento: nada;
 * - depois disso: multa de 10%, juros de 1% ao mês ou fração e correção de 0,35% ao mês.
 *
 * É imutável: para outra data de pagamento, calcule de novo.
 */
public final class Encargos {

    private final long diasAtraso;
    private final int meses;
    private final BigDecimal multa;
    private final BigDecimal juros;
    private final BigDecimal correcao;

    private Encargos(long diasAtraso, int meses, BigDecimal multa, BigDecimal juros, BigDecimal correcao) {
        this.diasAtraso = diasAtraso;
        this.meses = meses;
        this.multa = multa;
        this.juros = juros;
        this.correcao = correcao;
    }

    /**
     * Calcula os encargos de um valor devido.
     *
     * @param base          valor devido (aluguel + luz + IPTU + outros)
     * @param vencimento    data de vencimento
     * @param dataPagamento data em que foi pago
     */
    public static Encargos calcular(BigDecimal base, LocalDate vencimento, LocalDate dataPagamento) {
        long dias = Math.max(0, ChronoUnit.DAYS.between(vencimento, dataPagamento));
        if (dias <= RegrasNegocio.TOLERANCIA_DIAS) {
            return new Encargos(dias, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        int meses = (int) Math.ceil(dias / 30.0); // "ao mês ou fração": 31 dias = 2 meses
        BigDecimal m = BigDecimal.valueOf(meses);
        return new Encargos(dias, meses,
                arredondar(base.multiply(RegrasNegocio.MULTA)),
                arredondar(base.multiply(RegrasNegocio.JUROS_MES).multiply(m)),
                arredondar(base.multiply(RegrasNegocio.CORRECAO_MES).multiply(m)));
    }

    /** Encargos zerados (pagamento em dia). */
    public static Encargos nenhum() {
        return new Encargos(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal arredondar(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Soma dos encargos (sem o valor do aluguel). */
    public BigDecimal total() {
        return multa.add(juros).add(correcao);
    }

    public boolean temEncargos() {
        return total().signum() > 0;
    }

    public long getDiasAtraso() {
        return diasAtraso;
    }

    public int getMeses() {
        return meses;
    }

    public BigDecimal getMulta() {
        return multa;
    }

    public BigDecimal getJuros() {
        return juros;
    }

    public BigDecimal getCorrecao() {
        return correcao;
    }
}
