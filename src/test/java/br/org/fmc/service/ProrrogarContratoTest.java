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
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Prorrogacao;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
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
 * BOARD-169 · Prorrogar contrato vencido ou vencendo. Dados fictícios.
 *
 * O contrato de teste começa em 10/01/2025, com 12 meses: termina em 10/01/2026
 * e os aluguéis vencem todo dia 9. O "hoje" de cada teste é fixado com usarRelogio.
 */
class ProrrogarContratoTest {

    private static final LocalDate CHAVES = LocalDate.of(2025, 1, 10);
    private static final LocalDate TERMINO = LocalDate.of(2026, 1, 10);

    @TempDir
    Path pasta;

    private final ServicoContratos servico = new ServicoContratos();
    private final ContratoRepository repoContratos = new ContratoRepository();

    private Contrato contrato;

    @BeforeEach
    void bancoComUmContratoDe12Meses() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        Imovel imovel = new ImovelRepository().listarTodos().get(0);
        Inquilino maria = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "", "",
                EstadoCivil.SOLTEIRO, "", "", "3.000,00", "", "", "", ""));
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "5.000,00", "Rua do Fiador, 10", "", "");
        contrato = servico.cadastrar(new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12,
                CHAVES, fiador, null));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void hojeE(LocalDate dia) {
        servico.usarRelogio(Clock.fixed(dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    private Contrato noBanco() throws Exception {
        return repoContratos.buscarPorId(contrato.getId()).orElseThrow();
    }

    @Test
    void prorrogaContratoVencidoAPartirDoTerminoAtual() throws Exception {
        hojeE(LocalDate.of(2026, 3, 1)); // venceu há 50 dias
        assertTrue(servico.podeProrrogar(noBanco()));

        Contrato prorrogado = servico.prorrogar(contrato.getId(), PrazoContrato.MESES_24);

        // Conta do término atual (10/01/2026), não de hoje.
        assertEquals(LocalDate.of(2028, 1, 10), prorrogado.getDataTermino());
        assertEquals(LocalDate.of(2028, 1, 10), noBanco().getDataTermino());
    }

    @Test
    void prorrogaContratoQueVenceNosProximos60Dias() throws Exception {
        hojeE(LocalDate.of(2025, 12, 1)); // vence daqui a 40 dias

        servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12);

        assertEquals(LocalDate.of(2027, 1, 10), noBanco().getDataTermino());
    }

    @Test
    void contratoQueSoVenceDepoisDe60DiasAindaNaoProrroga() throws Exception {
        hojeE(LocalDate.of(2025, 11, 10)); // faltam 61 dias
        assertFalse(servico.podeProrrogar(noBanco()));
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12));
        assertTrue(erro.getErros().get(0).contains("a partir de 11/11/2025"), erro.getErros().toString());

        hojeE(LocalDate.of(2025, 11, 11)); // faltam exatamente 60 dias: já pode
        assertTrue(servico.podeProrrogar(noBanco()));
        assertEquals(TERMINO, noBanco().getDataTermino()); // a tentativa recusada não mudou nada
    }

    @Test
    void contratoEncerradoNaoProrroga() throws Exception {
        try (Connection conexao = Database.conectar(); Statement st = conexao.createStatement()) {
            st.executeUpdate("UPDATE contrato SET status = 'ENCERRADO' WHERE id = " + contrato.getId());
        }
        hojeE(LocalDate.of(2026, 3, 1));

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12));
        assertEquals(List.of("Contrato encerrado não pode ser prorrogado."), erro.getErros());
        assertTrue(noBanco().getProrrogacoes().isEmpty());
    }

    @Test
    void semPrazoAvisa() throws Exception {
        hojeE(LocalDate.of(2026, 3, 1));
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> servico.prorrogar(contrato.getId(), null));
        assertEquals(List.of("Escolha o prazo da prorrogação."), erro.getErros());
    }

    @Test
    void depoisDeProrrogarVoltaAGerarAluguel() throws Exception {
        YearMonth fevereiro = YearMonth.of(2026, 2);
        assertFalse(noBanco().cobreCompetencia(fevereiro)); // vencido: fevereiro não tem aluguel
        assertThrows(IllegalArgumentException.class, () -> new Aluguel(noBanco(), fevereiro));

        hojeE(LocalDate.of(2026, 3, 1));
        servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12);

        Contrato prorrogado = noBanco();
        assertTrue(prorrogado.cobreCompetencia(fevereiro)); // inclusive o mês desde o vencimento
        assertTrue(prorrogado.cobreCompetencia(YearMonth.of(2027, 1))); // vence 09/01/2027, antes do término 10/01/2027
        assertFalse(prorrogado.cobreCompetencia(YearMonth.of(2027, 2))); // depois do novo término, para de novo

        // O aluguel de fevereiro agora pode ser criado e gravado (é o que a BOARD-37 vai fazer ao gerar o mês).
        AluguelRepository alugueis = new AluguelRepository();
        alugueis.salvar(new Aluguel(prorrogado, fevereiro));
        assertEquals(1, alugueis.listarDoMes(fevereiro).size());
    }

    @Test
    void historicoGuardaCadaProrrogacao() throws Exception {
        hojeE(LocalDate.of(2026, 1, 5));
        servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12); // até 10/01/2027
        hojeE(LocalDate.of(2026, 12, 20));
        servico.prorrogar(contrato.getId(), PrazoContrato.MESES_36); // até 10/01/2030

        List<Prorrogacao> historico = noBanco().getProrrogacoes();
        assertEquals(2, historico.size());

        Prorrogacao primeira = historico.get(0);
        assertEquals(LocalDate.of(2026, 1, 5), primeira.getData());
        assertEquals(TERMINO, primeira.getTerminoAnterior());
        assertEquals(LocalDate.of(2027, 1, 10), primeira.getNovoTermino());
        assertEquals(PrazoContrato.MESES_12, primeira.getPrazo());
        assertEquals(Sessao.usuario().getNome(), primeira.getFeitaPor());

        Prorrogacao segunda = historico.get(1);
        assertEquals(LocalDate.of(2027, 1, 10), segunda.getTerminoAnterior());
        assertEquals(LocalDate.of(2030, 1, 10), segunda.getNovoTermino());
        assertEquals(LocalDate.of(2030, 1, 10), noBanco().getDataTermino());
    }

    @Test
    void secretariaProrrogaMasFinanceiroNao() throws Exception {
        hojeE(LocalDate.of(2026, 3, 1));
        Sessao.iniciar(new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO));
        assertThrows(AcessoNegadoException.class, () -> servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12));

        Sessao.iniciar(new Usuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA));
        servico.prorrogar(contrato.getId(), PrazoContrato.MESES_12);
        assertEquals("Secretária", noBanco().getProrrogacoes().get(0).getFeitaPor());
    }
}
