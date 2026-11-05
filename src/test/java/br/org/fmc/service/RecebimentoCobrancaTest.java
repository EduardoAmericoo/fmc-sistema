package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecebimentoCobrancaTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoCobranca cobranca = new ServicoCobranca();
    private Contrato contrato;

    @BeforeEach
    void prepararBanco() throws Exception {
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
        Clock clock = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(clock);
        recebimento.usarRelogio(clock);
        cobranca.usarRelogio(clock);
        alugueis.gerarDoMes(YearMonth.of(2026, 8));
        alugueis.gerarDoMes(YearMonth.of(2026, 9));
    }

    @AfterEach
    void encerrarSessao() {
        Sessao.encerrar();
    }

    @Test
    void recebeGravaAluguelReciboEReferenciaEmUmaOperacao() throws Exception {
        Aluguel aluguel = alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0);

        var recibo = recebimento.receber(aluguel, HOJE, FormaPagamento.PIX, "TX-123", false);

        assertEquals("00001/2026", recibo.getNumero());
        assertEquals("TX-123", recibo.getDocumento());
        assertTrue(recibo.getEncargos().getMulta().compareTo(BigDecimal.ZERO) > 0);
        Aluguel persistido = alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0);
        assertTrue(persistido.estaPago());
        assertEquals("00001/2026", persistido.getNumeroRecibo());
        assertThrows(RegraNegocioException.class, () ->
                recebimento.receber(persistido, HOJE, FormaPagamento.PIX, "TX-123", false));
    }

    @Test
    void dispensaEncargosERegistraTotalSemAcrescimos() throws Exception {
        Aluguel aluguel = alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0);

        var recibo = recebimento.receber(aluguel, HOJE, FormaPagamento.DINHEIRO, "", true);

        assertEquals(0, recibo.getEncargos().total().compareTo(BigDecimal.ZERO));
        assertEquals(aluguel.base(), recibo.getValorTotal());
    }

    @Test
    void cobrançaAgrupaMesesEGuardaHistoricoDoUltimoContato() throws Exception {
        assertEquals(1, cobranca.listarParaCobranca().size());
        assertEquals(2, cobranca.listarParaCobranca().get(0).mesesEmAberto());

        ContatoCobranca salvo = cobranca.registrarContato(contrato.getId(), "Prometeu pagar ate dia 10");
        assertNotNull(salvo.id());
        ContatoCobranca carregado = cobranca.listarParaCobranca().get(0).ultimoContato();
        assertEquals("Prometeu pagar ate dia 10", carregado.resultado());
        assertEquals(Sessao.usuario().getNome(), carregado.feitoPor().getNome());
        assertFalse(cobranca.listarParaCobranca().isEmpty());
    }

    @Test
    void financeiroPodeRegistrarContatoMasNaoReceber() throws Exception {
        Sessao.iniciar(new Usuario("Financeiro", "financeiro@exemplo.org", Perfil.FINANCEIRO));

        assertEquals(1, cobranca.listarParaCobranca().size());
        assertThrows(AcessoNegadoException.class, () ->
                recebimento.receber(alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0), HOJE,
                        FormaPagamento.PIX, "", false));
    }
}
