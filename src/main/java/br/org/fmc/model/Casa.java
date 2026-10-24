package br.org.fmc.model;

import java.math.BigDecimal;

/** Casa com os atributos e acessores herdados de Imovel. */
public class Casa extends Imovel {

    public Casa(String codigo, String endereco, BigDecimal valorAluguel) {
        super(codigo, TipoImovel.CASA, endereco, valorAluguel);
    }
}
