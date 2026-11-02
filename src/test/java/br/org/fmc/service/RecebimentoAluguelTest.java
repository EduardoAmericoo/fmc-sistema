package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import br.org.fmc.repository.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecebimentoAluguelTest {
    @TempDir Path pasta;
    private final ServicoAlugueis servico = new ServicoAlugueis();
    private final AluguelRepository repo = new AluguelRepository();
    private Aluguel aluguel;
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void preparar() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Teste, 1", "", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Pessoa Teste", "529.982.247-25",
                "", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        Contrato contrato = new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino,
                PrazoContrato.MESES_12, LocalDate.of(2026, 7, 2), null,
                new DadosContrato.Caucao("3.000,00", LocalDate.of(2026, 7, 1), FormaPagamento.PIX, "")));
        aluguel = new Aluguel(contrato, YearMonth.of(2026, 10));
        repo.salvar(aluguel);
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        Sessao.iniciar(new Usuario("Financeiro Teste", "financeiro@exemplo.org", Perfil.FINANCEIRO));
    }

    @AfterEach void sair() { Sessao.encerrar(); }

    private void dinheiro(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor));
    }

    @Test
    void quintoDiaSemEncargosEFormaPersistida() throws Exception {
        var r = servico.receber(aluguel.getId(), LocalDate.of(2026, 10, 6), FormaPagamento.PIX);
        dinheiro("1070", r.total());
        dinheiro("0", r.multa());
        dinheiro("0", r.juros());
        dinheiro("0", r.correcao());
        assertEquals(r, repo.buscarRecebimento(aluguel.getId()).orElseThrow());
        var recarregado = repo.buscarPorId(aluguel.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 6), recarregado.getDataPagamento());
        assertEquals(SituacaoAluguel.PAGO, recarregado.getSituacao(HOJE));
        assertTrue(repo.listarEmAberto().isEmpty());
    }

    @Test
    void sextoDiaAplicaMultaJurosECorrecao() throws Exception {
        var r = servico.receber(aluguel.getId(), LocalDate.of(2026, 10, 7), FormaPagamento.DINHEIRO);
        dinheiro("107", r.multa());
        dinheiro("10.70", r.juros());
        dinheiro("3.75", r.correcao());
        dinheiro("1191.45", r.total());
        assertEquals(FormaPagamento.DINHEIRO, repo.buscarRecebimento(aluguel.getId()).orElseThrow().forma());
    }

    @Test
    void usaDataInformadaEContaMesOuFracao() throws Exception {
        var r = servico.receber(aluguel.getId(), LocalDate.of(2026, 11, 1), FormaPagamento.TRANSFERENCIA);
        dinheiro("21.40", r.juros());
        dinheiro("7.49", r.correcao());
        dinheiro("1205.89", r.total());
    }

    @Test
    void pagamentoAntecipadoSemEncargos() throws Exception {
        var r = servico.receber(aluguel.getId(), LocalDate.of(2026, 9, 30), FormaPagamento.BOLETO);
        dinheiro("1070", r.total());
        assertEquals(FormaPagamento.BOLETO, repo.buscarRecebimento(aluguel.getId()).orElseThrow().forma());
    }

    @Test
    void repetirNaoAlteraRecebimento() throws Exception {
        var primeiro = servico.receber(aluguel.getId(), LocalDate.of(2026, 10, 7), FormaPagamento.PIX);
        assertThrows(RegraNegocioException.class,
                () -> servico.receber(aluguel.getId(), HOJE, FormaPagamento.DINHEIRO));
        assertEquals(primeiro, repo.buscarRecebimento(aluguel.getId()).orElseThrow());
        assertFalse(repo.registrarRecebimento(primeiro));
    }

    @Test
    void exigeDataFormaEPermissaoSemAlterarBanco() throws Exception {
        assertThrows(RegraNegocioException.class, () -> servico.receber(aluguel.getId(), null, FormaPagamento.PIX));
        assertThrows(RegraNegocioException.class, () -> servico.receber(aluguel.getId(), HOJE, null));
        assertThrows(RegraNegocioException.class,
                () -> servico.receber(aluguel.getId(), HOJE.plusDays(1), FormaPagamento.PIX));
        assertThrows(RegraNegocioException.class, () -> servico.receber(9999, HOJE, FormaPagamento.PIX));
        Sessao.usuario().personalizarPermissoes(Set.of(Acao.VER_ALUGUEIS));
        assertThrows(AcessoNegadoException.class, () -> servico.receber(aluguel.getId(), HOJE, FormaPagamento.PIX));
        assertFalse(repo.buscarPorId(aluguel.getId()).orElseThrow().estaPago());
    }

    @Test
    void falhaAoGravarValoresDesfazBaixa() throws Exception {
        try (var conexao = Database.conectar(); var st = conexao.createStatement()) {
            st.execute("CREATE TRIGGER falhar_recebimento BEFORE INSERT ON aluguel_recebimento "
                    + "BEGIN SELECT RAISE(ABORT, 'falha simulada'); END");
        }
        assertThrows(java.sql.SQLException.class, () -> servico.receber(aluguel.getId(), HOJE, FormaPagamento.PIX));
        assertFalse(repo.buscarPorId(aluguel.getId()).orElseThrow().estaPago());
        assertTrue(repo.buscarRecebimento(aluguel.getId()).isEmpty());
    }

    @Test
    void inicializarBancoExistentePreservaCobrancas() throws Exception {
        Database.inicializar();
        assertTrue(repo.buscarPorId(aluguel.getId()).isPresent());
        assertTrue(repo.buscarRecebimento(aluguel.getId()).isEmpty());
    }
}
