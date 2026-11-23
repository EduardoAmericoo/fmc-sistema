package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Conta;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.LancamentoRepository;
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

/** [D4] BOARD-43 · Cada recibo entra sozinho no Caixa (dinheiro e cheque) ou no Banco (o resto). */
class LancamentoCaixaBancoTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final LancamentoRepository lancamentos = new LancamentoRepository();

    @BeforeEach
    void mariaComTresAlugueisEmAberto() throws Exception {
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
                "(79) 99999-0000", "teste@exemplo.org", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        recebimento.usarRelogio(relogio);
        for (YearMonth mes : List.of(AGOSTO, SETEMBRO, OUTUBRO)) {
            alugueis.gerarDoMes(mes);
        }
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Aluguel aluguel(YearMonth mes) throws Exception {
        return alugueis.listarDoMes(mes).get(0);
    }

    private List<Lancamento> todos(Conta conta) throws Exception {
        return lancamentos.listar(conta, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1));
    }

    @Test
    void dinheiroEChequeVaoParaOCaixaEORestoParaOBanco() {
        assertEquals(Conta.CAIXA, Conta.de(FormaPagamento.DINHEIRO));
        assertEquals(Conta.CAIXA, Conta.de(FormaPagamento.CHEQUE));
        assertEquals(Conta.BANCO, Conta.de(FormaPagamento.PIX));
        assertEquals(Conta.BANCO, Conta.de(FormaPagamento.TRANSFERENCIA));
        assertEquals(Conta.BANCO, Conta.de(FormaPagamento.BOLETO_BANCARIO));
    }

    @Test
    void receberEmDinheiroLancaOValorTotalNoCaixaNaDataDoPagamento() throws Exception {
        Recibo recibo = recebimento.receber(aluguel(AGOSTO), HOJE, FormaPagamento.DINHEIRO, "");

        List<Lancamento> caixa = todos(Conta.CAIXA);
        assertEquals(1, caixa.size());
        Lancamento l = caixa.get(0);
        assertEquals(TipoLancamento.RECEBIMENTO_ALUGUEL, l.tipo());
        assertEquals(0, recibo.getValorTotal().compareTo(l.valor())); // aluguel, taxas, multa, juros e correção
        assertEquals(HOJE, l.data());
        assertEquals(recibo.getId(), l.reciboId());
        assertTrue(l.descricao().contains("recibo " + recibo.getNumero()));
        assertTrue(todos(Conta.BANCO).isEmpty());
        assertEquals(Conta.CAIXA, recibo.getConta());
    }

    @Test
    void pixVaiParaOBanco() throws Exception {
        Recibo recibo = recebimento.receber(aluguel(SETEMBRO), HOJE, FormaPagamento.PIX, "TX-1");

        assertEquals(1, todos(Conta.BANCO).size());
        assertEquals(recibo.getId(), lancamentos.doRecibo(recibo.getId()).orElseThrow().reciboId());
        assertTrue(todos(Conta.CAIXA).isEmpty());
    }

    @Test
    void comDispensaDeEncargosLancaSoOQueFoiPago() throws Exception {
        Aluguel agosto = aluguel(AGOSTO);
        Recibo recibo = recebimento.receber(agosto, HOJE, FormaPagamento.PIX, "", true, "Acordo com a diretoria");

        Lancamento l = todos(Conta.BANCO).get(0);
        assertEquals(0, agosto.base().compareTo(l.valor()));
        assertEquals(0, recibo.getValorTotal().compareTo(l.valor()));
    }

    @Test
    void saldoSomaOsLancamentosAnterioresADataDeCadaConta() throws Exception {
        Recibo dinheiro = recebimento.receber(aluguel(AGOSTO), HOJE.minusDays(2), FormaPagamento.DINHEIRO, "");
        Recibo cheque = recebimento.receber(aluguel(SETEMBRO), HOJE.minusDays(1), FormaPagamento.CHEQUE, "");
        Recibo pix = recebimento.receber(aluguel(OUTUBRO), HOJE, FormaPagamento.PIX, "");

        assertEquals(0, dinheiro.getValorTotal().add(cheque.getValorTotal())
                .compareTo(lancamentos.saldoAntesDe(Conta.CAIXA, HOJE.plusDays(1))));
        assertEquals(0, dinheiro.getValorTotal().compareTo(lancamentos.saldoAntesDe(Conta.CAIXA, HOJE.minusDays(1))));
        assertEquals(0, pix.getValorTotal().compareTo(lancamentos.saldoAntesDe(Conta.BANCO, HOJE.plusDays(1))));
        assertEquals(0, BigDecimal.ZERO.compareTo(lancamentos.saldoAntesDe(Conta.BANCO, HOJE)));
    }

    @Test
    void receberDuasVezesNaoDuplicaOLancamento() throws Exception {
        Aluguel agosto = aluguel(AGOSTO);
        Aluguel copiaAntiga = aluguel(AGOSTO); // lida antes do pagamento: a tela ainda acha que está em aberto
        recebimento.receber(agosto, HOJE, FormaPagamento.DINHEIRO, "");

        // o banco recusa o segundo recebimento e a transação desfaz tudo: nem recibo nem lançamento a mais
        assertThrows(java.sql.SQLException.class,
                () -> recebimento.receber(copiaAntiga, HOJE, FormaPagamento.DINHEIRO, ""));
        assertEquals(1, todos(Conta.CAIXA).size());
    }
}
