package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PessoaTest {

    @Test
    void inquilinoSomaRendaOutrasRendasEConjuge() {
        Inquilino i = Fixtures.inquilino("2500");
        i.setOutrasRendas(new BigDecimal("300"));
        i.setEstadoCivil(EstadoCivil.CASADO);
        Conjuge c = new Conjuge("Carlos Teste");
        c.setRenda(new BigDecimal("700"));
        i.setConjuge(c);
        assertEquals(new BigDecimal("3500"), i.rendaConsiderada());
    }

    @Test
    void fiadorUsaSoARendaDele() {
        Fiador f = new Fiador("José Fiador", Fixtures.CPF_B);
        f.setRendaMensal(new BigDecimal("4000"));
        f.setEstadoCivil(EstadoCivil.CASADO);
        Conjuge c = new Conjuge("Ana Fiadora");
        c.setRenda(new BigDecimal("9000"));
        f.setConjuge(c);
        assertEquals(new BigDecimal("4000"), f.rendaConsiderada());
    }

    @Test
    void conjugeSoParaCasadoESomeAoMudarEstadoCivil() {
        Inquilino i = Fixtures.inquilino("2000");
        assertThrows(IllegalArgumentException.class, () -> i.setConjuge(new Conjuge("Alguém")));
        i.setEstadoCivil(EstadoCivil.UNIAO_ESTAVEL);
        i.setConjuge(new Conjuge("Alguém"));
        i.setEstadoCivil(EstadoCivil.DIVORCIADO);
        assertNull(i.getConjuge());
    }

    @Test
    void validaCamposObrigatoriosERendaNegativa() {
        assertThrows(IllegalArgumentException.class, () -> new Inquilino(" ", Fixtures.CPF_A));
        assertThrows(NullPointerException.class, () -> new Inquilino("Maria", null));
        Inquilino i = Fixtures.inquilino("1000");
        assertThrows(IllegalArgumentException.class, () -> i.setRendaMensal(new BigDecimal("-1")));
    }
}
