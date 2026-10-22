package br.org.fmc.ui;

/** Iniciais de um nome para o círculo de avatar (ex.: "Maria das Graças Teste" → "MT"). */
final class Iniciais {

    private Iniciais() {
    }

    static String de(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = nome.trim().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primeira + ultima).toUpperCase();
    }
}
