package br.org.fmc.model;

import java.util.Set;

/**
 * Confere se um telefone brasileiro faz sentido antes de gravar. O telefone é o principal
 * meio de cobrar o inquilino, então "12" ou um número sem DDD não pode passar.
 *
 * Vale: DDD que existe + 9 números começando com 9 (celular) ou 8 números começando
 * com 2 a 5 (fixo). Aceita com ou sem parênteses, espaço e traço.
 */
public final class Telefone {

    /** DDDs que existem no Brasil (Anatel). Ex.: 79 é Sergipe. */
    private static final Set<Integer> DDDS = Set.of(
            11, 12, 13, 14, 15, 16, 17, 18, 19,
            21, 22, 24, 27, 28,
            31, 32, 33, 34, 35, 37, 38,
            41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 53, 54, 55,
            61, 62, 63, 64, 65, 66, 67, 68, 69,
            71, 73, 74, 75, 77, 79,
            81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 92, 93, 94, 95, 96, 97, 98, 99);

    private Telefone() {
    }

    public static boolean ehValido(String telefone) {
        return problema(telefone) == null;
    }

    /**
     * Explica por que o telefone não serve, para mostrar na tela; null se estiver certo.
     * Ex.: "Telefone incompleto: tem 2 números. Digite o DDD e o número, ex.: (79) 99999-9999."
     */
    public static String problema(String telefone) {
        String d = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (d.isEmpty()) {
            return "Informe o telefone com DDD.";
        }
        if (d.length() < 10) {
            return "Telefone incompleto: tem " + d.length()
                    + " números. Digite o DDD e o número, ex.: (79) 99999-9999.";
        }
        if (d.length() > 11) {
            return "Telefone com números a mais: tem " + d.length() + ". O máximo é 11 (DDD + 9 números).";
        }
        if (!DDDS.contains(Integer.parseInt(d.substring(0, 2)))) {
            return "O DDD " + d.substring(0, 2) + " não existe. Em Sergipe o DDD é 79.";
        }
        char primeiro = d.charAt(2);
        if (d.length() == 11 && primeiro != '9') {
            return "Celular com 9 números precisa começar com 9, ex.: (79) 99999-9999.";
        }
        if (d.length() == 10 && (primeiro < '2' || primeiro > '5')) {
            return "Telefone fixo começa com 2, 3, 4 ou 5, ex.: (79) 3211-4410. Se for celular, falta o 9 da frente.";
        }
        return null;
    }
}
