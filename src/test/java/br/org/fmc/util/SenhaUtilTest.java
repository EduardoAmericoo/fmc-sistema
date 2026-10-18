package br.org.fmc.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SenhaUtilTest {

    @Test
    void senhaCertaConfereESenhaErradaNao() {
        String hash = SenhaUtil.gerarHash("Fmc@2026");
        assertTrue(SenhaUtil.confere("Fmc@2026", hash));
        assertFalse(SenhaUtil.confere("fmc@2026", hash));
        assertFalse(SenhaUtil.confere("", hash));
    }

    @Test
    void oHashNaoContemASenhaEMudaACadaVez() {
        String a = SenhaUtil.gerarHash("Fmc@2026");
        String b = SenhaUtil.gerarHash("Fmc@2026");
        assertFalse(a.contains("Fmc@2026"));
        assertNotEquals(a, b); // sal aleatório
        assertTrue(SenhaUtil.confere("Fmc@2026", b));
    }

    @Test
    void hashInvalidoOuSenhaVazia() {
        assertFalse(SenhaUtil.confere("x", "texto-qualquer"));
        assertFalse(SenhaUtil.confere("x", null));
        assertThrows(IllegalArgumentException.class, () -> SenhaUtil.gerarHash(""));
    }
}
