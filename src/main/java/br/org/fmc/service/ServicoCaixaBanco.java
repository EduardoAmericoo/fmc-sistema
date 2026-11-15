package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.repository.LancamentoRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * [D5] BOARD-63 · Caixa e banco: quanto há hoje em cada um e o movimento do mês (saldo
 * anterior, entradas, saídas e saldo final), a partir dos lançamentos que os recibos geram
 * sozinhos (D4).
 *
 * O saldo inicial é informado UMA vez pelo Administrador, para o Caixa e para o Banco: é
 * quanto havia em cada um naquele dia. Daí em diante o sistema soma sozinho. Os lançamentos
 * de antes dessa data não contam, porque já estão dentro do saldo informado.
 */
public class ServicoCaixaBanco {

    private static final LocalDate INICIO = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM = LocalDate.of(2999, 12, 31);

    /** Quanto há no Caixa e no Banco num dia (ao fim do dia). */
    public record Disponibilidades(LocalDate dia, BigDecimal caixa, BigDecimal banco) {
        public BigDecimal total() {
            return caixa.add(banco);
        }
    }

    /** Uma linha do movimento: o lançamento e o saldo da conta logo depois dele. */
    public record Linha(Lancamento lancamento, BigDecimal saldoDepois) {
    }

    /** O movimento de uma conta num mês. */
    public record Movimento(Conta conta, YearMonth mes, BigDecimal saldoAnterior, BigDecimal entradas,
            BigDecimal saidas, List<Linha> linhas) {
        public BigDecimal saldoFinal() {
            return saldoAnterior.add(entradas).subtract(saidas);
        }
    }

    private final LancamentoRepository lancamentos = new LancamentoRepository();
    private Clock relogio = Clock.systemDefaultZone();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /** Quanto há hoje no Caixa e no Banco. */
    public Disponibilidades disponibilidades() throws SQLException {
        Sessao.exigir(Acao.VER_CAIXA_BANCO);
        LocalDate hoje = hoje();
        return new Disponibilidades(hoje, saldoAoFimDe(Conta.CAIXA, hoje), saldoAoFimDe(Conta.BANCO, hoje));
    }

    /** Saldo anterior, entradas, saídas, saldo final e as linhas do mês, em ordem de data. */
    public Movimento movimento(Conta conta, YearMonth mes) throws SQLException {
        Sessao.exigir(Acao.VER_CAIXA_BANCO);
        LocalDate primeiro = mes.atDay(1);
        LocalDate ultimo = mes.atEndOfMonth();
        BigDecimal anterior = BigDecimal.ZERO;
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        List<Lancamento> doMes = new ArrayList<>();
        for (Lancamento l : considerados(conta)) {
            boolean saldoInicialDoMes = l.tipo() == TipoLancamento.SALDO_INICIAL && !l.data().isAfter(ultimo);
            if (l.data().isBefore(primeiro) || saldoInicialDoMes) {
                anterior = anterior.add(l.valor()); // o saldo inicial vale como o ponto de partida do mês
            } else if (!l.data().isAfter(ultimo)) {
                doMes.add(l);
                if (l.entrada()) {
                    entradas = entradas.add(l.valor());
                } else {
                    saidas = saidas.add(l.valor().negate());
                }
            }
        }
        List<Linha> linhas = new ArrayList<>();
        BigDecimal saldo = anterior;
        for (Lancamento l : doMes) {
            saldo = saldo.add(l.valor());
            linhas.add(new Linha(l, saldo));
        }
        return new Movimento(conta, mes, anterior, entradas, saidas, List.copyOf(linhas));
    }

    /** O saldo inicial já informado da conta, se houver. */
    public Optional<Lancamento> saldoInicial(Conta conta) throws SQLException {
        Sessao.exigir(Acao.VER_CAIXA_BANCO);
        return lancamentos.saldoInicial(conta);
    }

    /** As contas que ainda não têm saldo inicial (para a tela avisar). */
    public List<Conta> semSaldoInicial() throws SQLException {
        List<Conta> faltam = new ArrayList<>();
        for (Conta conta : Conta.values()) {
            if (saldoInicial(conta).isEmpty()) {
                faltam.add(conta);
            }
        }
        return faltam;
    }

    /**
     * Informa quanto havia no Caixa e no Banco na data. Cada conta só recebe saldo inicial uma
     * vez. Valor nulo deixa a conta para depois (ex.: informar só o Caixa agora).
     */
    public void informarSaldoInicial(LocalDate data, BigDecimal caixa, BigDecimal banco) throws SQLException {
        Sessao.exigir(Acao.INFORMAR_SALDO_INICIAL);
        List<String> erros = new ArrayList<>();
        if (data == null) {
            erros.add("Informe a data do saldo.");
        } else if (data.isAfter(hoje())) {
            erros.add("A data do saldo não pode ser no futuro.");
        }
        if (caixa == null && banco == null) {
            erros.add("Informe o saldo do Caixa, do Banco ou dos dois.");
        }
        conferirValor(Conta.CAIXA, caixa, erros);
        conferirValor(Conta.BANCO, banco, erros);
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        if (caixa != null) {
            lancamentos.salvar(saldoInicialDe(Conta.CAIXA, data, caixa, agora));
        }
        if (banco != null) {
            lancamentos.salvar(saldoInicialDe(Conta.BANCO, data, banco, agora));
        }
    }

    /** Os meses que o seletor oferece: do primeiro lançamento (ou do saldo inicial) até o mês atual. */
    public List<YearMonth> meses() throws SQLException {
        Sessao.exigir(Acao.VER_CAIXA_BANCO);
        YearMonth atual = YearMonth.from(hoje());
        YearMonth primeiro = atual;
        for (Conta conta : Conta.values()) {
            for (Lancamento l : considerados(conta)) {
                YearMonth m = YearMonth.from(l.data());
                if (m.isBefore(primeiro)) {
                    primeiro = m;
                }
            }
        }
        List<YearMonth> meses = new ArrayList<>();
        for (YearMonth m = atual; !m.isBefore(primeiro); m = m.minusMonths(1)) {
            meses.add(m); // do mais recente para o mais antigo
        }
        return meses;
    }

    // ---------- regras internas ----------

    private void conferirValor(Conta conta, BigDecimal valor, List<String> erros) throws SQLException {
        if (valor == null) {
            return;
        }
        if (valor.signum() < 0) {
            erros.add("O saldo do " + conta + " não pode ser negativo.");
        }
        Optional<Lancamento> existente = lancamentos.saldoInicial(conta);
        if (existente.isPresent()) {
            erros.add("O saldo inicial do " + conta + " já foi informado: " + Formatos.moeda(existente.get().valor())
                    + " em " + Formatos.data(existente.get().data()) + ".");
        }
    }

    private Lancamento saldoInicialDe(Conta conta, LocalDate data, BigDecimal valor, LocalDateTime agora) {
        return new Lancamento(null, conta, TipoLancamento.SALDO_INICIAL, data, valor,
                "Saldo inicial do " + conta + " informado por " + Sessao.usuario().getNome(), null,
                Sessao.usuario(), agora);
    }

    /** Saldo ao fim do dia: tudo o que conta até a data, inclusive. */
    private BigDecimal saldoAoFimDe(Conta conta, LocalDate dia) throws SQLException {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Lancamento l : considerados(conta)) {
            if (!l.data().isAfter(dia)) {
                saldo = saldo.add(l.valor());
            }
        }
        return saldo;
    }

    /**
     * Os lançamentos que entram na conta: com saldo inicial, os de antes da data dele ficam de
     * fora (o valor informado já os inclui).
     */
    private List<Lancamento> considerados(Conta conta) throws SQLException {
        Optional<Lancamento> inicial = lancamentos.saldoInicial(conta);
        List<Lancamento> todos = lancamentos.listar(conta, INICIO, FIM);
        if (inicial.isEmpty()) {
            return todos;
        }
        LocalDate desde = inicial.get().data();
        return todos.stream()
                .filter(l -> l.tipo() == TipoLancamento.SALDO_INICIAL || !l.data().isBefore(desde))
                .toList();
    }
}
