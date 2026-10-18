package br.org.fmc.model;

import java.util.List;

/**
 * Toda locação exige uma garantia: fiança (fiador) ou caução (3 meses de aluguel).
 *
 * É abstrata: cada tipo de garantia valida de um jeito (polimorfismo).
 */
public abstract class Garantia {

    protected Long id;

    /** Problemas encontrados nesta garantia para o contrato. Lista vazia = tudo certo. */
    public abstract List<String> validar(Contrato contrato);

    /** Texto curto para mostrar na tela, ex.: "Fiador: Maria Souza". */
    public abstract String getDescricao();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return getDescricao();
    }
}
