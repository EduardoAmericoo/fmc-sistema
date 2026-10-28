package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Encerramento;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BOARD-174 · Encerrar contrato. Dados fictícios.
 *
 * O contrato de teste começa em 10/01/2025, com 12 meses (termina em 10/01/2026),
 * e os aluguéis vencem todo dia 9. O "hoje" é fixado em 01/07/2025, no meio do prazo.
 */
class EncerrarContratoTest {

    private static final LocalDate CHAVES = LocalDate.of(2025, 1, 10);
    private static final LocalDate HOJE = LocalDate.of(2025, 7, 1);
    private static final LocalDate SAIDA = LocalDate.of(2025, 6, 20);

    @TempDir
    Path pasta;

    private final ServicoContratos servico = new ServicoContratos();
    private final ContratoRepository repoContratos = new ContratoRepository();
    private final ImovelRepository repoImoveis = new ImovelRepository();

    private Imovel imovel;
    private Inquilino maria;
    private Contrato contrato;

    @BeforeEach
    void bancoComUmContratoAtivo() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        imovel = repoImoveis.listarTodos().get(0);
        maria = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "", "",
                EstadoCivil.SOLTEIRO, "", "", "3.000,00", "", "", "", ""));
        contrato = servico.cadastrar(contratoNovo());
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private DadosContrato contratoNovo() {
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "5.000,00", "Rua do Fiador, 10", "", "");
        return new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12, CHAVES, fiador, null);
    }

    private Contrato noBanco() throws Exception {
        return repoContratos.buscarPorId(contrato.getId()).orElseThrow();
    }

    private List<String> errosAoEncerrar(LocalDate saida, MotivoEncerramento motivo, String observacao) {
        return assertThrows(RegraNegocioException.class,
                () -> servico.encerrar(contrato.getId(), saida, motivo, observacao)).getErros();
    }

    @Test
    void encerraEOImovelVoltaAFicarVago() throws Exception {
        assertEquals(SituacaoImovel.OCUPADO, repoImoveis.buscarPorId(imovel.getId()).orElseThrow().getSituacao());

        servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.SAIDA_DO_INQUILINO, "Mudou de cidade");

        Contrato lido = noBanco();
        assertEquals(StatusContrato.ENCERRADO, lido.getStatus());
        assertEquals(SituacaoImovel.VAGO, repoImoveis.buscarPorId(imovel.getId()).orElseThrow().getSituacao());

        Encerramento e = lido.getEncerramento();
        assertEquals(SAIDA, e.getDataSaida());
        assertEquals(MotivoEncerramento.SAIDA_DO_INQUILINO, e.getMotivo());
        assertEquals("Mudou de cidade", e.getObservacao());
        assertEquals(HOJE, e.getRegistradoEm());
        assertEquals(Sessao.usuario().getNome(), e.getFeitoPor());
    }

    @Test
    void contratoAtivoNaoTemEncerramento() throws Exception {
        assertNull(noBanco().getEncerramento());
    }

    @Test
    void depoisDeEncerrarImovelEInquilinoPodemTerOutroContrato() throws Exception {
        servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.FIM_DO_PRAZO, "");

        assertTrue(servico.imoveisVagos().stream().anyMatch(i -> i.getId().equals(imovel.getId())));
        assertTrue(servico.inquilinosSemContrato().stream().anyMatch(i -> i.getId().equals(maria.getId())));
        Contrato novo = servico.cadastrar(contratoNovo());
        assertEquals("CT-1002", novo.getNumero());
    }

    @Test
    void geraAluguelSoAteODiaDaSaidaEMantemOsQueEstaoEmAberto() throws Exception {
        AluguelRepository alugueis = new AluguelRepository();
        YearMonth maio = YearMonth.of(2025, 5);
        alugueis.salvar(new Aluguel(noBanco(), maio)); // gerado antes da saída e ainda não pago

        servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.SAIDA_DO_INQUILINO, "");

        Contrato encerrado = noBanco();
        assertTrue(encerrado.cobreCompetencia(YearMonth.of(2025, 6)));   // vence 09/06, antes da saída (20/06)
        assertFalse(encerrado.cobreCompetencia(YearMonth.of(2025, 7)));  // vence 09/07, depois da saída
        assertFalse(encerrado.cobreCompetencia(YearMonth.of(2025, 12)));
        assertEquals(1, alugueis.listarDoMes(maio).size());              // o aluguel em aberto continua lá
    }

    @Test
    void contratoEncerradoNaoEncerraDeNovoNemProrroga() throws Exception {
        servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.FIM_DO_PRAZO, "");

        assertEquals(List.of("Este contrato já está encerrado."),
                errosAoEncerrar(HOJE, MotivoEncerramento.FIM_DO_PRAZO, ""));
        assertEquals(List.of("Contrato encerrado não pode ser prorrogado."),
                assertThrows(RegraNegocioException.class,
                        () -> servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12)).getErros());
    }

    @Test
    void conferenciasDaDataEDoMotivo() throws Exception {
        assertEquals(List.of("Informe a data em que as chaves foram devolvidas.", "Escolha o motivo do encerramento."),
                errosAoEncerrar(null, null, ""));
        assertTrue(errosAoEncerrar(LocalDate.of(2025, 1, 5), MotivoEncerramento.FIM_DO_PRAZO, "").get(0)
                .contains("antes do início do contrato (10/01/2025)"));
        assertTrue(errosAoEncerrar(HOJE.plusDays(1), MotivoEncerramento.FIM_DO_PRAZO, "").get(0)
                .contains("não pode ser no futuro"));
        assertEquals(List.of("Explique o motivo na observação."),
                errosAoEncerrar(SAIDA, MotivoEncerramento.OUTRO, "  "));

        // Nenhuma tentativa recusada mudou o contrato ou o imóvel.
        assertEquals(StatusContrato.ATIVO, noBanco().getStatus());
        assertEquals(SituacaoImovel.OCUPADO, repoImoveis.buscarPorId(imovel.getId()).orElseThrow().getSituacao());
    }

    @Test
    void podeEncerrarNoProprioDiaEComOutroMotivoExplicado() throws Exception {
        servico.encerrar(contrato.getId(), HOJE, MotivoEncerramento.OUTRO, "Falecimento do inquilino");
        assertEquals("Falecimento do inquilino", noBanco().getEncerramento().getObservacao());
    }

    @Test
    void secretariaEncerraMasFinanceiroNao() throws Exception {
        Sessao.iniciar(new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO));
        assertThrows(AcessoNegadoException.class,
                () -> servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.FIM_DO_PRAZO, ""));

        Sessao.iniciar(new Usuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA));
        servico.encerrar(contrato.getId(), SAIDA, MotivoEncerramento.FIM_DO_PRAZO, "");
        assertEquals("Secretária", noBanco().getEncerramento().getFeitoPor());
    }
}
