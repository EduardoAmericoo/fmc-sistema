package br.org.fmc.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formatação para mostrar valores na tela e no recibo, no padrão brasileiro. */
public final class Formatos {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatos() {
    }

    /** Ex.: R$ 1.234,56. Nulo vira "—". */
    public static String moeda(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        // O Java separa "R$" do número com um espaço especial; trocamos por um espaço comum.
        return NumberFormat.getCurrencyInstance(BRASIL).format(valor).replace(' ', ' ');
    }

    /** Ex.: 23/09/2026. Nulo vira "—". */
    public static String data(LocalDate data) {
        return data == null ? "—" : data.format(DATA);
    }

    /** Valor por extenso, como no recibo. Ex.: 1500 → "mil e quinhentos reais". */
    public static String porExtenso(BigDecimal valor) {
        long centavosTotais = valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        if (centavosTotais < 0) {
            throw new IllegalArgumentException("Valor negativo não tem extenso.");
        }
        long reais = centavosTotais / 100;
        int centavos = (int) (centavosTotais % 100);
        if (reais == 0 && centavos == 0) {
            return "zero real";
        }
        String textoCentavos = centavos == 0 ? "" : ate999(centavos) + (centavos == 1 ? " centavo" : " centavos");
        if (reais == 0) {
            return textoCentavos;
        }
        return reaisPorExtenso(reais) + (textoCentavos.isEmpty() ? "" : " e " + textoCentavos);
    }

    private static final String[] UNIDADES = {"", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito",
        "nove", "dez", "onze", "doze", "treze", "catorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
    private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta",
        "setenta", "oitenta", "noventa"};
    private static final String[] CENTENAS = {"", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
        "seiscentos", "setecentos", "oitocentos", "novecentos"};

    private static String reaisPorExtenso(long reais) {
        long milhoes = reais / 1_000_000;
        int milhares = (int) (reais % 1_000_000 / 1000);
        int resto = (int) (reais % 1000);
        List<String> partes = new ArrayList<>();
        if (milhoes > 0) {
            partes.add(milhoes == 1 ? "um milhão" : ate999((int) milhoes) + " milhões");
        }
        if (milhares > 0) {
            partes.add(milhares == 1 ? "mil" : ate999(milhares) + " mil");
        }
        if (resto > 0) {
            partes.add(ate999(resto));
        }
        String texto;
        if (partes.size() == 1) {
            texto = partes.get(0);
        } else {
            // "mil e quinhentos", mas "mil, duzentos e trinta"
            boolean usaE = resto > 0 && (resto < 100 || resto % 100 == 0);
            texto = String.join(", ", partes.subList(0, partes.size() - 1))
                    + (usaE ? " e " : ", ") + partes.get(partes.size() - 1);
        }
        if (milhoes > 0 && reais % 1_000_000 == 0) {
            return texto + " de reais";
        }
        return texto + (reais == 1 ? " real" : " reais");
    }

    /** Número de 1 a 999 por extenso. */
    private static String ate999(int n) {
        if (n == 100) {
            return "cem";
        }
        List<String> partes = new ArrayList<>();
        int c = n / 100;
        int r = n % 100;
        if (c > 0) {
            partes.add(CENTENAS[c]);
        }
        if (r > 0) {
            if (r < 20) {
                partes.add(UNIDADES[r]);
            } else {
                int u = r % 10;
                partes.add(DEZENAS[r / 10] + (u > 0 ? " e " + UNIDADES[u] : ""));
            }
        }
        return String.join(" e ", partes);
    }
}
