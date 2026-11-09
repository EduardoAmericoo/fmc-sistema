package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.ReciboImpresso;
import br.org.fmc.util.ReciboPdf;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-60 · O recibo pode ser aberto de novo, impresso e salvo em PDF com as duas vias. */
class ReciboTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();

    @BeforeEach
    void mariaComAgostoEmAtraso() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "Apto 101", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Araújo", "529.982.247-25",
                "(79) 99999-1111", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        recebimento.usarRelogio(relogio);
        alugueis.gerarDoMes(YearMonth.of(2026, 8));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Aluguel agosto() throws Exception {
        return alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0);
    }

    @Test
    void reciboDeUmAluguelRecebidoPodeSerAbertoDeNovoComTudoOQueFoiGravado() throws Exception {
        Recibo emitido = recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "TX-123");

        Recibo aberto = recebimento.reciboDoAluguel(agosto()).orElseThrow();

        assertEquals(emitido.getNumero(), aberto.getNumero());
        assertEquals(0, emitido.getValorTotal().compareTo(aberto.getValorTotal()));
        assertEquals(0, emitido.getEncargos().getMulta().compareTo(aberto.getEncargos().getMulta()));
        assertTrue(aberto.getEncargos().getMulta().compareTo(BigDecimal.ZERO) > 0); // pago com atraso
        assertEquals(FormaPagamento.PIX, aberto.getFormaPagamento());
        assertEquals("TX-123", aberto.getDocumento());
        assertEquals(Sessao.usuario().getNome(), aberto.getEmitidoPor().getNome());
    }

    @Test
    void aluguelSemRecebimentoNaoTemRecibo() throws Exception {
        assertTrue(recebimento.reciboDoAluguel(agosto()).isEmpty());
    }

    @Test
    void textosDoReciboSeguemOProtótipo() throws Exception {
        ReciboImpresso r = new ReciboImpresso(recebimento.receber(agosto(), HOJE, FormaPagamento.DINHEIRO, ""));

        assertEquals("Nº 00001/2026", r.numero());
        assertEquals("Agosto de 2026", r.mes());
        assertEquals("MARIA ARAÚJO", r.pagador());
        assertTrue(r.quantiaPorExtenso().startsWith("(MIL"));
        assertTrue(r.referente().contains("agosto de 2026, do imóvel IM-001 · Apto 101"));
        assertTrue(r.foiPago(FormaPagamento.DINHEIRO));
        assertEquals("recibo-00001-2026-CT-1001.pdf", r.nomeArquivo());
    }

    @Test
    void pdfTemAsDuasViasComNumeroNomeEValorPorExtenso() throws Exception {
        Recibo recibo = recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "");
        Path arquivo = pasta.resolve("recibo.pdf");

        ReciboPdf.gerar(recibo, arquivo);

        try (PDDocument pdf = Loader.loadPDF(arquivo.toFile())) {
            String texto = new PDFTextStripper().getText(pdf);
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(texto.contains("1ª via: Locatário"));
            assertTrue(texto.contains("2ª via: Fundação"));
            assertTrue(texto.contains("Nº 00001/2026"));
            assertTrue(texto.contains("MARIA ARAÚJO"));
            assertTrue(texto.contains("FUNDAÇÃO MANUEL CRUZ"));
            assertTrue(texto.contains("REAIS"));
        }
    }
}
