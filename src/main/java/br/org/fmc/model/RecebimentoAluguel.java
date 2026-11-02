package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Valores efetivamente recebidos, preservados mesmo que as regras mudem depois. */
public record RecebimentoAluguel(long aluguelId, LocalDate data, FormaPagamento forma,
        BigDecimal valorBase, BigDecimal multa, BigDecimal juros, BigDecimal correcao, BigDecimal total) {
}
