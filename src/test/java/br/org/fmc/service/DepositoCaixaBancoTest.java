package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.TipoLancamento;
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

/** Depósito do Caixa no Banco: o dinheiro e os cheques saem do Caixa e entram no Banco. */
class DepositoCaixaBancoTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final ServicoCaixaBanco caixaBanco = new ServicoCaixaBanco();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void caixaComMilReais() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        caixaBanco.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
        caixaBanco.informarSaldoInicial(INICIO, reais("1000.00"), reais("5000.00"));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    private static void mesmoValor(String esperado, BigDecimal obtido) {
        assertEquals(0, new BigDecimal(esperado).compareTo(obtido), "esperado " + esperado + ", veio " + obtido);
    }

    @Test
    void depositoTiraDoCaixaPoeNoBancoESemMudarOTotal() throws Exception {
        caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 20), reais("800.00"), "123456");

        ServicoCaixaBanco.Disponibilidades d = caixaBanco.disponibilidades();
        mesmoValor("200.00", d.caixa());
        mesmoValor("5800.00", d.banco());
        mesmoValor("6000.00", d.total());

        ServicoCaixaBanco.Movimento caixa = caixaBanco.movimento(Conta.CAIXA, OUTUBRO);
        mesmoValor("800.00", caixa.saidas());
        assertEquals(TipoLancamento.DEPOSITO, caixa.linhas().get(0).lancamento().tipo());
        assertTrue(caixa.linhas().get(0).lancamento().descricao().contains("comprovante 123456"));
        ServicoCaixaBanco.Movimento banco = caixaBanco.movimento(Conta.BANCO, OUTUBRO);
        mesmoValor("800.00", banco.entradas());
    }

    @Test
    void naoDeixaDepositarMaisDoQueOCaixaTem() throws Exception {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 20), reais("1000.01"), ""));
        assertTrue(e.getErros().get(0).contains("R$ 1.000,00"), e.getErros().toString());
        mesmoValor("1000.00", caixaBanco.disponibilidades().caixa()); // nada foi gravado
    }

    @Test
    void depositoComDataPassadaNaoDeixaOCaixaNegativoNosDiasSeguintes() throws Exception {
        caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 20), reais("600.00"), "");

        // em 10/10 o Caixa tinha 1.000,00, mas depois do depósito de 20/10 só sobram 400,00
        mesmoValor("400.00", caixaBanco.disponivelParaDepositar(LocalDate.of(2026, 10, 10)));
        assertThrows(RegraNegocioException.class,
                () -> caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 10), reais("500.00"), ""));
        caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 10), reais("400.00"), "");
        mesmoValor("0.00", caixaBanco.disponibilidades().caixa());
    }

    @Test
    void dataEValorPrecisamFazerSentido() {
        RegraNegocioException futuro = assertThrows(RegraNegocioException.class,
                () -> caixaBanco.depositarNoBanco(HOJE.plusDays(1), reais("100.00"), ""));
        assertTrue(futuro.getErros().contains("A data do depósito não pode ser no futuro."));
        RegraNegocioException antes = assertThrows(RegraNegocioException.class,
                () -> caixaBanco.depositarNoBanco(INICIO.minusDays(1), reais("100.00"), ""));
        assertTrue(antes.getErros().get(0).contains("antes do saldo inicial"), antes.getErros().toString());
        RegraNegocioException zero = assertThrows(RegraNegocioException.class,
                () -> caixaBanco.depositarNoBanco(HOJE, BigDecimal.ZERO, ""));
        assertTrue(zero.getErros().contains("Informe o valor depositado, maior que zero."));
        assertThrows(RegraNegocioException.class, () -> caixaBanco.depositarNoBanco(null, reais("100.00"), ""));
    }

    @Test
    void secretariaDepositaEFinanceiroSoConsulta() throws Exception {
        assertTrue(Perfil.SECRETARIA.getPermissoes().contains(Acao.DEPOSITAR_NO_BANCO));
        assertFalse(Perfil.FINANCEIRO.getPermissoes().contains(Acao.DEPOSITAR_NO_BANCO));
        acesso.criarUsuario("Carlos Financeiro", "financeiro@exemplo.org", Perfil.FINANCEIRO, "teste123");
        Sessao.encerrar();
        acesso.login("financeiro@exemplo.org", "teste123");

        assertThrows(AcessoNegadoException.class,
                () -> caixaBanco.depositarNoBanco(LocalDate.of(2026, 10, 20), reais("100.00"), ""));
    }
}
