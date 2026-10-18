package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void aceitaCpfValidoComOuSemPontuacao() {
        assertTrue(Cpf.ehValido("529.982.247-25"));
        assertTrue(Cpf.ehValido("52998224725"));
        assertEquals("529.982.247-25", new Cpf("52998224725").formatado());
    }

    @Test
    void recusaDigitoErradoTamanhoErradoENumerosRepetidos() {
        assertFalse(Cpf.ehValido("529.982.247-24"));
        assertFalse(Cpf.ehValido("123"));
        assertFalse(Cpf.ehValido("111.111.111-11"));
        assertFalse(Cpf.ehValido(null));
        assertThrows(IllegalArgumentException.class, () -> new Cpf("111.111.111-11"));
    }

    @Test
    void doisCpfsComOMesmoNumeroSaoIguais() {
        assertEquals(new Cpf("529.982.247-25"), new Cpf("52998224725"));
    }
}
