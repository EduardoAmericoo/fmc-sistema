package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-36 · Cadastrar contrato ativo, com garantia (BOARD-57) e regra de renda (BOARD-58). Dados fictícios. */
class CadastroContratoTest {

    private static final String CPF_MARIA = "529.982.247-25";
    private static final String CPF_CARLOS = "111.444.777-35";
    private static final String CPF_FIADOR = "123.456.789-09";
    private static final LocalDate CHAVES = LocalDate.of(2026, 11, 1);

    @TempDir
    Path pasta;

    private final ServicoContratos servico = new ServicoContratos();
    private final ServicoImoveis imoveis = new ServicoImoveis();
    private final ImovelRepository repoImoveis = new ImovelRepository();
    private final ContratoRepository repoContratos = new ContratoRepository();

    private Imovel imovel;
    private Inquilino maria;

    @BeforeEach
    void bancoComUmImovelVagoEUmaInquilina() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        imovel = novoImovel("IM-001", "1.000,00");
        maria = novaInquilina("Maria Teste", CPF_MARIA, "3.000,00"); // exatamente 3x o aluguel
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Imovel novoImovel(String codigo, String aluguel) throws Exception {
        imoveis.salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "", aluguel,
                "50,00", "20,00", SituacaoImovel.VAGO);
        return repoImoveis.listarTodos().stream().filter(i -> i.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    private Inquilino novaInquilina(String nome, String cpf, String renda) throws Exception {
        return new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "", "", EstadoCivil.SOLTEIRO,
                "", "", renda, "", "", "", ""));
    }

    private static DadosContrato.Fiador fiador(EstadoCivil estadoCivil, String conjuge) {
        return new DadosContrato.Fiador("José Fiador", CPF_FIADOR, "(79) 99999-1111", estadoCivil, "5.000,00",
                "Rua do Fiador, 10", conjuge, "");
    }

    private DadosContrato comFiador(Imovel i, Inquilino q) {
        return new DadosContrato(i.getId(), q.getId(), PrazoContrato.MESES_12, CHAVES,
                fiador(EstadoCivil.SOLTEIRO, ""), null);
    }

    private DadosContrato comCaucao(String valor, LocalDate recebidaEm) {
        return new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_24, CHAVES, null,
                new DadosContrato.Caucao(valor, recebidaEm, FormaPagamento.PIX, "00001/2026"));
    }

    private SituacaoImovel situacaoNoBanco(Imovel i) throws SQLException {
        return repoImoveis.buscarPorId(i.getId()).orElseThrow().getSituacao();
    }

    @Test
    void cadastraContratoComFiadorEOImovelFicaOcupado() throws Exception {
        Contrato novo = servico.cadastrar(comFiador(imovel, maria));

        assertEquals("CT-1001", novo.getNumero());
        assertEquals(SituacaoImovel.OCUPADO, situacaoNoBanco(imovel));

        Contrato lido = repoContratos.buscarPorId(novo.getId()).orElseThrow();
        assertEquals("Maria Teste", lido.getInquilino().getNome());
        assertEquals("IM-001", lido.getImovel().getCodigo());
        assertEquals(new BigDecimal("1000.00"), lido.getValorAluguel());
        assertEquals(LocalDate.of(2027, 11, 1), lido.getDataTermino());
        assertEquals(1, lido.getDiaVencimento()); // 1º aluguel em 01/12/2026
        Fianca fianca = assertInstanceOf(Fianca.class, lido.getGarantia());
        assertEquals("José Fiador", fianca.getFiador().getNome());
        assertEquals(new BigDecimal("5000.00"), fianca.getFiador().getRendaMensal());
    }

    @Test
    void cadastraContratoComCaucaoDeTresAlugueis() throws Exception {
        Contrato novo = servico.cadastrar(comCaucao("3.000,00", CHAVES.minusDays(2)));

        Caucao caucao = assertInstanceOf(Caucao.class, repoContratos.buscarPorId(novo.getId()).orElseThrow().getGarantia());
        assertEquals(new BigDecimal("3000.00"), caucao.getValor());
        assertEquals(FormaPagamento.PIX, caucao.getFormaPagamento());
        assertEquals("00001/2026", caucao.getNumeroRecibo());
    }

    @Test
    void rendaMenorQueTresAlugueisBloqueiaOCadastro() throws Exception {
        Inquilino carlos = novaInquilina("Carlos Teste", CPF_CARLOS, "2.999,99");

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(comFiador(imovel, carlos)));

        assertEquals(List.of("A renda familiar do inquilino (R$ 2.999,99) precisa ser pelo menos 3 vezes o aluguel "
                + "(R$ 3.000,00)."), e.getErros());
        assertTrue(servico.listar().isEmpty());
        assertEquals(SituacaoImovel.VAGO, situacaoNoBanco(imovel));
    }

    @Test
    void imovelOcupadoNaoRecebeOutroContrato() throws Exception {
        servico.cadastrar(comFiador(imovel, maria));
        Inquilino carlos = novaInquilina("Carlos Teste", CPF_CARLOS, "9.000,00");

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(comFiador(imovel, carlos)));

        assertEquals(List.of("O imóvel IM-001 está ocupado: só imóvel vago pode receber contrato."), e.getErros());
    }

    @Test
    void inquilinoComContratoAtivoNaoPegaOutroImovel() throws Exception {
        servico.cadastrar(comFiador(imovel, maria));
        Imovel outro = novoImovel("IM-002", "800,00");

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(comFiador(outro, maria)));

        assertEquals(List.of("Maria Teste já tem um contrato ativo."), e.getErros());
        // No formulário, Maria e o IM-001 já não aparecem como opção.
        assertTrue(servico.inquilinosSemContrato().stream().noneMatch(i -> i.getNome().equals("Maria Teste")));
        assertEquals(List.of("IM-002"), servico.imoveisVagos().stream().map(Imovel::getCodigo).toList());
    }

    @Test
    void mostraTodosOsCamposQueFaltamDeUmaVez() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(new DadosContrato(null, null, null, null, null, null)));

        assertEquals(List.of("Escolha o imóvel.", "Escolha o inquilino.", "Escolha o prazo do contrato.",
                "Informe a data de entrega das chaves.", "Escolha a garantia: fiador ou caução."), e.getErros());
    }

    @Test
    void fiadorNaoPodeSerOProprioInquilino() {
        DadosContrato.Fiador mesmaPessoa = new DadosContrato.Fiador("Maria Teste", CPF_MARIA, "",
                EstadoCivil.SOLTEIRO, "5.000,00", "", "", "");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.cadastrar(
                new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12, CHAVES, mesmaPessoa, null)));

        assertEquals(List.of("O fiador não pode ser o próprio inquilino."), e.getErros());
    }

    @Test
    void fiadorCasadoPrecisaInformarOConjuge() throws Exception {
        DadosContrato semConjuge = new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12, CHAVES,
                fiador(EstadoCivil.CASADO, ""), null);
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.cadastrar(semConjuge));
        assertEquals(List.of("Fiador casado: informe também o cônjuge."), e.getErros());

        Contrato comConjuge = servico.cadastrar(new DadosContrato(imovel.getId(), maria.getId(),
                PrazoContrato.MESES_12, CHAVES, fiador(EstadoCivil.CASADO, "Ana Fiadora"), null));
        Fianca fianca = (Fianca) repoContratos.buscarPorId(comConjuge.getId()).orElseThrow().getGarantia();
        assertEquals("Ana Fiadora", fianca.getFiador().getConjuge().getNome());
    }

    @Test
    void caucaoMenorQueTresAlugueisOuRecebidaDepoisDasChavesNaoServe() {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(comCaucao("2.999,00", CHAVES.plusDays(1))));

        assertEquals(List.of("A caução precisa ser de pelo menos 3 meses de aluguel (R$ 3.000,00).",
                "A caução precisa ser recebida até a entrega das chaves."), e.getErros());
    }

    @Test
    void financeiroVeOsContratosMasNaoCadastra() throws Exception {
        servico.cadastrar(comFiador(imovel, maria));
        Sessao.iniciar(new Usuario("Consulta", "consulta@example.org", Perfil.FINANCEIRO));

        assertEquals(1, servico.listar().size());
        assertThrows(AcessoNegadoException.class, () -> servico.cadastrar(comFiador(imovel, maria)));
    }

    @Test
    void contratoGuardaOAluguelDoDiaDoCadastro() throws Exception {
        Contrato novo = servico.cadastrar(comFiador(imovel, maria));
        imoveis.salvarFormulario(imovel.getId(), "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.200,00", "50,00", "20,00", SituacaoImovel.OCUPADO); // reajuste no imóvel

        assertEquals(new BigDecimal("1000.00"), repoContratos.buscarPorId(novo.getId()).orElseThrow().getValorAluguel());
    }

    @Test
    void imovelComContratoNaoPodeSerExcluido() throws Exception {
        servico.cadastrar(comFiador(imovel, maria));

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> imoveis.excluir(imovel.getId()));
        assertEquals(List.of("Este imóvel tem contrato cadastrado e não pode ser excluído."), e.getErros());
    }

    /** Tudo ou nada: se o imóvel deixou de estar vago no meio do caminho, nem o contrato nem a garantia ficam gravados. */
    @Test
    void seAlgoFalharNadaFicaGravado() throws Exception {
        Contrato contrato = new Contrato(imovel, maria, new Caucao(new BigDecimal("3000"), CHAVES,
                FormaPagamento.PIX), PrazoContrato.MESES_12, CHAVES);
        imoveis.salvarFormulario(imovel.getId(), "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.EM_MANUTENCAO); // alguém mudou o imóvel

        SQLException e = assertThrows(SQLException.class, () -> repoContratos.salvar(contrato));

        assertEquals("O imóvel IM-001 não está mais vago.", e.getMessage());
        assertEquals(0, linhas("contrato"));
        assertEquals(0, linhas("garantia"));
        assertEquals(null, contrato.getId());
    }

    private static int linhas(String tabela) throws SQLException {
        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            return rs.getInt(1);
        }
    }
}
