package br.org.fmc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.org.fmc.model.Cpf;
import org.junit.jupiter.api.Test;

/** BOARD-165 · Máscaras de CPF e telefone e as explicações de CPF errado. */
class MascaraTest {

    @Test
    void cpfVaiSendoFormatadoEnquantoDigita() {
        assertEquals("529", Mascara.formatarCpf("529"));
        assertEquals("529.98", Mascara.formatarCpf("52998"));
        assertEquals("529.982.247", Mascara.formatarCpf("529982247"));
        assertEquals("529.982.247-25", Mascara.formatarCpf("52998224725"));
    }

    @Test
    void telefoneCelularEFixo() {
        assertEquals("(7", Mascara.formatarTelefone("7"));
        assertEquals("(79) 9981", Mascara.formatarTelefone("799981"));
        assertEquals("(79) 3211-4410", Mascara.formatarTelefone("7932114410"));
        assertEquals("(79) 99812-4410", Mascara.formatarTelefone("79998124410"));
    }

    @Test
    void cpfExplicaOQueEstaErrado() {
        assertNull(Cpf.problema("529.982.247-25"));
        assertEquals("CPF incompleto: tem 9 números, precisa ter 11.", Cpf.problema("529.982.247"));
        assertEquals("CPF inválido: números todos iguais não formam um CPF.", Cpf.problema("111.111.111-11"));
        assertEquals("CPF inválido: os 2 últimos números não conferem com os 9 primeiros. Confira se digitou certo.",
                Cpf.problema("529.982.247-26"));
    }
}
