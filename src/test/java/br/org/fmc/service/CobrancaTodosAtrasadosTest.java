package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.SituacaoContato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BOARD-188 · A lista de cobrança tem todos os inquilinos em atraso (não só quem deve 2 ou mais),
 * sem quem está na tolerância. Ordem: quem está há mais tempo sem pagar; no empate, a urgência do contato.
 */
class CobrancaTodosAtrasadosTest {

    @TempDir
    Path pasta;

    /** Os aluguéis vencem todo dia 1: setembro e agosto já passaram da tolerância; outubro ainda não. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 4);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoCobranca cobranca = new ServicoCobranca();
    private final Map<String, Long> contratos = new HashMap<>();

    @BeforeEach
    void quatroInquilinos() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        relogio(HOJE);

        inquilino("IM-001", "Maria Teste", "529.982.247-25"); // deve agosto e setembro
        inquilino("IM-002", "João Teste", "111.444.777-35");  // deve setembro (nunca contatado)
        inquilino("IM-003", "Pedro Teste", "123.456.789-09"); // deve setembro (promessa quebrada)
        inquilino("IM-004", "Carla Teste", "390.533.447-05"); // só outubro, ainda na tolerância
        for (YearMonth mes : List.of(AGOSTO, SETEMBRO, OUTUBRO)) {
            alugueis.gerarDoMes(mes);
        }
        pagar("João Teste", AGOSTO);
        pagar("Pedro Teste", AGOSTO);
        pagar("Carla Teste", AGOSTO);
        pagar("Carla Teste", SETEMBRO);

        // Pedro prometeu pagar em 02/10 e não pagou
        relogio(LocalDate.of(2026, 9, 28));
        cobranca.registrarContato(contratos.get("Pedro Teste"), ResultadoContato.PROMETEU_PAGAR,
                LocalDate.of(2026, 10, 2), "");
        relogio(HOJE);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void relogio(LocalDate dia) {
        Clock c = Clock.fixed(dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(c);
        recebimento.usarRelogio(c);
        cobranca.usarRelogio(c);
    }

    private void inquilino(String imovel, String nome, String cpf) throws Exception {
        LocalDate chaves = LocalDate.of(2026, 6, 1); // 1º vencimento em 01/07: todos vencem no dia 1
        new ServicoImoveis().salvarFormulario(null, imovel, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long idImovel = new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(imovel))
                .findFirst().orElseThrow().getId();
        long idInquilino = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "(79) 99999-0000", "teste@exemplo.org",
                EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        contratos.put(nome, new ServicoContratos().cadastrar(new DadosContrato(idImovel, idInquilino,
                PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, ""))).getId());
    }

    private void pagar(String nome, YearMonth mes) throws Exception {
        Aluguel a = alugueis.listarDoMes(mes).stream()
                .filter(x -> x.getContrato().getInquilino().getNome().equals(nome)).findFirst().orElseThrow();
        recebimento.receber(a, a.getVencimento(), FormaPagamento.PIX, "");
    }

    @Test
    void todosOsAtrasadosNaOrdemDoAtrasoENoEmpatePelaUrgenciaDoContato() throws Exception {
        List<ServicoCobranca.Linha> linhas = cobranca.listarParaCobranca();

        // Maria deve desde agosto; Pedro e João desde setembro: Pedro (promessa quebrada) antes de
        // João (nunca contatado). Carla está na tolerância e não entra.
        assertEquals(List.of("Maria Teste 2", "Pedro Teste 1", "João Teste 1"), linhas.stream()
                .map(l -> l.contrato().getInquilino().getNome() + " " + l.mesesEmAberto()).toList());
        assertEquals(SituacaoContato.PROMESSA_NAO_CUMPRIDA, linhas.get(1).situacao());
        assertEquals(SituacaoContato.NUNCA_CONTATADO, linhas.get(2).situacao());
    }

    @Test
    void quemSaiDaToleranciaEntraNaLista() throws Exception {
        relogio(LocalDate.of(2026, 10, 7)); // outubro venceu há 6 dias: passou dos 5 de tolerância

        List<String> nomes = cobranca.listarParaCobranca().stream()
                .map(l -> l.contrato().getInquilino().getNome()).toList();

        assertEquals(List.of("Maria Teste", "Pedro Teste", "João Teste", "Carla Teste"), nomes);
    }
}
