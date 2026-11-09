package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.SituacaoContato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * [D7] BOARD-64 · Contato de cobrança com resultado e promessa de pagamento: a situação na lista,
 * os avisos do Painel (vencendo e não cumprida) e o histórico.
 */
class PromessaPagamentoTest {

    @TempDir
    Path pasta;

    /** Maria deve agosto e setembro: está na lista de cobrança. */
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final LocalDate PROMETIDO = LocalDate.of(2026, 11, 20);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoCobranca cobranca = new ServicoCobranca();
    private final ServicoPainel painel = new ServicoPainel();
    private Contrato contrato;

    @BeforeEach
    void mariaComDoisAlugueisAtrasados() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25",
                "(79) 99999-1111", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        contrato = new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino,
                PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        hojeE(HOJE);
        alugueis.gerarDoMes(YearMonth.of(2026, 8));
        alugueis.gerarDoMes(YearMonth.of(2026, 9));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void hojeE(LocalDate dia) {
        Clock relogio = Clock.fixed(dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        cobranca.usarRelogio(relogio);
        painel.usarRelogio(relogio);
    }

    private ServicoCobranca.Linha linhaDaMaria() throws Exception {
        return cobranca.listarParaCobranca().get(0);
    }

    private List<ServicoPainel.Pendencia> pendencias(ServicoPainel.Tipo tipo) throws Exception {
        return painel.montar().pendencias().stream().filter(p -> p.tipo() == tipo).toList();
    }

    @Test
    void semNenhumContatoApareceComoNuncaContatado() throws Exception {
        assertEquals(SituacaoContato.NUNCA_CONTATADO, linhaDaMaria().situacao());
        assertEquals("Nunca contatado", linhaDaMaria().acompanhamento().texto());
    }

    @Test
    void promessaPrecisaDeDataEntreHojeE90Dias() {
        long id = contrato.getId();
        assertThrows(RegraNegocioException.class, () -> cobranca.registrarContato(id, null, null, ""));
        assertThrows(RegraNegocioException.class,
                () -> cobranca.registrarContato(id, ResultadoContato.PROMETEU_PAGAR, null, ""));
        assertThrows(RegraNegocioException.class,
                () -> cobranca.registrarContato(id, ResultadoContato.PROMETEU_PAGAR, HOJE.minusDays(1), ""));
        assertThrows(RegraNegocioException.class,
                () -> cobranca.registrarContato(id, ResultadoContato.PROMETEU_PAGAR, HOJE.plusDays(91), ""));
    }

    @Test
    void naoAtendeuNaoGuardaDataEMostraHaQuantosDiasEQuemLigou() throws Exception {
        ContatoCobranca salvo = cobranca.registrarContato(contrato.getId(), ResultadoContato.NAO_ATENDEU,
                PROMETIDO, "");
        assertNull(salvo.dataPrometida());

        hojeE(HOJE.plusDays(2));
        ServicoCobranca.Acompanhamento a = linhaDaMaria().acompanhamento();
        assertEquals(SituacaoContato.SEM_RETORNO, a.situacao());
        assertEquals("Não atendeu", a.texto());
        assertEquals("Há 2 dias · " + Sessao.usuario().getNome(), a.detalhe());
    }

    @Test
    void promessaNoPrazoNaoAvisaNoPainel() throws Exception {
        cobranca.registrarContato(contrato.getId(), ResultadoContato.PROMETEU_PAGAR, PROMETIDO, "vai pagar com o 13º");

        ServicoCobranca.Acompanhamento a = linhaDaMaria().acompanhamento();
        assertEquals(SituacaoContato.PROMESSA_NO_PRAZO, a.situacao());
        assertEquals("Prometeu para 20/11", a.texto());
        assertEquals("Faltam 17 dias", a.detalhe());
        assertTrue(pendencias(ServicoPainel.Tipo.PROMESSAS_VENCENDO).isEmpty());
    }

    @Test
    void cincoDiasAntesOPainelAvisaTodoDiaComAContagem() throws Exception {
        cobranca.registrarContato(contrato.getId(), ResultadoContato.PROMETEU_PAGAR, PROMETIDO, "");

        hojeE(PROMETIDO.minusDays(3));
        assertEquals(SituacaoContato.PROMESSA_VENCENDO, linhaDaMaria().situacao());
        List<ServicoPainel.Pendencia> aviso = pendencias(ServicoPainel.Tipo.PROMESSAS_VENCENDO);
        assertEquals(1, aviso.size());
        assertEquals("Maria Teste: faltam 3 dias.", aviso.get(0).detalhe());

        hojeE(PROMETIDO);
        assertEquals("Maria Teste: vence hoje.", pendencias(ServicoPainel.Tipo.PROMESSAS_VENCENDO).get(0).detalhe());
    }

    @Test
    void passadaADataSemPagarViraPromessaNaoCumprida() throws Exception {
        cobranca.registrarContato(contrato.getId(), ResultadoContato.PROMETEU_PAGAR, PROMETIDO, "");

        hojeE(PROMETIDO.plusDays(1));
        ServicoCobranca.Acompanhamento a = linhaDaMaria().acompanhamento();
        assertEquals(SituacaoContato.PROMESSA_NAO_CUMPRIDA, a.situacao());
        assertEquals("Era 20/11 · há 1 dia", a.detalhe());
        List<ServicoPainel.Pendencia> aviso = pendencias(ServicoPainel.Tipo.PROMESSAS_NAO_CUMPRIDAS);
        assertEquals(1, aviso.size());
        assertEquals(ServicoPainel.Gravidade.ALTA, aviso.get(0).gravidade());
        assertTrue(pendencias(ServicoPainel.Tipo.PROMESSAS_VENCENDO).isEmpty());
    }

    @Test
    void quemPagaSaiDaListaEDosAvisos() throws Exception {
        cobranca.registrarContato(contrato.getId(), ResultadoContato.PROMETEU_PAGAR, PROMETIDO, "");
        ServicoRecebimento recebimento = new ServicoRecebimento();
        for (Aluguel a : List.of(alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0),
                alugueis.listarDoMes(YearMonth.of(2026, 9)).get(0))) {
            recebimento.receber(a, HOJE, FormaPagamento.PIX, "");
        }

        hojeE(PROMETIDO.minusDays(2));
        assertTrue(cobranca.listarParaCobranca().isEmpty());
        assertTrue(pendencias(ServicoPainel.Tipo.PROMESSAS_VENCENDO).isEmpty());
        hojeE(PROMETIDO.plusDays(5));
        assertTrue(pendencias(ServicoPainel.Tipo.PROMESSAS_NAO_CUMPRIDAS).isEmpty());
    }

    @Test
    void historicoGuardaTodosOsContatosNaJanelaENaFicha() throws Exception {
        cobranca.registrarContato(contrato.getId(), ResultadoContato.NAO_ATENDEU, null, "");
        hojeE(HOJE.plusDays(1));
        cobranca.registrarContato(contrato.getId(), ResultadoContato.PROMETEU_PAGAR, PROMETIDO, "metade agora");

        List<ContatoCobranca> historico = cobranca.historico(contrato.getId());
        assertEquals(2, historico.size());
        assertEquals("Prometeu pagar até 20/11/2026 · metade agora", historico.get(0).descricao());
        assertEquals("Não atendeu", historico.get(1).descricao());

        List<ContatoCobranca> naFicha = new ServicoInquilinos().ficha(contrato.getInquilino().getId()).contatos();
        assertEquals(historico.stream().map(ContatoCobranca::id).toList(),
                naFicha.stream().map(ContatoCobranca::id).toList());
    }

    @Test
    void contatoAntigoSoComTextoContinuaAparecendo() throws Exception {
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(
                "INSERT INTO cobranca_contato (contrato_id, registrado_em, resultado, feito_por) VALUES (?, ?, ?, ?)")) {
            st.setLong(1, contrato.getId());
            st.setString(2, "2026-11-01T10:00");
            st.setString(3, "Disse que paga semana que vem");
            st.setLong(4, Sessao.usuario().getId());
            st.executeUpdate();
        }

        ContatoCobranca antigo = cobranca.historico(contrato.getId()).get(0);
        assertNull(antigo.tipo());
        assertEquals("Disse que paga semana que vem", antigo.descricao());
        assertEquals("Contato registrado", linhaDaMaria().acompanhamento().texto());
    }
}
