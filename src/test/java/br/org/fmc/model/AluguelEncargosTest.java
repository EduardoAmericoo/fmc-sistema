package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AluguelEncargosTest {

    private static final BigDecimal BASE = new BigDecimal("1000.00");
    private static final LocalDate VENC = LocalDate.of(2026, 9, 1);

    @Test
    void ateCincoDiasNaoTemEncargos() {
        assertEquals(0, Encargos.calcular(BASE, VENC, VENC.minusDays(3)).total().signum());
        assertEquals(0, Encargos.calcular(BASE, VENC, VENC.plusDays(5)).total().signum());
    }

    @Test
    void seisDiasCobraMultaJurosECorrecaoDeUmMes() {
        Encargos e = Encargos.calcular(BASE, VENC, VENC.plusDays(6));
        assertEquals(1, e.getMeses());
        assertEquals(new BigDecimal("100.00"), e.getMulta());
        assertEquals(new BigDecimal("10.00"), e.getJuros());
        assertEquals(new BigDecimal("3.50"), e.getCorrecao());
        assertEquals(new BigDecimal("113.50"), e.total());
    }

    @Test
    void trintaEUmDiasContamComoDoisMeses() {
        Encargos e = Encargos.calcular(BASE, VENC, VENC.plusDays(31));
        assertEquals(2, e.getMeses());
        assertEquals(new BigDecimal("100.00"), e.getMulta());
        assertEquals(new BigDecimal("20.00"), e.getJuros());
        assertEquals(new BigDecimal("7.00"), e.getCorrecao());
    }

    private Contrato contrato() {
        return new Contrato(Fixtures.imovel("1000.00"), Fixtures.inquilino("3000"), Fixtures.fianca("5000"),
                PrazoContrato.MESES_12, LocalDate.of(2026, 1, 2));
    }

    @Test
    void situacaoDoAluguelMudaComOsDias() {
        Aluguel a = new Aluguel(contrato(), YearMonth.of(2026, 9));
        LocalDate v = a.getVencimento();
        assertEquals(SituacaoAluguel.A_VENCER, a.getSituacao(v));
        assertEquals(SituacaoAluguel.EM_TOLERANCIA, a.getSituacao(v.plusDays(5)));
        assertEquals(SituacaoAluguel.EM_ATRASO, a.getSituacao(v.plusDays(6)));
        a.registrarPagamento(v.plusDays(10));
        assertEquals(SituacaoAluguel.PAGO, a.getSituacao(v.plusDays(20)));
        assertThrows(IllegalStateException.class, () -> a.registrarPagamento(v));
    }

    @Test
    void naoCriaAluguelForaDoContrato() {
        assertThrows(IllegalArgumentException.class, () -> new Aluguel(contrato(), YearMonth.of(2025, 12)));
    }

    @Test
    void reciboTemNumeroComCincoDigitosETotalComEncargos() {
        Aluguel a = new Aluguel(contrato(), YearMonth.of(2026, 9));
        Usuario u = new Usuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA);
        LocalDate pago = a.getVencimento().plusDays(6);
        Recibo r = new Recibo(2026, 816, a, pago, FormaPagamento.PIX, a.encargosEm(pago), u);
        assertEquals("00816/2026", r.getNumero());
        assertEquals(new BigDecimal("1113.50"), r.getValorTotal());
        assertEquals("mil, cento e treze reais e cinquenta centavos", r.valorPorExtenso());
    }
}
