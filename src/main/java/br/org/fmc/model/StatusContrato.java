package br.org.fmc.model;

public enum StatusContrato {
    ATIVO("Ativo"),
    ENCERRADO("Encerrado");

    private final String descricao;

    StatusContrato(String descricao) {
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
