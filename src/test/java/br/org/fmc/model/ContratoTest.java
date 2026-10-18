package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContratoTest {

    private static final LocalDate ENTREGA = LocalDate.of(2026, 1, 1);

    private Contrato contrato(String aluguel, String renda, Garantia garantia) {
        return new Contrato(Fixtures.imovel(aluguel), Fixtures.inquilino(renda), garantia,
                PrazoContrato.MESES_12, ENTREGA);
    }

    @Test
    void rendaPrecisaSerPeloMenosTresVezesOAluguel() {
        assertTrue(contrato("1000", "3000", Fixtures.fianca("5000")).atendeRegraRenda());
        Contrato baixo = contrato("1000", "2999.99", Fixtures.fianca("5000"));
        assertFalse(baixo.atendeRegraRenda());
        assertEquals(1, baixo.validar().size());
    }

    @Test
    void primeiroVencimentoTrintaDiasDepoisDasChavesETerminoPeloPrazo() {
        Contrato c = contrato("1000", "3000", Fixtures.fianca("5000"));
        assertEquals(LocalDate.of(2026, 1, 31), c.primeiroVencimento());
        assertEquals(31, c.getDiaVencimento());
        assertEquals(LocalDate.of(2027, 1, 1), c.getDataTermino());
    }

    @Test
    void emMesCurtoVenceNoUltimoDia() {
        Contrato c = contrato("1000", "3000", Fixtures.fianca("5000"));
        assertEquals(LocalDate.of(2026, 2, 28), c.vencimentoDe(YearMonth.of(2026, 2)));
    }

    @Test
    void contratoDe12MesesTem12Alugueis() {
        Contrato c = contrato("1000", "3000", Fixtures.fianca("5000"));
        assertFalse(c.cobreCompetencia(YearMonth.of(2025, 12)));
        assertTrue(c.cobreCompetencia(YearMonth.of(2026, 1)));
        assertTrue(c.cobreCompetencia(YearMonth.of(2026, 12)));
        assertFalse(c.cobreCompetencia(YearMonth.of(2027, 1)));
        int total = 0;
        for (YearMonth m = YearMonth.of(2025, 1); m.isBefore(YearMonth.of(2028, 1)); m = m.plusMonths(1)) {
            if (c.cobreCompetencia(m)) {
                total++;
            }
        }
        assertEquals(12, total);
    }

    @Test
    void caucaoPrecisaSerTresAlugueisRecebidaAteAsChaves() {
        Caucao ok = new Caucao(new BigDecimal("3000"), ENTREGA, FormaPagamento.PIX);
        assertTrue(contrato("1000", "3000", ok).validar().isEmpty());

        Caucao pouco = new Caucao(new BigDecimal("2999"), ENTREGA, FormaPagamento.PIX);
        assertEquals(1, contrato("1000", "3000", pouco).validar().size());

        Caucao depois = new Caucao(new BigDecimal("3000"), ENTREGA.plusDays(1), FormaPagamento.DINHEIRO);
        assertEquals(1, contrato("1000", "3000", depois).validar().size());
    }

    @Test
    void fiadorPrecisaDeRendaEConjugeSeCasado() {
        Fianca f = Fixtures.fianca("0");
        f.getFiador().setEstadoCivil(EstadoCivil.CASADO);
        List<String> erros = contrato("1000", "3000", f).validar();
        assertEquals(2, erros.size());
    }

    @Test
    void valoresSaoCopiadosDoImovelEGarantiaEObrigatoria() {
        Imovel im = Fixtures.imovel("1450");
        im.setTaxaLuz(new BigDecimal("22.45"));
        Contrato c = new Contrato(im, Fixtures.inquilino("9000"), Fixtures.fianca("5000"),
                PrazoContrato.MESES_36, ENTREGA);
        im.setValorAluguel(new BigDecimal("2000"));
        assertEquals(new BigDecimal("1450"), c.getValorAluguel());
        assertEquals(new BigDecimal("22.45"), c.getTaxaLuz());
        assertThrows(NullPointerException.class,
                () -> new Contrato(im, Fixtures.inquilino("9000"), null, PrazoContrato.MESES_12, ENTREGA));
    }

    @Test
    void imovelSoPodeSerOcupadoSeEstiverVago() {
        Imovel im = Fixtures.imovel("1000");
        im.ocupar();
        assertThrows(IllegalStateException.class, im::ocupar);
        im.desocupar();
        assertTrue(im.estaDisponivel());
    }
}
