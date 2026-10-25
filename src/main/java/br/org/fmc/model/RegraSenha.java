package br.org.fmc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Regra de senha forte (BOARD-62): pelo menos 8 caracteres, com letra maiúscula,
 * letra minúscula e número. Vale para toda senha que a pessoa escolhe.
 */
public final class RegraSenha {

    public static final int TAMANHO_MINIMO = 8;

    /** Os itens da regra, na ordem em que aparecem na tela. */
    public enum Item {
        TAMANHO("Pelo menos " + TAMANHO_MINIMO + " caracteres"),
        MAIUSCULA("Uma letra maiúscula"),
        MINUSCULA("Uma letra minúscula"),
        NUMERO("Um número");

        private final String descricao;

        Item(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        /** Esta senha cumpre este item? */
        public boolean atendidoPor(String senha) {
            String s = senha == null ? "" : senha;
            return switch (this) {
                case TAMANHO -> s.length() >= TAMANHO_MINIMO;
                case MAIUSCULA -> s.chars().anyMatch(Character::isUpperCase);
                case MINUSCULA -> s.chars().anyMatch(Character::isLowerCase);
                case NUMERO -> s.chars().anyMatch(Character::isDigit);
            };
        }
    }

    private RegraSenha() {
    }

    /** O que falta para a senha ser forte. Lista vazia = senha forte. */
    public static List<Item> faltando(String senha) {
        List<Item> faltam = new ArrayList<>();
        for (Item item : Item.values()) {
            if (!item.atendidoPor(senha)) {
                faltam.add(item);
            }
        }
        return faltam;
    }

    /**
     * Explica o que falta, para mostrar na tela; null se a senha for forte.
     * Ex.: "A senha precisa ter: pelo menos 8 caracteres e um número."
     */
    public static String problema(String senha) {
        List<String> itens = faltando(senha).stream().map(i -> i.getDescricao().toLowerCase()).toList();
        if (itens.isEmpty()) {
            return null;
        }
        String lista = itens.size() == 1 ? itens.get(0)
                : String.join(", ", itens.subList(0, itens.size() - 1)) + " e " + itens.get(itens.size() - 1);
        return "A senha precisa ter: " + lista + ".";
    }
}
