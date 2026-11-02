package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import br.org.fmc.repository.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GeracaoAlugueisTest {
    @TempDir Path pasta;
    private final ServicoAlugueis servico = new ServicoAlugueis();
    private final AluguelRepository alugueis = new AluguelRepository();
    private Contrato contrato;
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @BeforeEach
    void preparar() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA,
                "Rua Teste, 1", "Centro", "", "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Pessoa Teste", "529.982.247-25",
                "", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        contrato = new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12,
                LocalDate.of(2026, 7, 2), null,
                new DadosContrato.Caucao("3.000,00", LocalDate.of(2026, 7, 1), FormaPagamento.PIX, "")));
    }

    @AfterEach
    void sair() { Sessao.encerrar(); }

    @Test
    void financeiroGeraValorEVencimentoDoContrato() throws Exception {
        Usuario financeiro = new Usuario("Financeiro Teste", "financeiro@exemplo.org", Perfil.FINANCEIRO);
        Sessao.iniciar(financeiro);
        assertEquals(1, servico.gerarDoMes(MES));
        Aluguel gerado = servico.listarDoMes(MES).get(0);
        assertEquals(contrato.getId(), gerado.getContrato().getId());
        assertEquals(LocalDate.of(2026, 10, 1), gerado.getVencimento());
        assertEquals(0, new BigDecimal("1070.00").compareTo(gerado.base()));
    }

    @Test
    void repetirNaoDuplicaNemAlteraPagamentoOuValores() throws Exception {
        Aluguel existente = new Aluguel(contrato, MES);
        existente.setOutros(new BigDecimal("25"));
        existente.registrarPagamento(LocalDate.of(2026, 10, 1));
        alugueis.salvar(existente);
        assertEquals(0, servico.gerarDoMes(MES));
        assertEquals(0, servico.gerarDoMes(MES));
        List<Aluguel> lista = alugueis.listarDoMes(MES);
        assertEquals(1, lista.size());
        assertEquals(existente.getId(), lista.get(0).getId());
        assertTrue(lista.get(0).estaPago());
        assertEquals(0, existente.base().compareTo(lista.get(0).base()));
        assertEquals(1, servico.gerarDoMes(MES.plusMonths(1)));
    }

    @Test
    void encerradoNaoGeraMesmoParaCompetenciaAnteriorASaida() throws Exception {
        contrato.encerrar(new Encerramento(LocalDate.of(2026, 11, 10), MotivoEncerramento.SAIDA_DO_INQUILINO,
                "", LocalDate.of(2026, 11, 10), "Admin Teste"));
        new ContratoRepository().salvarEncerramento(contrato);
        assertEquals(0, servico.gerarDoMes(MES));
        assertTrue(alugueis.listarDoMes(MES).isEmpty());
    }

    @Test
    void naoGeraAntesDoPrimeiroVencimentoOuDepoisDoTermino() throws Exception {
        assertEquals(0, servico.gerarDoMes(YearMonth.of(2026, 7)));
        assertEquals(0, servico.gerarDoMes(YearMonth.of(2027, 8)));
        assertEquals(1, servico.gerarDoMes(YearMonth.of(2026, 8)));
    }

    @Test
    void geraUmaPorContratoECompletaMesParcial() throws Exception {
        new ServicoImoveis().salvarFormulario(null, "IM-002", "", TipoImovel.CASA,
                "Rua Teste, 2", "Centro", "", "1.200,00", "0", "0", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().stream()
                .filter(i -> i.getCodigo().equals("IM-002")).findFirst().orElseThrow().getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Outra Pessoa Teste",
                "111.444.777-35", "", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12,
                LocalDate.of(2026, 7, 2), null,
                new DadosContrato.Caucao("3.600,00", LocalDate.of(2026, 7, 1), FormaPagamento.PIX, "")));
        alugueis.salvar(new Aluguel(contrato, MES));
        assertEquals(1, servico.gerarDoMes(MES));
        assertEquals(2, alugueis.listarDoMes(MES).size());
        assertEquals(0, servico.gerarDoMes(MES));
        assertEquals(2, servico.gerarDoMes(MES.plusMonths(1)));
    }

    @Test
    void vencimentoNoUltimoDiaDeMesCurto() throws Exception {
        try (var conexao = Database.conectar(); var st = conexao.createStatement()) {
            st.executeUpdate("UPDATE contrato SET data_entrega_chaves = '2026-08-01', "
                    + "data_termino = '2027-08-01', dia_vencimento = 31");
        }
        YearMonth fevereiro = YearMonth.of(2027, 2);
        assertEquals(1, servico.gerarDoMes(fevereiro));
        assertEquals(LocalDate.of(2027, 2, 28), alugueis.listarDoMes(fevereiro).get(0).getVencimento());
    }

    @Test
    void falhaNoLoteNaoDeixaCobrancasParciais() throws Exception {
        Contrato inexistente = new Contrato(contrato.getImovel(), contrato.getInquilino(), contrato.getGarantia(),
                contrato.getPrazo(), contrato.getDataEntregaChaves());
        inexistente.setId(999999L);
        assertThrows(java.sql.SQLException.class, () -> alugueis.inserirAusentes(
                List.of(new Aluguel(contrato, MES), new Aluguel(inexistente, MES))));
        assertTrue(alugueis.listarDoMes(MES).isEmpty());
    }

    @Test
    void mesObrigatorioEPermissaoConferida() {
        assertThrows(RegraNegocioException.class, () -> servico.gerarDoMes(null));
        Usuario consulta = new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO);
        consulta.personalizarPermissoes(Set.of(Acao.VER_ALUGUEIS));
        Sessao.iniciar(consulta);
        assertThrows(AcessoNegadoException.class, () -> servico.gerarDoMes(MES));
    }
}
