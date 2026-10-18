package br.org.fmc.model;

/**
 * CPF já validado. Um objeto Cpf só existe se os dígitos verificadores
 * estiverem certos, então quem recebe um Cpf não precisa validar de novo.
 *
 * É imutável: depois de criado, não muda.
 */
public final class Cpf {

    private final String numero; // só os 11 dígitos

    public Cpf(String numero) {
        if (!ehValido(numero)) {
            throw new IllegalArgumentException("CPF inválido: confira os dígitos.");
        }
        this.numero = numero.replaceAll("\\D", "");
    }

    /** Confere os dígitos verificadores. Aceita com ou sem pontos e traço. */
    public static boolean ehValido(String numero) {
        if (numero == null) {
            return false;
        }
        String d = numero.replaceAll("\\D", "");
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, 9) == d.charAt(9) - '0' && digito(d, 10) == d.charAt(10) - '0';
    }

    /** Calcula o dígito verificador da posição informada (9 ou 10). */
    private static int digito(String d, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (d.charAt(i) - '0') * (posicao + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    public String getNumero() {
        return numero;
    }

    /** No formato 000.000.000-00. */
    public String formatado() {
        return numero.substring(0, 3) + "." + numero.substring(3, 6) + "."
                + numero.substring(6, 9) + "-" + numero.substring(9);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cpf outro && numero.equals(outro.numero);
    }

    @Override
    public int hashCode() {
        return numero.hashCode();
    }

    @Override
    public String toString() {
        return formatado();
    }
}
