package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.Tela;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.service.ServicoPainel.Gravidade;
import br.org.fmc.service.ServicoPainel.NumerosDoMes;
import br.org.fmc.service.ServicoPainel.Painel;
import br.org.fmc.service.ServicoPainel.Pendencia;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BOARD-40 e BOARD-61 · Painel com os números do mês e a lista de pendências. Dados fictícios.
 *
 * "Hoje" é 24/10/2026. Cada aluguel soma o aluguel + R$ 50 de luz + R$ 20 de IPTU:
 * - Maria (IM-001, R$ 1.070/mês, vence dia 9): agosto e setembro em atraso, outubro pago;
 * - Carlos (IM-002, R$ 2.070/mês, vence dia 20): setembro pago, outubro em tolerância (4 dias);
 *   o contrato termina em 20/11/2026, então vence nos próximos 60 dias;
 * - Ana (IM-003, R$ 1.570/mês, vence dia 1): agosto em atraso, setembro pago;
 *   o contrato terminou em 01/09/2026 (vencido);
 * - IM-004 vago.
 * As contas esperadas abaixo foram feitas à mão a partir desses números.
 */
class PainelTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 24);

    @TempDir
    Path pasta;

    private final ServicoPainel servico = new ServicoPainel();
    private final AluguelRepository alugueis = new AluguelRepository();

    @BeforeEach
    void bancoComTresContratosEUmImovelVago() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

        Contrato maria = contrato("IM-001", "1.000,00", "Maria Teste", "529.982.247-25", "3.000,00",
                LocalDate.of(2026, 1, 10));
        Contrato carlos = contrato("IM-002", "2.000,00", "Carlos Teste", "111.444.777-35", "6.000,00",
                LocalDate.of(2025, 11, 20));
        Contrato ana = contrato("IM-003", "1.500,00", "Ana Teste", "390.533.447-05", "4.500,00",
                LocalDate.of(2025, 9, 1));
        novoImovel("IM-004", "1.200,00");

        aluguel(maria, YearMonth.of(2026, 8), null);
        aluguel(maria, YearMonth.of(2026, 9), null);
        aluguel(maria, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 9));
        aluguel(carlos, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 20));
        aluguel(carlos, YearMonth.of(2026, 10), null);
        aluguel(ana, YearMonth.of(2026, 8), null);
        aluguel(ana, YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Imovel novoImovel(String codigo, String valor) throws Exception {
        new ServicoImoveis().salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                valor, "50,00", "20,00", SituacaoImovel.VAGO);
        return new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(codigo))
                .findFirst().orElseThrow();
    }

    private Contrato contrato(String codigo, String valor, String nome, String cpf, String renda, LocalDate chaves)
            throws Exception {
        Imovel imovel = novoImovel(codigo, valor);
        Inquilino inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "", "",
                EstadoCivil.SOLTEIRO, "", "", renda, "", "", "", ""));
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "9.000,00", "Rua do Fiador, 10", "", "");
        return new ServicoContratos().cadastrar(new DadosContrato(imovel.getId(), inquilino.getId(),
                PrazoContrato.MESES_12, chaves, fiador, null));
    }

    private void aluguel(Contrato c, YearMonth mes, LocalDate pagoEm) throws Exception {
        Aluguel a = new Aluguel(c, mes);
        if (pagoEm != null) {
            a.registrarPagamento(pagoEm);
        }
        alugueis.salvar(a);
    }

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void numerosDoMesConferemComASomaFeitaAMao() throws Exception {
        NumerosDoMes n = servico.montar().numeros();

        assertEquals(YearMonth.of(2026, 10), n.mes());
        // Outubro: Maria (1.070, pago) + Carlos (2.070, em tolerância).
        assertEquals(2, n.previsto().quantidade());
        assertEquals(0, reais("3140.00").compareTo(n.previsto().total()));
        assertEquals(1, n.recebido().quantidade());
        assertEquals(0, reais("1070.00").compareTo(n.recebido().total()));
        assertEquals(34, n.percentualRecebido()); // 1.070 / 3.140 = 34,07%
        assertEquals(1, n.aReceber().quantidade());
        assertEquals(0, reais("2070.00").compareTo(n.aReceber().total()));
        // Em atraso, de todos os meses: Maria agosto e setembro + Ana agosto.
        assertEquals(3, n.emAtraso().quantidade());
        assertEquals(0, reais("3710.00").compareTo(n.emAtraso().total()));
        assertTrue(n.atrasoComEncargos().compareTo(n.emAtraso().total()) > 0); // com multa e juros fica maior
    }

    @Test
    void alugueisEmAtrasoDoMaisAntigoParaOMaisRecente() throws Exception {
        List<Aluguel> atrasos = servico.montar().alugueisEmAtraso();

        assertEquals(List.of("Ana Teste 2026-08-01", "Maria Teste 2026-08-09", "Maria Teste 2026-09-09"),
                atrasos.stream().map(a -> a.getContrato().getInquilino().getNome() + " " + a.getVencimento()).toList());
        // Só os atrasados: o de Carlos (em tolerância) e os pagos ficam de fora.
        assertTrue(atrasos.stream().noneMatch(a -> a.estaPago()
                || a.getContrato().getInquilino().getNome().equals("Carlos Teste")));
    }

    @Test
    void pendenciasNaOrdemDeImportancia() throws Exception {
        List<Pendencia> pendencias = servico.montar().pendencias();

        assertEquals(List.of(
                "ALTA 3 Aluguéis em atraso",
                "ALTA 1 Inquilino com mais de 1 mês em atraso",
                "MEDIA 1 Aluguel em tolerância",
                "ALTA 1 Contrato vencido",
                "MEDIA 1 Contrato vence nos próximos 60 dias",
                "INFO 1 Imóvel vago"),
                pendencias.stream().map(p -> p.gravidade() + " " + p.quantidade() + " " + p.titulo()).toList());
        assertEquals(Tela.CONTRATOS, pendencias.get(3).destino());
        assertTrue(pendencias.get(0).detalhe().contains("R$ 3.710,00"), pendencias.get(0).detalhe());
    }

    @Test
    void abrirLevaParaATelaCertaEAListaDeAlugueisEmAbertoBateComOPainel() throws Exception {
        List<Pendencia> pendencias = servico.montar().pendencias();
        assertEquals(List.of(Tela.ALUGUEIS, Tela.ALUGUEIS, Tela.ALUGUEIS, Tela.CONTRATOS, Tela.CONTRATOS, Tela.IMOVEIS),
                pendencias.stream().map(Pendencia::destino).toList());

        // "Em aberto, todos os meses" na tela de Aluguéis: os 3 em atraso + o de Carlos em tolerância.
        List<Aluguel> emAberto = new ServicoAlugueis().listarEmAberto();
        assertEquals(List.of("2026-08-01", "2026-08-09", "2026-09-09", "2026-10-20"),
                emAberto.stream().map(a -> a.getVencimento().toString()).toList());
    }

    @Test
    void maisDeUmMesEmAtrasoMostraSoOsAtrasosDessesInquilinos() throws Exception {
        // No Painel: 1 inquilino (Maria, agosto e setembro). Ana tem só 1 mês em atraso e fica de fora.
        Pendencia cobranca = servico.montar().pendencias().get(1);
        assertEquals(1, cobranca.quantidade());

        // Na tela de Aluguéis, pela mesma regra: só os 2 aluguéis em atraso de Maria.
        List<Aluguel> lista = ServicoAlugueis.deInquilinosComMaisDeUmMesEmAtraso(
                new ServicoAlugueis().listarEmAberto(), HOJE);
        assertEquals(List.of("Maria Teste 2026-08-09", "Maria Teste 2026-09-09"),
                lista.stream().map(a -> a.getContrato().getInquilino().getNome() + " " + a.getVencimento()).toList());
    }

    @Test
    void ocupacaoContaSoImoveisNaoArquivados() throws Exception {
        Imovel arquivado = novoImovel("IM-005", "900,00");
        new ServicoImoveis().arquivar(arquivado.getId(), "Vendido");

        ServicoPainel.Ocupacao o = servico.montar().ocupacao();
        assertEquals(4, o.imoveis());
        assertEquals(3, o.ocupados());
        assertEquals(1, o.vagos());
    }

    @Test
    void cadaParteSoApareceParaQuemPodeVer() throws Exception {
        Usuario soImoveis = new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO);
        soImoveis.personalizarPermissoes(Set.of(Acao.VER_IMOVEIS));
        Sessao.iniciar(soImoveis);

        Painel p = servico.montar();
        assertNull(p.numeros());
        assertTrue(p.alugueisEmAtraso().isEmpty());
        assertNotNull(p.ocupacao());
        assertEquals(List.of(Gravidade.INFO), p.pendencias().stream().map(Pendencia::gravidade).toList());
    }

    @Test
    void semLoginNaoMostraNada() {
        Sessao.encerrar();
        assertThrows(AcessoNegadoException.class, servico::montar);
    }
}
