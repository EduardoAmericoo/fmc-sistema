package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Telefone com DDD que existe e nome completo de verdade, antes de gravar. */
class TelefoneENomeTest {

    @Test
    void celularEFixoComDddValemComOuSemMascara() {
        assertTrue(Telefone.ehValido("(79) 99812-4410"));
        assertTrue(Telefone.ehValido("79998124410"));
        assertTrue(Telefone.ehValido("(79) 3211-4410"));
        assertTrue(Telefone.ehValido("(11) 2345-6789"));
    }

    @Test
    void telefoneCurtoSemDddOuComDddQueNaoExisteEhRecusado() {
        assertEquals("Informe o telefone com DDD.", Telefone.problema(""));
        assertEquals("Informe o telefone com DDD.", Telefone.problema(null));
        assertEquals("Telefone incompleto: tem 2 números. Digite o DDD e o número, ex.: (79) 99999-9999.",
                Telefone.problema("(12"));
        assertEquals("Telefone incompleto: tem 9 números. Digite o DDD e o número, ex.: (79) 99999-9999.",
                Telefone.problema("99812-4410"));
        assertEquals("O DDD 00 não existe. Em Sergipe o DDD é 79.", Telefone.problema("(00) 99999-9999"));
        assertEquals("O DDD 20 não existe. Em Sergipe o DDD é 79.", Telefone.problema("(20) 99999-9999"));
        assertEquals("Telefone com números a mais: tem 12. O máximo é 11 (DDD + 9 números).",
                Telefone.problema("799981244100"));
    }

    @Test
    void celularPrecisaComecarCom9EFixoCom2a5() {
        assertEquals("Celular com 9 números precisa começar com 9, ex.: (79) 99999-9999.",
                Telefone.problema("(79) 89812-4410"));
        assertEquals("Telefone fixo começa com 2, 3, 4 ou 5, ex.: (79) 3211-4410. Se for celular, falta o 9 da frente.",
                Telefone.problema("(79) 9812-4410"));
    }

    @Test
    void nomeCompletoComAcentoApostrofoEHifenVale() {
        assertNull(NomePessoa.problema("Maria Teste", ""));
        assertNull(NomePessoa.problema("  José   D'Ávila  Santos-Silva ", ""));
        assertNull(NomePessoa.problema("Ana B. Souza", ""));
    }

    @Test
    void nomeVazioCurtoComNumeroOuGrandeDemaisEhRecusado() {
        assertEquals("Informe o nome completo.", NomePessoa.problema(" ", ""));
        assertEquals("Informe o nome completo do fiador, com nome e sobrenome.", NomePessoa.problema("Maria", " do fiador"));
        assertEquals("Informe o nome completo, com nome e sobrenome.", NomePessoa.problema("A B", ""));
        assertEquals("O nome só pode ter letras: tire números e símbolos.", NomePessoa.problema("123", ""));
        assertEquals("O nome só pode ter letras: tire números e símbolos.", NomePessoa.problema("Maria Teste 2", ""));
        assertEquals("O nome pode ter no máximo 120 letras (tem 600).", NomePessoa.problema("A".repeat(600), ""));
    }
}
