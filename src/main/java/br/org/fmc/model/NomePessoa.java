package br.org.fmc.model;

/**
 * Confere o nome completo de uma pessoa (inquilino, cônjuge, fiador). O nome vai no contrato
 * e no recibo, então "A" ou "123" não pode passar.
 *
 * Vale: nome e sobrenome, só letras (com acento), espaço, apóstrofo, hífen e ponto
 * (ex.: "Maria D'Ávila", "João Santos-Silva", "José A. Souza"), até RegrasNegocio.NOME_MAXIMO letras.
 */
public final class NomePessoa {

    private NomePessoa() {
    }

    /**
     * Explica por que o nome não serve; null se estiver certo.
     *
     * @param de complemento da mensagem, ex.: "" para o inquilino ou " do fiador"
     */
    public static String problema(String nome, String de) {
        String n = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            return "Informe o nome completo" + de + ".";
        }
        if (n.length() > RegrasNegocio.NOME_MAXIMO) {
            return "O nome" + de + " pode ter no máximo " + RegrasNegocio.NOME_MAXIMO + " letras (tem "
                    + n.length() + ").";
        }
        if (!n.matches("[\\p{L} .'’-]+")) {
            return "O nome" + de + " só pode ter letras: tire números e símbolos.";
        }
        long palavras = n.chars().filter(c -> c == ' ').count() + 1;
        if (palavras < 2 || n.replaceAll("[^\\p{L}]", "").length() < 3) {
            return "Informe o nome completo" + de + ", com nome e sobrenome.";
        }
        return null;
    }
}
