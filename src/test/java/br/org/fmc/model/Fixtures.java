package br.org.fmc.model;

import java.math.BigDecimal;

/** Objetos de exemplo para os testes, com dados fictícios. */
final class Fixtures {

    static final Cpf CPF_A = new Cpf("529.982.247-25");
    static final Cpf CPF_B = new Cpf("111.444.777-35");

    private Fixtures() {
    }

    static Imovel imovel(String aluguel) {
        return new Imovel("IM-001", TipoImovel.APARTAMENTO, "Av. Exemplo, 52", new BigDecimal(aluguel));
    }

    static Inquilino inquilino(String renda) {
        Inquilino i = new Inquilino("Maria Teste", CPF_A);
        i.setRendaMensal(new BigDecimal(renda));
        return i;
    }

    static Fianca fianca(String rendaFiador) {
        Fiador f = new Fiador("José Fiador", CPF_B);
        f.setRendaMensal(new BigDecimal(rendaFiador));
        return new Fianca(f);
    }
}
