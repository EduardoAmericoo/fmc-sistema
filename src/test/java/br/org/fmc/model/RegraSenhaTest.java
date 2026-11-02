package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.util.SenhaUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** BOARD-62 · Regra de senha forte. */
class RegraSenhaTest {

    @Test
    void senhaForteTemOitoCaracteresMaiusculaMinusculaENumero() {
        assertNull(RegraSenha.problema("Fundacao2026"));
        assertNull(RegraSenha.problema("Fmc@2026"));
    }

    @Test
    void explicaTudoOQueFalta() {
        assertEquals("A senha precisa ter: pelo menos 8 caracteres, uma letra maiúscula, uma letra minúscula "
                + "e um número.", RegraSenha.problema(""));
        assertEquals("A senha precisa ter: uma letra maiúscula.", RegraSenha.problema("teste1234"));
        assertEquals("A senha precisa ter: pelo menos 8 caracteres e um número.", RegraSenha.problema("Teste"));
    }

    @Test
    void senhaProvisoriaGeradaEhSempreForteEDiferente() {
        Set<String> geradas = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String s = SenhaUtil.gerarProvisoria();
            assertNull(RegraSenha.problema(s), s);
            assertTrue(s.matches("[A-HJ-NP-Za-km-z2-9]{4}-[A-HJ-NP-Za-km-z2-9]{4}"), s); // sem 0, O, 1, l e I
            geradas.add(s);
        }
        assertTrue(geradas.size() > 195, "senhas provisórias deveriam quase nunca se repetir");
    }
}
