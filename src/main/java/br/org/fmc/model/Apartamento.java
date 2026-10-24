package br.org.fmc.model;

import java.math.BigDecimal;

/** Apartamento com os atributos e acessores herdados de Imovel. */
public class Apartamento extends Imovel {

    public Apartamento(String codigo, String endereco, BigDecimal valorAluguel) {
        super(codigo, TipoImovel.APARTAMENTO, endereco, valorAluguel);
    }
}
