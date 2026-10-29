package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.util.TextoBusca;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lista de aluguéis com situação e filtros (BOARD-38).
 *
 * A situação de cada aluguel é calculada pela data de hoje e pelo pagamento
 * (Aluguel.getSituacao): a vencer até o dia do vencimento; em tolerância até 5 dias
 * depois; em atraso a partir do 6º dia; paga quando o pagamento foi registrado.
 *
 * A geração dos aluguéis do mês é a BOARD-37 e o recebimento é a BOARD-39.
 */
public class ServicoAlugueis {

    /** Quantos aluguéis e quanto somam (sem encargos) numa situação. */
    public record Total(int quantidade, BigDecimal valor) {
    }

    private final AluguelRepository alugueis = new AluguelRepository();

    /** Relógio do sistema; os testes trocam por um relógio parado para testar cada situação. */
    private Clock relogio = Clock.systemDefaultZone();

    /** Só para os testes: faz o serviço usar outro relógio. */
    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /** A data de hoje, que define a situação de cada aluguel. */
    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /** Os aluguéis que vencem no mês informado. */
    public List<Aluguel> listarDoMes(YearMonth mes) throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        if (mes == null) {
            throw new RegraNegocioException("Escolha o mês.");
        }
        return alugueis.listarDoMes(mes);
    }

    /**
     * Todos os aluguéis ainda não pagos, de qualquer mês, do vencimento mais antigo para o mais recente.
     * É o que a tela mostra quando vem do Painel (ex.: "28 aluguéis em atraso" somam vários meses).
     */
    public List<Aluguel> listarEmAberto() throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        return alugueis.listarEmAberto();
    }

    /**
     * Os meses para o filtro:os que já têm aluguéis, do mais recente para o mais antigo,
     * e sempre o mês atual (mesmo antes de os aluguéis dele serem gerados).
     */
    public List<YearMonth> mesesDisponiveis() throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        List<YearMonth> meses = new ArrayList<>(alugueis.mesesComAluguel());
        YearMonth atual = YearMonth.from(hoje());
        if (!meses.contains(atual)) {
            meses.add(atual);
        }
        meses.sort((a, b) -> b.compareTo(a));
        return meses;
    }

    /**
     * Filtra a lista da tela pela situação (nula = todas) e por uma busca no código do imóvel,
     * na unidade, no nome do inquilino ou no número do contrato. A busca ignora maiúsculas e acentos.
     */
    public static List<Aluguel> filtrar(List<Aluguel> lista, SituacaoAluguel situacao, String busca, LocalDate hoje) {
        String termo = TextoBusca.normalizar(busca);
        return lista.stream()
                .filter(a -> situacao == null || a.getSituacao(hoje) == situacao)
                .filter(a -> termo.isEmpty() || atendeBusca(a, termo))
                .toList();
    }

    private static boolean atendeBusca(Aluguel a, String termo) {
        return TextoBusca.normalizar(a.getContrato().getImovel().getCodigo()).contains(termo)
                || TextoBusca.normalizar(a.getContrato().getImovel().getUnidade()).contains(termo)
                || TextoBusca.normalizar(a.getContrato().getInquilino().getNome()).contains(termo)
                || TextoBusca.normalizar(a.getContrato().getNumero()).contains(termo);
    }

    /**
     * Uma linha da lista de cobrança: um inquilino (contrato) e todos os aluguéis dele em atraso,
     * do mais antigo para o mais recente.
     */
    public record Cobranca(Contrato contrato, List<Aluguel> alugueis) {

        /** O aluguel em atraso mais antigo: é ele que diz há quanto tempo o inquilino não paga. */
        public Aluguel maisAntigo() {
            return alugueis.get(0);
        }

        /** Soma dos aluguéis em atraso, sem multa e juros. */
        public BigDecimal base() {
            return alugueis.stream().map(Aluguel::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Quanto o inquilino deve se pagar hoje: os aluguéis em atraso com multa, juros e correção. */
        public BigDecimal devidoHoje(LocalDate hoje) {
            return alugueis.stream().map(a -> a.base().add(a.encargosEm(hoje).total()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** O fiador, para avisar da dívida; null quando a garantia é caução. */
        public Fiador fiador() {
            return contrato.getGarantia() instanceof Fianca f ? f.getFiador() : null;
        }

        /** A caução que garante o contrato; null quando a garantia é fiador. */
        public Caucao caucao() {
            return contrato.getGarantia() instanceof Caucao c ? c : null;
        }
    }

    /**
     * Os aluguéis em atraso dos inquilinos com 2 ou mais aluguéis atrasados (no mesmo contrato):
     * é quem precisa ir para cobrança. O Painel conta esses inquilinos e a tela de Aluguéis
     * mostra a lista de cobrança deles, pela mesma regra.
     */
    public static List<Aluguel> deInquilinosComDoisOuMaisAtrasados(List<Aluguel> lista, LocalDate hoje) {
        List<Aluguel> emAtraso = lista.stream().filter(a -> a.getSituacao(hoje) == SituacaoAluguel.EM_ATRASO).toList();
        Map<Long, Long> porContrato = emAtraso.stream()
                .collect(Collectors.groupingBy(a -> a.getContrato().getId(), Collectors.counting()));
        return emAtraso.stream().filter(a -> porContrato.get(a.getContrato().getId()) > 1).toList();
    }

    /**
     * A lista de cobrança: uma linha por inquilino com 2 ou mais aluguéis atrasados. Primeiro quem
     * deve mais aluguéis; entre os que devem o mesmo número, quem está há mais tempo sem pagar.
     */
    public static List<Cobranca> listaDeCobranca(List<Aluguel> lista, LocalDate hoje) {
        Map<Long, List<Aluguel>> porContrato = deInquilinosComDoisOuMaisAtrasados(lista, hoje).stream()
                .collect(Collectors.groupingBy(a -> a.getContrato().getId()));
        return porContrato.values().stream()
                .map(doContrato -> new Cobranca(doContrato.get(0).getContrato(), doContrato.stream()
                        .sorted(Comparator.comparing(Aluguel::getVencimento)).toList()))
                .sorted(Comparator.comparing((Cobranca c) -> c.alugueis().size()).reversed()
                        .thenComparing(c -> c.maisAntigo().getVencimento())
                        .thenComparing(c -> c.contrato().getImovel().getCodigo()))
                .toList();
    }

    /** Quantos inquilinos da lista de cobrança devem 2, 3, e 4 ou mais aluguéis. */
    public record Faixas(int dois, int tres, int quatroOuMais) {
    }

    public static Faixas faixas(List<Cobranca> lista) {
        int dois = (int) lista.stream().filter(c -> c.alugueis().size() == 2).count();
        int tres = (int) lista.stream().filter(c -> c.alugueis().size() == 3).count();
        return new Faixas(dois, tres, lista.size() - dois - tres);
    }

    /** Quantos aluguéis há em cada situação e quanto somam, para o resumo no topo da tela. */
    public static Map<SituacaoAluguel, Total> totais(List<Aluguel> lista, LocalDate hoje) {
        Map<SituacaoAluguel, Total> totais = new EnumMap<>(SituacaoAluguel.class);
        for (SituacaoAluguel s : SituacaoAluguel.values()) {
            totais.put(s, new Total(0, BigDecimal.ZERO));
        }
        for (Aluguel a : lista) {
            SituacaoAluguel s = a.getSituacao(hoje);
            Total t = totais.get(s);
            totais.put(s, new Total(t.quantidade() + 1, t.valor().add(a.base())));
        }
        return totais;
    }
}
