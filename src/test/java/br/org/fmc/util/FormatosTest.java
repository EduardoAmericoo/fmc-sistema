package br.org.fmc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
