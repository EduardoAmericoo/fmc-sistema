package br.org.fmc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FormatosTest {

    @Test
    void moedaEDataNoPadraoBrasileiro() {
        assertEquals("R$ 1.234,56", Formatos.moeda(new BigDecimal("1234.56")));
        assertEquals("23/09/2026", Formatos.data(LocalDate.of(2026, 9, 23)));
        assertEquals("—", Formatos.moeda(null));
    }

    @Test
    void valorPorExtenso() {
        assertEquals("um real", Formatos.porExtenso(new BigDecimal("1")));
        assertEquals("cem reais", Formatos.porExtenso(new BigDecimal("100")));
        assertEquals("cento e um reais", Formatos.porExtenso(new BigDecimal("101")));
        assertEquals("mil e quinhentos reais", Formatos.porExtenso(new BigDecimal("1500")));
        assertEquals("cinquenta centavos", Formatos.porExtenso(new BigDecimal("0.50")));
        assertEquals("mil, duzentos e trinta e quatro reais e cinquenta e seis centavos",
                Formatos.porExtenso(new BigDecimal("1234.56")));
        assertEquals("dois milhões de reais", Formatos.porExtenso(new BigDecimal("2000000")));
        assertEquals("vinte e dois reais e quarenta e cinco centavos",
                Formatos.porExtenso(new BigDecimal("22.45")));
    }

    @Test
    void leValorDigitadoDeVariosJeitos() {
        BigDecimal esperado = new BigDecimal("2500.50");
        assertEquals(esperado, Formatos.lerValor("2.500,50"));
        assertEquals(esperado, Formatos.lerValor("2500,5"));
        assertEquals(esperado, Formatos.lerValor("2500.50"));
        assertEquals(esperado, Formatos.lerValor("R$ 2.500,50"));
        assertEquals(new BigDecimal("2500.00"), Formatos.lerValor("2.500"));
        assertEquals(BigDecimal.ZERO, Formatos.lerValor("  "));
        assertThrows(IllegalArgumentException.class, () -> Formatos.lerValor("dois mil"));
        assertThrows(IllegalArgumentException.class, () -> Formatos.lerValor("-100"));
    }

    @Test
    void numeroParaCampoVoltaIgualAoSerLido() {
        assertEquals("2.500,50", Formatos.numero(new BigDecimal("2500.5")));
        assertEquals(new BigDecimal("2500.50"), Formatos.lerValor(Formatos.numero(new BigDecimal("2500.5"))));
    }
}
