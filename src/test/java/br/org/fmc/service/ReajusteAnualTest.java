package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Reajuste;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
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
 * [D9] BOARD-46 · Reajuste anual. Dados fictícios.
 *
 * O contrato de teste começa em 10/01/2025, com 24 meses e aluguel de R$ 1.000 (vence dia 9).
 * O 1º aniversário é 10/01/2026, e "hoje" é 01/03/2026: o reajuste está vencido.
 * Aluguéis: dezembro/2025 pago; janeiro/2026 (vence 09/01, antes do aniversário) e
 * fevereiro/2026 (vence 09/02, depois do aniversário) em aberto.
 */
class ReajusteAnualTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 1);

    @TempDir
    Path pasta;

    private final ServicoContratos servico = new ServicoContratos();
    private final ContratoRepository repo = new ContratoRepository();
    private Contrato contrato;

    @BeforeEach
    void bancoComUmContratoDeAniversarioVencido() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        Imovel imovel = new ImovelRepository().listarTodos().get(0);
        long maria = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "", "",
                EstadoCivil.SOLTEIRO, "", "", "4.000,00", "", "", "", "")).getId();
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "9.000,00", "Rua do Fiador, 10", "", "");
        contrato = servico.cadastrar(new DadosContrato(imovel.getId(), maria, PrazoContrato.MESES_24,
                LocalDate.of(2025, 1, 10), fiador, null));
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

        AluguelRepository alugueis = new AluguelRepository();
        Aluguel dezembro = new Aluguel(contrato, YearMonth.of(2025, 12));
        dezembro.registrarPagamento(LocalDate.of(2025, 12, 9));
        alugueis.salvar(dezembro);
        alugueis.salvar(new Aluguel(contrato, YearMonth.of(2026, 1)));
        alugueis.salvar(new Aluguel(contrato, YearMonth.of(2026, 2)));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Contrato noBanco() throws Exception {
        return repo.buscarPorId(contrato.getId()).orElseThrow();
    }

    private BigDecimal aluguelDe(YearMonth mes) throws Exception {
        return new AluguelRepository().listarDoContrato(contrato).stream()
                .filter(a -> a.getCompetencia().equals(mes)).findFirst().orElseThrow().getValorAluguel();
    }

    private List<String> errosAoReajustar(String percentual) {
        return assertThrows(RegraNegocioException.class, () -> servico.reajustar(contrato.getId(), percentual))
                .getErros();
    }

    @Test
    void aniversarioVencidoDeixaOReajustePendente() {
        assertEquals(LocalDate.of(2026, 1, 10), contrato.proximoReajuste());
        assertTrue(contrato.reajustePendente(HOJE));
        assertFalse(contrato.reajustePendente(LocalDate.of(2026, 1, 9))); // um dia antes do aniversário
    }

    @Test
    void reajusteMudaOAluguelEOsAlugueisAindaNaoPagosDepoisDoAniversario() throws Exception {
        Reajuste r = servico.reajustar(contrato.getId(), "4,80");

        assertEquals(0, new BigDecimal("1048.00").compareTo(r.getValorNovo()));
        Contrato lido = noBanco();
        assertEquals(0, new BigDecimal("1048.00").compareTo(lido.getValorAluguel()));
        assertEquals(1, lido.getReajustes().size());
        assertEquals(LocalDate.of(2026, 1, 10), lido.getReajustes().get(0).getAniversario());
        assertEquals(0, new BigDecimal("4.80").compareTo(lido.getReajustes().get(0).getPercentual()));
        // Fevereiro vence depois do aniversário: aluguel novo. Janeiro (antes) e dezembro (pago) ficam como estavam.
        assertEquals(0, new BigDecimal("1048.00").compareTo(aluguelDe(YearMonth.of(2026, 2))));
        assertEquals(0, new BigDecimal("1000.00").compareTo(aluguelDe(YearMonth.of(2026, 1))));
        assertEquals(0, new BigDecimal("1000.00").compareTo(aluguelDe(YearMonth.of(2025, 12))));
    }

    @Test
    void depoisDeReajustarOProximoSoNoAnoQueVem() throws Exception {
        servico.reajustar(contrato.getId(), "4,8%");

        Contrato lido = noBanco();
        assertFalse(lido.reajustePendente(HOJE));
        assertEquals(LocalDate.of(2027, 1, 10), lido.proximoReajuste());
        assertEquals(List.of("O próximo reajuste do contrato " + contrato.getNumero() + " só vence em 10/01/2027."),
                errosAoReajustar("4,80"));
    }

    @Test
    void zeroPorCentoGuardaOReajusteSemMudarOAluguel() throws Exception {
        servico.reajustar(contrato.getId(), "0");

        Contrato lido = noBanco();
        assertEquals(0, new BigDecimal("1000.00").compareTo(lido.getValorAluguel()));
        assertFalse(lido.reajustePendente(HOJE));
    }

    @Test
    void percentualVazioInvalidoOuAltoDemais() {
        assertEquals(List.of("Informe o percentual do reajuste (média do INPC e do IGP-DI)."), errosAoReajustar(""));
        assertEquals(List.of("Percentual inválido: use só números, ex.: 4,80."), errosAoReajustar("abc"));
        assertEquals(List.of("O reajuste não pode passar de 30%. Confira se digitou certo (ex.: 4,80)."),
                errosAoReajustar("48"));
    }

    @Test
    void contratoEncerradoNaoEReajustado() throws Exception {
        servico.encerrar(contrato.getId(), LocalDate.of(2026, 2, 20), MotivoEncerramento.SAIDA_DO_INQUILINO, "");

        assertEquals(List.of("Contrato encerrado não é reajustado."), errosAoReajustar("4,80"));
    }

    @Test
    void valorNovoArredondadoNosCentavos() {
        assertEquals(new BigDecimal("1396.66"), Reajuste.novoValor(new BigDecimal("1333.33"), new BigDecimal("4.75")));
    }

    @Test
    void filtroDaTelaSoComReajusteVencido() throws Exception {
        List<Contrato> todos = servico.listar();
        assertEquals(1, ServicoContratos.filtrar(todos, null, "", true, HOJE).size());
        servico.reajustar(contrato.getId(), "4,80");
        assertEquals(0, ServicoContratos.filtrar(servico.listar(), null, "", true, HOJE).size());
    }

    @Test
    void soQuemTemAPermissaoReajusta() {
        Sessao.iniciar(new Usuario("Consulta", "financeiro@exemplo.org", Perfil.FINANCEIRO));
        assertThrows(AcessoNegadoException.class, () -> servico.reajustar(contrato.getId(), "4,80"));
    }
}
