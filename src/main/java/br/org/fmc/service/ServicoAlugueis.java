package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.util.TextoBusca;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
