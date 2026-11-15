package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [D4] BOARD-43 · Uma entrada ou saída de dinheiro no Caixa ou no Banco.
 *
 * @param valor     positivo = entrada; negativo = saída
 * @param reciboId  o recibo que gerou o lançamento (null no saldo inicial)
 */
public record Lancamento(Long id, Conta conta, TipoLancamento tipo, LocalDate data, BigDecimal valor,
        String descricao, Long reciboId, Usuario registradoPor, LocalDateTime registradoEm) {

    public Lancamento {
        conta = Valida.obrigatorio(conta, "a conta (Caixa ou Banco)");
        tipo = Valida.obrigatorio(tipo, "o tipo do lançamento");
        data = Valida.obrigatorio(data, "a data do lançamento");
        valor = Valida.obrigatorio(valor, "o valor do lançamento");
        descricao = Valida.textoObrigatorio(descricao, "a descrição do lançamento");
        registradoPor = Valida.obrigatorio(registradoPor, "quem registrou o lançamento");
        registradoEm = Valida.obrigatorio(registradoEm, "quando o lançamento foi registrado");
    }

    public boolean entrada() {
        return valor.signum() > 0;
    }
}
