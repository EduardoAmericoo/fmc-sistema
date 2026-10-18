package br.org.fmc.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pequenas verificações usadas pelos setters do modelo.
 * Se o valor for inválido, lança IllegalArgumentException com uma mensagem
 * que pode ser mostrada direto na tela.
 */
final class Valida {

    private Valida() {
    }

    static String textoObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Informe " + campo + ".");
        }
        return valor.trim();
    }

    static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    static <T> T obrigatorio(T valor, String campo) {
        return Objects.requireNonNull(valor, () -> "Informe " + campo + ".");
    }

    static BigDecimal naoNegativo(BigDecimal valor, String campo) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        if (v.signum() < 0) {
            throw new IllegalArgumentException(primeiraMaiuscula(campo) + " não pode ser negativo.");
        }
        return v;
    }

    static BigDecimal positivo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Informe " + campo + " maior que zero.");
        }
        return valor;
    }

    private static String primeiraMaiuscula(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
