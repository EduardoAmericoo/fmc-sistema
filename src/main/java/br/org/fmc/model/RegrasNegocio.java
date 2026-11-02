package br.org.fmc.model;

import java.math.BigDecimal;

/**
 * Números das regras de negócio da Fundação, num lugar só.
 * Se uma regra mudar (ex.: a multa), muda aqui e vale para o sistema inteiro.
 */
public final class RegrasNegocio {

    /** Dias depois do vencimento em que o aluguel ainda é pago sem encargos. */
    public static final int TOLERANCIA_DIAS = 5;

    /** Multa por atraso: 10% do valor devido. */
    public static final BigDecimal MULTA = new BigDecimal("0.10");

    /** Juros: 1% ao mês ou fração. */
    public static final BigDecimal JUROS_MES = new BigDecimal("0.01");

    /** Correção pelo INPC: índice simulado de 0,35% ao mês. */
    public static final BigDecimal CORRECAO_MES = new BigDecimal("0.0035");

    /** A renda familiar precisa ser pelo menos 3 vezes o aluguel. */
    public static final int MULTIPLO_RENDA = 3;

    /** A caução vale 3 meses de aluguel. */
    public static final int MESES_CAUCAO = 3;

    /** O 1º aluguel vence 30 dias depois da entrega das chaves. */
    public static final int DIAS_1O_VENCIMENTO = 30;

    /** Contrato que vence nos próximos 60 dias já pode ser prorrogado (e aparece como "vencendo"). */
    public static final int DIAS_AVISO_VENCIMENTO = 60;

    private RegrasNegocio() {
    }
}
