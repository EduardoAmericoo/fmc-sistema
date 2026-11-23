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

    /**
     * Teto do reajuste anual digitado ([D9] BOARD-46), para pegar erro de digitação
     * (ex.: 48 no lugar de 4,8). Um índice negativo não reduz o aluguel: digita-se 0.
     */
    public static final BigDecimal REAJUSTE_MAXIMO_PERCENTUAL = new BigDecimal("30");

    /** [D7] BOARD-64: o Painel avisa da promessa de pagamento a partir de 5 dias antes da data. */
    public static final int DIAS_AVISO_PROMESSA = 5;

    /** [D7] BOARD-64: a data prometida pode ser de no máximo 90 dias depois do contato. */
    public static final int DIAS_MAXIMO_PROMESSA = 90;

    /** Tamanho máximo do nome de uma pessoa: cabe no contrato e no recibo. */
    public static final int NOME_MAXIMO = 120;

    /**
     * Teto de qualquer valor digitado (renda, aluguel, caução, luz, IPTU): R$ 1.000.000,00.
     * Pega zeros a mais por engano (ex.: 2500000 no lugar de 2500,00).
     */
    public static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000.00");

    /** Aluguel mínimo de um imóvel: abaixo disso é quase certo erro de digitação. A validar com o cliente. */
    public static final BigDecimal ALUGUEL_MINIMO = new BigDecimal("50.00");

    /** O endereço precisa de pelo menos 5 letras e números (ex.: "Rua A, 1"). */
    public static final int ENDERECO_MINIMO = 5;

    /**
     * A entrega das chaves pode ser marcada até 60 dias à frente (contrato assinado antes da mudança)
     * e não antes de 2000 (contrato antigo vindo do papel ou da planilha).
     */
    public static final int DIAS_MAXIMO_CHAVES_NO_FUTURO = 60;
    public static final int ANO_MINIMO_CONTRATO = 2000;

    /** A caução é recebida no máximo 60 dias antes da entrega das chaves. */
    public static final int DIAS_MAXIMO_CAUCAO_ANTES_DAS_CHAVES = 60;

    private RegrasNegocio() {
    }
}
