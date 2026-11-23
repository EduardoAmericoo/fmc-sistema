package br.org.fmc.service;

import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.util.List;

/**
 * Lê um valor em dinheiro digitado (renda, aluguel, caução...) e anota o problema na lista de erros,
 * para a tela mostrar tudo de uma vez. Vazio vira zero: quem exige o valor confere depois.
 */
final class ValorDigitado {

    private ValorDigitado() {
    }

    /**
     * @param invalido mensagem para texto que não é número, ex.: "Renda mensal inválida"
     * @param campo    nome do campo na mensagem do teto, ex.: "A renda mensal"
     */
    static BigDecimal ler(String texto, String invalido, String campo, List<String> erros) {
        BigDecimal valor;
        try {
            valor = Formatos.lerValor(texto);
        } catch (IllegalArgumentException e) {
            erros.add(invalido + ": use só números, ex.: 2.500,00.");
            return BigDecimal.ZERO;
        }
        if (valor.compareTo(RegrasNegocio.VALOR_MAXIMO) > 0) {
            erros.add(campo + " passa de " + Formatos.moeda(RegrasNegocio.VALOR_MAXIMO)
                    + ". Confira se não sobrou um zero.");
        }
        return valor;
    }
}
