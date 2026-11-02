package br.org.fmc.service;

import br.org.fmc.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoletoFicticioTest {
    private Aluguel aluguel() {
        Imovel imovel = new Imovel("IM-TESTE", TipoImovel.CASA, "Rua Fictícia, 1",
                new BigDecimal("1000"));
        Inquilino pessoa = new Inquilino("José da Silva (Teste)", new Cpf("52998224725"));
        Contrato contrato = new Contrato(imovel, pessoa,
                new Caucao(new BigDecimal("3000"), LocalDate.of(2026, 7, 1), FormaPagamento.PIX),
                PrazoContrato.MESES_12, LocalDate.of(2026, 7, 2));
        contrato.setNumero("CT-TESTE");
        Sessao.iniciar(new Usuario("Financeiro Teste", "financeiro@exemplo.org", Perfil.FINANCEIRO));
        return new Aluguel(contrato, YearMonth.of(2026, 10));
    }

    @AfterEach void sair() { Sessao.encerrar(); }

    @Test
    void pdfLegivelComDadosAvisoEEncargosSemAlterarCobranca() throws Exception {
        Aluguel aluguel = aluguel();
        byte[] pdf = new ServicoBoletoFicticio().gerar(aluguel, LocalDate.of(2026, 11, 2));
        try (var documento = Loader.loadPDF(pdf)) {
            assertEquals(1, documento.getNumberOfPages());
            String texto = new PDFTextStripper().getText(documento);
            assertTrue(texto.contains("SEM VALIDADE PARA PAGAMENTO"));
            assertTrue(texto.contains("José da Silva (Teste)"));
            assertTrue(texto.contains("CT-TESTE / 2026-10"));
            assertTrue(texto.contains("01/10/2026"));
            assertTrue(texto.contains("1.127,00"));
            assertTrue(texto.contains("NÃO É BOLETO BANCÁRIO"));
        }
        assertFalse(aluguel.estaPago());
        assertNull(aluguel.getId());
    }

    @Test
    void naoEmiteParaAluguelPago() {
        Aluguel aluguel = aluguel();
        aluguel.registrarPagamento(LocalDate.of(2026, 10, 1));
        assertThrows(RegraNegocioException.class,
                () -> new ServicoBoletoFicticio().gerar(aluguel, LocalDate.of(2026, 11, 2)));
    }

    @Test
    void exigePermissaoDeGerarCobrancas() {
        Aluguel aluguel = aluguel();
        Usuario consulta = Sessao.usuario();
        consulta.personalizarPermissoes(Set.of(Acao.VER_ALUGUEIS));
        assertThrows(AcessoNegadoException.class,
                () -> new ServicoBoletoFicticio().gerar(aluguel, LocalDate.of(2026, 11, 2)));
    }
}
