package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Conta;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
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

/** [D5] BOARD-63 · Saldo inicial, disponibilidades e movimento do mês do Caixa e do Banco. */
class CaixaBancoTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoCaixaBanco caixaBanco = new ServicoCaixaBanco();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void mariaComTresAlugueisEmAberto() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25",
                "(79) 99999-0000", "teste@exemplo.org", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        recebimento.usarRelogio(relogio);
        caixaBanco.usarRelogio(relogio);
        for (YearMonth mes : List.of(AGOSTO, SETEMBRO, OUTUBRO)) {
            alugueis.gerarDoMes(mes);
        }
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Recibo receber(YearMonth mes, LocalDate dia, FormaPagamento forma) throws Exception {
        return recebimento.receber(alugueis.listarDoMes(mes).get(0), dia, forma, "");
    }

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    private static void mesmoValor(BigDecimal esperado, BigDecimal obtido) {
        assertEquals(0, esperado.compareTo(obtido), "esperado " + esperado + ", veio " + obtido);
    }

    @Test
    void semSaldoInicialEDeRecebimentosTudoComecaEmZero() throws Exception {
        ServicoCaixaBanco.Disponibilidades d = caixaBanco.disponibilidades();
        mesmoValor(BigDecimal.ZERO, d.total());
        assertEquals(List.of(Conta.CAIXA, Conta.BANCO), caixaBanco.semSaldoInicial());
    }

    @Test
    void saldoInicialMaisRecebimentosDaAsDisponibilidadesDeHoje() throws Exception {
        caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 1), reais("500.00"), reais("10000.00"));
        Recibo dinheiro = receber(AGOSTO, LocalDate.of(2026, 10, 20), FormaPagamento.DINHEIRO);
        Recibo pix = receber(SETEMBRO, LocalDate.of(2026, 11, 2), FormaPagamento.PIX);

        ServicoCaixaBanco.Disponibilidades d = caixaBanco.disponibilidades();
        mesmoValor(reais("500.00").add(dinheiro.getValorTotal()), d.caixa());
        mesmoValor(reais("10000.00").add(pix.getValorTotal()), d.banco());
        assertTrue(caixaBanco.semSaldoInicial().isEmpty());
    }

    @Test
    void movimentoDoMesTemSaldoAnteriorEntradasESaldoFinal() throws Exception {
        caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 1), null, reais("1000.00"));
        Recibo outubro = receber(AGOSTO, LocalDate.of(2026, 10, 15), FormaPagamento.PIX);
        Recibo novembro1 = receber(SETEMBRO, LocalDate.of(2026, 11, 1), FormaPagamento.TRANSFERENCIA);
        Recibo novembro2 = receber(OUTUBRO, LocalDate.of(2026, 11, 3), FormaPagamento.BOLETO_BANCARIO);

        // outubro: o saldo inicial é o ponto de partida do mês em que foi informado
        ServicoCaixaBanco.Movimento out = caixaBanco.movimento(Conta.BANCO, OUTUBRO);
        mesmoValor(reais("1000.00"), out.saldoAnterior());
        mesmoValor(outubro.getValorTotal(), out.entradas());
        assertEquals(1, out.linhas().size());

        // novembro: o saldo anterior é o saldo final de outubro
        ServicoCaixaBanco.Movimento nov = caixaBanco.movimento(Conta.BANCO, NOVEMBRO);
        mesmoValor(out.saldoFinal(), nov.saldoAnterior());
        mesmoValor(novembro1.getValorTotal().add(novembro2.getValorTotal()), nov.entradas());
        mesmoValor(BigDecimal.ZERO, nov.saidas());
        assertEquals(2, nov.linhas().size());
        mesmoValor(nov.saldoAnterior().add(novembro1.getValorTotal()), nov.linhas().get(0).saldoDepois());
        mesmoValor(nov.saldoFinal(), nov.linhas().get(1).saldoDepois());

        // o Caixa não teve nada
        mesmoValor(BigDecimal.ZERO, caixaBanco.movimento(Conta.CAIXA, NOVEMBRO).saldoFinal());
    }

    @Test
    void recebimentoDeAntesDoSaldoInicialJaEstaDentroDele() throws Exception {
        receber(AGOSTO, LocalDate.of(2026, 9, 10), FormaPagamento.DINHEIRO); // antes da data do saldo
        caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 1), reais("800.00"), null);

        mesmoValor(reais("800.00"), caixaBanco.disponibilidades().caixa());
        assertTrue(caixaBanco.movimento(Conta.CAIXA, SETEMBRO).linhas().isEmpty());
    }

    @Test
    void saldoInicialSoUmaVezPorConta() throws Exception {
        caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 1), reais("100.00"), null);

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 2), reais("200.00"), null));
        assertTrue(e.getErros().get(0).startsWith("O saldo inicial do Caixa já foi informado"));
        // o Banco ainda pode ser informado depois
        caixaBanco.informarSaldoInicial(LocalDate.of(2026, 10, 2), null, reais("300.00"));
        assertTrue(caixaBanco.semSaldoInicial().isEmpty());
    }

    @Test
    void saldoInicialRecusaValorNegativoDataFuturaENadaInformado() {
        assertThrows(RegraNegocioException.class,
                () -> caixaBanco.informarSaldoInicial(HOJE, reais("-1.00"), null));
        assertThrows(RegraNegocioException.class,
                () -> caixaBanco.informarSaldoInicial(HOJE.plusDays(1), reais("1.00"), null));
        assertThrows(RegraNegocioException.class, () -> caixaBanco.informarSaldoInicial(HOJE, null, null));
        assertThrows(RegraNegocioException.class, () -> caixaBanco.informarSaldoInicial(null, reais("1.00"), null));
    }

    @Test
    void mesesVaoDoPrimeiroLancamentoAteOMesAtual() throws Exception {
        receber(AGOSTO, LocalDate.of(2026, 9, 10), FormaPagamento.PIX);
        assertEquals(List.of(NOVEMBRO, OUTUBRO, SETEMBRO), caixaBanco.meses());
    }

    @Test
    void secretariaEFinanceiroVeemMasSoOAdministradorInformaOSaldoInicial() throws Exception {
        assertTrue(Perfil.SECRETARIA.getPermissoes().contains(br.org.fmc.model.Acao.VER_CAIXA_BANCO));
        assertTrue(Perfil.FINANCEIRO.getPermissoes().contains(br.org.fmc.model.Acao.VER_CAIXA_BANCO));
        assertFalse(Perfil.SECRETARIA.getPermissoes().contains(br.org.fmc.model.Acao.INFORMAR_SALDO_INICIAL));

        acesso.criarUsuario("Teste Secretaria", "secretaria@exemplo.org", Perfil.SECRETARIA, "teste123");
        Sessao.encerrar();
        acesso.login("secretaria@exemplo.org", "teste123");
        caixaBanco.disponibilidades(); // vê
        assertThrows(AcessoNegadoException.class,
                () -> caixaBanco.informarSaldoInicial(HOJE, reais("1.00"), null));
    }
}
