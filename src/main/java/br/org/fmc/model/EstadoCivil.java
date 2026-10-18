package br.org.fmc.model;

public enum EstadoCivil {
    SOLTEIRO("Solteiro(a)"),
    CASADO("Casado(a)"),
    UNIAO_ESTAVEL("União estável"),
    DIVORCIADO("Divorciado(a)"),
    VIUVO("Viúvo(a)");

    private final String descricao;

    EstadoCivil(String descricao) {
        this.descricao = descricao;
    }

    /** Casado ou em união estável: nesses casos o cônjuge entra no cadastro. */
    public boolean temConjuge() {
        return this == CASADO || this == UNIAO_ESTAVEL;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
