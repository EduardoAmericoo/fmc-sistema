package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.LancamentoRepository;
import br.org.fmc.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Correções da revisão geral antes da entrega (23/11): problemas achados com teste no banco. */
class RevisaoFinalTest {

    @TempDir
    Path pasta;

    private final ServicoContratos contratos = new ServicoContratos();
    private final ServicoImoveis imoveis = new ServicoImoveis();
    private final ServicoInquilinos inquilinos = new ServicoInquilinos();
    private final AluguelRepository alugueis = new AluguelRepository();

    /** Chaves há 6 meses: o contrato já tem 6 aluguéis gerados. */
    private static final LocalDate CHAVES = LocalDate.now().minusMonths(6);

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
        System.clearProperty("fmc.modo");
    }

    private long imovel() throws Exception {
        imoveis.salvarFormulario(null, "IM-1", "", TipoImovel.CASA, "Rua Itabaiana, 120", "Centro", "", "1.000,00",
                "40,00", "30,00", SituacaoImovel.VAGO);
        return new ImovelRepository().listarTodos().get(0).getId();
    }

    private long maria() throws Exception {
        return inquilinos.cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "(79) 99812-4410",
                "maria@exemplo.org", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
    }

    /** Contrato com caução em dinheiro e os aluguéis dos meses que já passaram. */
    private Contrato contratoComCaucaoEmDinheiro(long imovel, long inquilino) throws Exception {
        return contratos.cadastrarEGerar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, CHAVES, null,
                new DadosContrato.Caucao("3.000,00", CHAVES, FormaPagamento.DINHEIRO, "C-1")), true).contrato();
    }

    private List<Aluguel> emAberto(Contrato c) throws Exception {
        return alugueis.listarDoContrato(c).stream().filter(a -> !a.estaPago()).toList();
    }

    // ---------- 1. encerramento registrado depois da saída ----------

    @Test
    void encerramentoAtrasadoCancelaOsAlugueisQueVencemDepoisDaSaida() throws Exception {
        Contrato c = contratoComCaucaoEmDinheiro(imovel(), maria());
        int gerados = emAberto(c).size();
        LocalDate saida = CHAVES.plusDays(45); // saiu no 2º mês, mas só foi registrado hoje

        contratos.encerrar(c.getId(), saida, MotivoEncerramento.SAIDA_DO_INQUILINO, "");

        List<Aluguel> restam = emAberto(c);
        assertTrue(restam.stream().noneMatch(a -> a.getVencimento().isAfter(saida)), "nada vence depois da saída");
        assertFalse(restam.isEmpty(), "os meses em que morou continuam sendo cobrados");
        try (Connection con = Database.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM aluguel_cancelado")) {
            rs.next();
            assertEquals(gerados - restam.size(), rs.getInt(1), "os cancelados ficam guardados, não somem");
        }
    }

    // ---------- 2. modo cliente ----------

    @Test
    void noModoClienteOAdminInicialPrecisaTrocarASenha() throws Exception {
        Database.usarArquivo(pasta.resolve("cliente.db"));
        Database.inicializar();
        System.setProperty("fmc.modo", "cliente");

        new ServicoAcesso().garantirAdministradorInicial();

        Usuario admin = new UsuarioRepository().buscarPorEmail(ServicoAcesso.EMAIL_ADMIN_INICIAL).orElseThrow();
        assertTrue(admin.temSenhaProvisoria());
    }

    @Test
    void naDemonstracaoOAdminEntraDireto() throws Exception {
        Usuario admin = new UsuarioRepository().buscarPorEmail(ServicoAcesso.EMAIL_ADMIN_INICIAL).orElseThrow();
        assertFalse(admin.temSenhaProvisoria());
    }

    // ---------- 3 e 5. estorno sem Caixa negativo, caução no Caixa ----------

    @Test
    void caucaoEmDinheiroEntraNoCaixa() throws Exception {
        contratoComCaucaoEmDinheiro(imovel(), maria());

        List<Lancamento> caixa = new LancamentoRepository().listar(Conta.CAIXA, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));
        assertEquals(1, caixa.size());
        assertEquals(TipoLancamento.CAUCAO_RECEBIDA, caixa.get(0).tipo());
        assertEquals(0, new BigDecimal("3000.00").compareTo(caixa.get(0).valor()));
        assertEquals(CHAVES, caixa.get(0).data());
    }

    @Test
    void naoCancelaReciboSeODinheiroJaSaiuDoCaixa() throws Exception {
        Contrato c = contratoComCaucaoEmDinheiro(imovel(), maria());
        ServicoCaixaBanco caixaBanco = new ServicoCaixaBanco();
        caixaBanco.informarSaldoInicial(CHAVES.minusDays(1), BigDecimal.ZERO, BigDecimal.ZERO);
        ServicoRecebimento recebimento = new ServicoRecebimento();
        Recibo recibo = recebimento.receber(emAberto(c).get(0), LocalDate.now(), FormaPagamento.DINHEIRO, "");
        // Tudo o que estava no Caixa (caução + aluguel) foi depositado no Banco.
        caixaBanco.depositarNoBanco(LocalDate.now(), caixaBanco.disponivelParaDepositar(LocalDate.now()), "DEP-1");

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> recebimento.cancelarRecibo(recibo, "Recibo emitido errado"));
        assertTrue(e.getMessage().startsWith("O Caixa tem R$ 0,00 e o estorno deste recibo é de"), e.getMessage());

        caixaBanco.sacarParaOCaixa(LocalDate.now(), recibo.getValorTotal(), "SAQ-1");
        recebimento.cancelarRecibo(recibo, "Recibo emitido errado"); // com o saque, agora dá
        assertEquals(0, caixaBanco.disponibilidades().caixa().signum());
    }

    // ---------- 4. imóvel ocupado ----------

    @Test
    void iptuNovoDoImovelOcupadoValeParaOsAlugueisQueAindaVaoVencer() throws Exception {
        long imovel = imovel();
        Contrato c = contratoComCaucaoEmDinheiro(imovel, maria());

        imoveis.salvarFormulario(imovel, "IM-1", "", TipoImovel.CASA, "Rua Itabaiana, 120", "Centro", "", "1.000,00",
                "40,00", "55,00", SituacaoImovel.OCUPADO);

        for (Aluguel a : alugueis.listarDoContrato(c)) {
            BigDecimal esperado = a.getVencimento().isBefore(LocalDate.now()) ? new BigDecimal("30.00")
                    : new BigDecimal("55.00");
            assertEquals(0, esperado.compareTo(a.getValorIptu()), "IPTU do aluguel que vence em " + a.getVencimento());
        }
        assertEquals(0, new BigDecimal("55.00").compareTo(
                new br.org.fmc.repository.ContratoRepository().buscarPorId(c.getId()).orElseThrow().getValorIptu()));
    }

    // ---------- 6. arquivamento ----------

    @Test
    void naoArquivaQuemAindaDeveNemSemMotivo() throws Exception {
        long imovel = imovel();
        long maria = maria();
        Contrato c = contratoComCaucaoEmDinheiro(imovel, maria);
        contratos.encerrar(c.getId(), LocalDate.now(), MotivoEncerramento.SAIDA_DO_INQUILINO, "");

        RegraNegocioException deve = assertThrows(RegraNegocioException.class,
                () -> inquilinos.arquivar(maria, "Mudou de cidade"));
        assertTrue(deve.getMessage().startsWith("Maria Teste ainda tem"), deve.getMessage());

        RegraNegocioException semMotivo = assertThrows(RegraNegocioException.class, () -> imoveis.arquivar(imovel, " "));
        assertEquals("Informe o motivo do arquivamento (ex.: mudou de cidade, imóvel vendido).", semMotivo.getMessage());
    }
}
