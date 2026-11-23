package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.AluguelRepository;
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
 * Geração dos meses que faltam, contrato por contrato: contrato cadastrado com início no passado
 * e contrato vencido que foi prorrogado depois (os meses do "buraco" voltam a ser cobrados).
 */
class GeracaoMesesQueFaltamTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 15);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoContratos contratos = new ServicoContratos();

    @BeforeEach
    void entrar() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        relogio(HOJE);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void relogio(LocalDate dia) {
        Clock c = Clock.fixed(dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(c);
        contratos.usarRelogio(c);
    }

    private DadosContrato dados(String imovel, String nome, String cpf, LocalDate chaves) throws Exception {
        new ServicoImoveis().salvarFormulario(null, imovel, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long idImovel = new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(imovel))
                .findFirst().orElseThrow().getId();
        long idInquilino = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "(79) 99999-0000", "teste@exemplo.org",
                EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        return new DadosContrato(idImovel, idInquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, ""));
    }

    private static List<String> meses(List<Aluguel> lista) {
        return lista.stream().map(a -> a.getCompetencia().toString()).toList();
    }

    @Test
    void contratoCadastradoComAtrasoJaGeraOsMesesVencidosEmAberto() throws Exception {
        // chaves em 08/08: o 1º aluguel venceu em 07/09 e os próximos todo dia 7
        DadosContrato d = dados("IM-001", "Eduardo Teste", "529.982.247-25", LocalDate.of(2026, 8, 8));
        assertEquals(List.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
                ServicoContratos.mesesQueJaVenceram(LocalDate.of(2026, 8, 8), PrazoContrato.MESES_12, HOJE));

        ServicoContratos.ComAlugueis r = contratos.cadastrarEGerar(d, true);

        assertEquals(List.of("2026-09", "2026-10", "2026-11"), meses(r.gerados()));
        assertTrue(r.gerados().stream().allMatch(a -> a.getSituacao(HOJE) == SituacaoAluguel.EM_ATRASO));
        assertTrue(alugueis.gerarAutomatico().isEmpty()); // entrar depois não duplica
    }

    @Test
    void contratoAntigoDoPapelNaoGeraOsMesesQueJaForamPagos() throws Exception {
        DadosContrato d = dados("IM-001", "Eduardo Teste", "529.982.247-25", LocalDate.of(2026, 8, 8));

        ServicoContratos.ComAlugueis r = contratos.cadastrarEGerar(d, false);
        assertTrue(r.gerados().isEmpty());

        // entrar no sistema hoje não volta a gerar setembro a novembro
        assertTrue(alugueis.gerarAutomatico().isEmpty());
        // em dezembro, entra só dezembro
        relogio(LocalDate.of(2026, 12, 2));
        assertEquals(List.of("2026-12"), meses(alugueis.gerarAutomatico()));
    }

    @Test
    void prorrogarDepoisDeVencidoGeraOsMesesDoBuraco() throws Exception {
        // contrato de 12 meses com chaves em 21/04/2025: vence em 21/04/2026; aluguel todo dia 21
        relogio(LocalDate.of(2025, 4, 21));
        Contrato c = contratos.cadastrarEGerar(
                dados("IM-001", "Daniel Teste", "111.444.777-35", LocalDate.of(2025, 4, 21)), true).contrato();
        relogio(LocalDate.of(2026, 4, 25));
        alugueis.gerarAutomatico(); // gera de maio/2025 a abril/2026 e para no término
        AluguelRepository repo = new AluguelRepository();
        assertEquals(YearMonth.of(2026, 4), repo.ultimoMesPorContrato().get(c.getId()));

        // vencido desde abril: entrar no sistema não gera nada até prorrogar
        relogio(HOJE);
        assertTrue(alugueis.gerarAutomatico().isEmpty());

        ServicoContratos.ComAlugueis r = contratos.prorrogarEGerar(c.getId(), PrazoContrato.MESES_12);

        assertEquals(List.of("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"),
                meses(r.gerados()));
        assertEquals(LocalDate.of(2026, 5, 21), r.gerados().get(0).getVencimento()); // vence na data original
    }
}
