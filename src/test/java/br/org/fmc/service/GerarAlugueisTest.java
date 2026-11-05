package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.DocumentoCobrancaPdf;
import br.org.fmc.model.Usuario;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

class GerarAlugueisTest {

    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    @TempDir
    Path pasta;

    private final ServicoAlugueis servico = new ServicoAlugueis();
    private final AluguelRepository repoAlugueis = new AluguelRepository();
    private final ServicoContratos servicoContratos = new ServicoContratos();
    private Contrato maria;
    private Contrato carlos;

    @BeforeEach
    void bancoComDoisContratosAtivos() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        maria = contrato("IM-001", "Maria Teste", "529.982.247-25");
        carlos = contrato("IM-002", "Carlos Souza", "111.444.777-35");
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Contrato contrato(String codigo, String nome, String cpf) throws Exception {
        new ServicoImoveis().salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(codigo))
                .findFirst().orElseThrow().getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "", "",
                EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        return servicoContratos.cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12,
                LocalDate.of(2026, 7, 2), null,
                new DadosContrato.Caucao("3.000,00", LocalDate.of(2026, 7, 1), FormaPagamento.PIX, "")));
    }

    @Test
    void geraUmAluguelPorContratoComValorEVencimentoDoMes() throws Exception {
        List<Aluguel> gerados = servico.gerarDoMes(OUTUBRO);

        assertEquals(2, gerados.size());
        assertEquals(2, servico.listarDoMes(OUTUBRO).size());
        assertTrue(gerados.stream().allMatch(a -> a.getVencimento().equals(LocalDate.of(2026, 10, 1))));
        assertTrue(gerados.stream().allMatch(a -> a.base().equals(new BigDecimal("1070.00"))));
    }

    @Test
    void executarGeracaoNovamenteNoMesmoMesNaoDuplica() throws Exception {
        servico.gerarDoMes(OUTUBRO);

        assertTrue(servico.gerarDoMes(OUTUBRO).isEmpty());
        assertEquals(2, repoAlugueis.listarDoMes(OUTUBRO).size());
    }

    @Test
    void contratoEncerradoNaoGeraMesmoCompetenciaAnteriorADataDeSaida() throws Exception {
        Contrato encerrado = servicoContratos.encerrar(maria.getId(), LocalDate.of(2026, 9, 15),
                MotivoEncerramento.SAIDA_DO_INQUILINO, "Mudança");
        assertTrue(encerrado.cobreCompetencia(SETEMBRO));

        List<Aluguel> gerados = servico.gerarDoMes(SETEMBRO);

        assertEquals(1, gerados.size());
        assertEquals(carlos.getId(), gerados.get(0).getContrato().getId());
    }

    @Test
    void perfilFinanceiroPodeGerarMasNaoReceberAluguel() throws Exception {
        Usuario financeiro = new Usuario("Financeiro", "financeiro@exemplo.org", Perfil.FINANCEIRO);
        Sessao.iniciar(financeiro);

        assertEquals(2, servico.gerarDoMes(OUTUBRO).size());
        assertFalse(financeiro.pode(br.org.fmc.model.Acao.RECEBER_ALUGUEL));
    }

    @Test
    void demonstrativoPdfMostraDadosEAvisoDeQueNaoPodeSerPago() throws Exception {
        Path arquivo = pasta.resolve("demonstrativo.pdf");
        Aluguel aluguel = new Aluguel(maria, OUTUBRO);

        servico.gerarDemonstrativo(aluguel, LocalDate.of(2026, 10, 2), arquivo);

        try (PDDocument pdf = Loader.loadPDF(arquivo.toFile())) {
            String texto = new PDFTextStripper().getText(pdf);
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(texto.contains("SIMULACAO - SEM VALOR PARA PAGAMENTO"));
            assertTrue(texto.contains("NAO E BOLETO BANCARIO"));
            assertTrue(texto.contains("CT-1001"));
            assertTrue(texto.contains("01/10/2026"));
            assertTrue(texto.contains("R$ 1.070,00"));
            assertTrue(texto.contains("Sem linha digitavel, codigo de barras"));
            assertFalse(texto.matches("(?s).*\\b\\d{47,48}\\b.*"));
        }
    }

    @Test
    void naoGeraDemonstrativoParaAluguelPago() {
        Aluguel pago = new Aluguel(maria, OUTUBRO);
        pago.registrarPagamento(LocalDate.of(2026, 10, 1));

        assertThrows(RegraNegocioException.class, () ->
                servico.gerarDemonstrativo(pago, LocalDate.of(2026, 10, 2), pasta.resolve("pago.pdf")));
    }
}
