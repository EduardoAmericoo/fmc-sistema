package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Campos que o sistema aceitava errados (telefone "12", inquilino sem renda, chaves em 1900...)
 * e agora são recusados com uma explicação.
 */
class ValidacaoCamposTest {

    @TempDir
    Path pasta;

    private final ServicoInquilinos inquilinos = new ServicoInquilinos();
    private final ServicoImoveis imoveis = new ServicoImoveis();
    private final ServicoContratos contratos = new ServicoContratos();

    private static final String CPF_MARIA = "529.982.247-25";
    private static final String CPF_FIADOR = "123.456.789-09";
    private static final LocalDate CHAVES = LocalDate.now().minusMonths(2).withDayOfMonth(1);

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
    }

    private static DadosInquilino maria(String telefone, String email, String renda) {
        return new DadosInquilino("Maria Teste", CPF_MARIA, telefone, email, EstadoCivil.SOLTEIRO, "", "", renda,
                "", "", "", "");
    }

    private List<String> errosAoCadastrar(DadosInquilino d) {
        return assertThrows(RegraNegocioException.class, () -> inquilinos.cadastrar(d)).getErros();
    }

    // ---------- inquilino ----------

    @Test
    void inquilinoPrecisaDeTelefoneEmailERenda() {
        assertEquals(List.of("Informe o telefone com DDD.", "Informe o e-mail.", "Informe a renda mensal, maior que zero."),
                errosAoCadastrar(maria("", "", "")));
        assertEquals(List.of("Informe a renda mensal, maior que zero."),
                errosAoCadastrar(maria("(79) 99812-4410", "maria@exemplo.org", "0,00")));
    }

    @Test
    void telefoneComDoisNumerosNaoPassaMais() {
        assertEquals(List.of("Telefone incompleto: tem 2 números. Digite o DDD e o número, ex.: (79) 99999-9999."),
                errosAoCadastrar(maria("(12", "maria@exemplo.org", "3.000,00")));
    }

    @Test
    void rendaComZerosAMaisEhRecusada() {
        assertEquals(List.of("A renda mensal passa de R$ 1.000.000,00. Confira se não sobrou um zero."),
                errosAoCadastrar(maria("(79) 99812-4410", "maria@exemplo.org", "25.000.000,00")));
    }

    @Test
    void nomeSoComNumerosOuSemSobrenomeEhRecusado() {
        DadosInquilino numeros = new DadosInquilino("123", CPF_MARIA, "(79) 99812-4410", "maria@exemplo.org",
                EstadoCivil.SOLTEIRO, "", "", "3.000,00", "", "", "", "");
        assertEquals(List.of("O nome só pode ter letras: tire números e símbolos."), errosAoCadastrar(numeros));
    }

    @Test
    void casadoPrecisaDoCônjugeComCpfDiferente() {
        DadosInquilino semConjuge = new DadosInquilino("Maria Teste", CPF_MARIA, "(79) 99812-4410",
                "maria@exemplo.org", EstadoCivil.CASADO, "", "", "3.000,00", "", "", "", "");
        assertEquals(List.of("Estado civil casado(a): informe o nome do cônjuge."), errosAoCadastrar(semConjuge));

        DadosInquilino mesmoCpf = new DadosInquilino("Maria Teste", CPF_MARIA, "(79) 99812-4410",
                "maria@exemplo.org", EstadoCivil.CASADO, "", "", "3.000,00", "", "Carlos Teste", CPF_MARIA, "");
        assertEquals(List.of("O CPF do cônjuge não pode ser o mesmo do inquilino."), errosAoCadastrar(mesmoCpf));
    }

    @Test
    void inquilinoCompletoContinuaSendoCadastrado() throws Exception {
        Inquilino maria = inquilinos.cadastrar(maria("(79) 3211-4410", "maria@exemplo.org", "3.000,00"));
        assertEquals("(79) 3211-4410", maria.getTelefone());
    }

    // ---------- imóvel ----------

    private List<String> errosDoImovel(String codigo, String endereco, String aluguel) {
        return assertThrows(RegraNegocioException.class, () -> imoveis.salvarFormulario(null, codigo, "",
                TipoImovel.CASA, endereco, "", "", aluguel, "", "", SituacaoImovel.VAGO)).getErros();
    }

    @Test
    void imovelPrecisaDeEnderecoCompletoCodigoDeVerdadeEAluguelMinimo() {
        assertEquals(List.of("Endereço incompleto: informe a rua e o número, ex.: Rua Itabaiana, 120."),
                errosDoImovel("IM-1", "x", "1.000,00"));
        assertEquals(List.of("O código do imóvel precisa ter letras ou números, ex.: IM-125."),
                errosDoImovel(".", "Rua Itabaiana, 120", "1.000,00"));
        assertEquals(List.of("O valor do aluguel precisa ser de pelo menos R$ 50,00. Confira se digitou certo."),
                errosDoImovel("IM-1", "Rua Itabaiana, 120", "0,01"));
        assertEquals(List.of("O valor do aluguel passa de R$ 1.000.000,00. Confira se não sobrou um zero."),
                errosDoImovel("IM-1", "Rua Itabaiana, 120", "15000000"));
    }

    // ---------- contrato ----------

    private long imovelVago() throws Exception {
        imoveis.salvarFormulario(null, "IM-1", "", TipoImovel.CASA, "Rua Itabaiana, 120", "Centro", "", "1.000,00",
                "", "", SituacaoImovel.VAGO);
        return new ImovelRepository().listarTodos().get(0).getId();
    }

    private long mariaCadastrada() throws Exception {
        return inquilinos.cadastrar(maria("(79) 99812-4410", "maria@exemplo.org", "5.000,00")).getId();
    }

    private List<String> errosDoContrato(LocalDate chaves, DadosContrato.Fiador fiador, DadosContrato.Caucao caucao)
            throws Exception {
        DadosContrato d = new DadosContrato(imovelVago(), mariaCadastrada(), PrazoContrato.MESES_12, chaves, fiador,
                caucao);
        return assertThrows(RegraNegocioException.class, () -> contratos.cadastrar(d)).getErros();
    }

    @Test
    void fiadorPrecisaDeTelefoneValidoEEndereco() throws Exception {
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", CPF_FIADOR, "(12", EstadoCivil.SOLTEIRO,
                "5.000,00", "", "", "");
        assertEquals(List.of(
                "Fiador: telefone incompleto: tem 2 números. Digite o DDD e o número, ex.: (79) 99999-9999.",
                "Informe o endereço do fiador."), errosDoContrato(CHAVES, fiador, null));
    }

    @Test
    void entregaDasChavesForaDeUmaDataPossivelEhRecusada() throws Exception {
        DadosContrato.Caucao caucao = new DadosContrato.Caucao("3.000,00", LocalDate.of(1900, 1, 1),
                FormaPagamento.PIX, "");
        List<String> erros = errosDoContrato(LocalDate.of(1900, 1, 1), null, caucao);
        assertEquals("A entrega das chaves não pode ser antes de 2000. Confira o ano.", erros.get(0));
    }

    @Test
    void entregaDasChavesMuitoNoFuturoEhRecusada() throws Exception {
        LocalDate longe = LocalDate.now().plusYears(60);
        List<String> erros = errosDoContrato(longe, null,
                new DadosContrato.Caucao("3.000,00", longe, FormaPagamento.PIX, ""));
        assertTrue(erros.get(0).startsWith("A entrega das chaves pode ser no máximo 60 dias à frente"), erros.get(0));
    }

    @Test
    void caucaoRecebidaMuitoAntesDasChavesEhRecusada() throws Exception {
        List<String> erros = errosDoContrato(CHAVES, null,
                new DadosContrato.Caucao("3.000,00", CHAVES.minusDays(61), FormaPagamento.PIX, ""));
        assertEquals(1, erros.size());
        assertTrue(erros.get(0).startsWith("A caução precisa ser recebida no máximo 60 dias antes"), erros.get(0));
    }

    // ---------- recebimento ----------

    @Test
    void pagamentoAntesDaEntregaDasChavesEhRecusado() throws Exception {
        contratos.cadastrar(new DadosContrato(imovelVago(), mariaCadastrada(), PrazoContrato.MESES_12, CHAVES, null,
                new DadosContrato.Caucao("3.000,00", CHAVES, FormaPagamento.PIX, "")));
        ServicoAlugueis alugueis = new ServicoAlugueis();
        alugueis.gerarDoMes(YearMonth.from(CHAVES.plusMonths(1)));
        Aluguel primeiro = alugueis.listarDoMes(YearMonth.from(CHAVES.plusMonths(1))).get(0);

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> new ServicoRecebimento()
                .receber(primeiro, LocalDate.of(1990, 5, 10), FormaPagamento.PIX, ""));
        assertTrue(e.getMessage().startsWith("A data do pagamento não pode ser antes da entrega das chaves"),
                e.getMessage());
    }
}
