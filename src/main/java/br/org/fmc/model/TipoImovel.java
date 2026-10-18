package br.org.fmc.model;

public enum TipoImovel {
    CASA("Casa"),
    APARTAMENTO("Apartamento"),
    SALA_COMERCIAL("Sala comercial"),
    LOJA("Loja");

    private final String descricao;

    TipoImovel(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
