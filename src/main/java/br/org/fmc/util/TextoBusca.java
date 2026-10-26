package br.org.fmc.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ajuda nas buscas das telas (BOARD-56): compara textos sem ligar para
 * maiúsculas, acentos e espaços a mais. Ex.: "joao" encontra "João".
 */
public final class TextoBusca {

    private TextoBusca() {
    }

    /** "  João  da SILVA " vira "joao da silva". Nulo vira "". */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    /** As palavras do que foi digitado, já normalizadas. "Maria  Silva" vira [maria, silva]. */
    public static List<String> palavras(String texto) {
        String n = normalizar(texto);
        return n.isEmpty() ? List.of() : Arrays.asList(n.split(" "));
    }

    /** Só os números do texto. "529.982.247-25" vira "52998224725". */
    public static String digitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
