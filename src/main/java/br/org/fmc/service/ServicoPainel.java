package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.model.Tela;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * O Painel, primeira tela depois do login: os números do mês (BOARD-40) e o que precisa
 * de atenção, com os aluguéis em atraso do mais antigo para o mais recente (BOARD-61).
 *
 * O Painel abre para qualquer pessoa logada, mas cada parte só aparece para quem pode ver
 * aquele assunto: números e aluguéis com VER_ALUGUEIS, contratos com VER_CONTRATOS e
 * imóveis com VER_IMOVEIS.
 *
 * Os valores são o devido sem encargos (aluguel + luz + IPTU + outros). No atraso, mostra
 * também quanto fica com multa, juros e correção se for pago hoje.
 */
public class ServicoPainel {

    /** Quantos e quanto somam. */
    public record Valor(int quantidade, BigDecimal total) {
        static final Valor ZERO = new Valor(0, BigDecimal.ZERO);

        static Valor de(List<Aluguel> alugueis) {
            return new Valor(alugueis.size(), alugueis.stream().map(Aluguel::base).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    /**
     * Os números do mês atual (BOARD-40).
     *
     * @param previsto           todos os aluguéis que vencem no mês
     * @param recebido           os do mês que já foram pagos
     * @param aReceber           os do mês ainda no prazo ou na tolerância
     * @param emAtraso           todos os aluguéis em atraso, de qualquer mês
     * @param atrasoComEncargos  o atraso com multa, juros e correção, se for pago hoje
     */
    public record NumerosDoMes(YearMonth mes, Valor previsto, Valor recebido, Valor aReceber, Valor emAtraso,
                               BigDecimal atrasoComEncargos) {

        /** Quanto do previsto já foi recebido, de 0 a 100. */
        public int percentualRecebido() {
            if (previsto.total().signum() == 0) {
                return 0;
            }
            return recebido.total().multiply(BigDecimal.valueOf(100))
                    .divide(previsto.total(), 0, RoundingMode.DOWN).intValue();
        }
    }

    /** Quantos imóveis há (sem os arquivados) e como estão. */
    public record Ocupacao(int imoveis, int ocupados, int vagos, int emManutencao) {
    }

    /** O tom do aviso: vermelho, laranja ou azul. */
    public enum Gravidade { ALTA, MEDIA, INFO }

    /** Um item de "Precisa de atenção", com a tela que resolve. */
    public record Pendencia(Gravidade gravidade, int quantidade, String titulo, String detalhe, Tela destino) {
    }

    /**
     * Tudo o que o Painel mostra. Partes que a pessoa não pode ver vêm nulas (números, ocupação)
     * ou vazias (listas).
     */
    public record Painel(LocalDate hoje, NumerosDoMes numeros, Ocupacao ocupacao, List<Pendencia> pendencias,
                         List<Aluguel> alugueisEmAtraso) {
    }

    private final AluguelRepository alugueis = new AluguelRepository();
    private final ContratoRepository contratos = new ContratoRepository();
    private final ImovelRepository imoveis = new ImovelRepository();
    private Clock relogio = Clock.systemDefaultZone();

    /** Para os testes: fixa o "hoje" num dia conhecido. */
    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /** Monta o Painel de hoje para quem está logado. */
    public Painel montar() throws SQLException {
        if (!Sessao.estaLogado()) {
            throw new AcessoNegadoException("Entre no sistema para ver o Painel.");
        }
        LocalDate hoje = LocalDate.now(relogio);
        List<Pendencia> pendencias = new ArrayList<>();
        NumerosDoMes numeros = null;
        List<Aluguel> emAtraso = List.of();
        Ocupacao ocupacao = null;

        if (Sessao.pode(Acao.VER_ALUGUEIS)) {
            List<Aluguel> emAberto = alugueis.listarEmAberto();
            emAtraso = emAtraso(emAberto, hoje);
            numeros = numerosDoMes(hoje, emAtraso);
            pendenciasDeAlugueis(emAberto, emAtraso, hoje, pendencias);
        }
        if (Sessao.pode(Acao.VER_CONTRATOS)) {
            pendenciasDeContratos(contratos.listarTodos(), hoje, pendencias);
        }
        if (Sessao.pode(Acao.VER_IMOVEIS)) {
            ocupacao = ocupacao(imoveis.listarTodos());
            if (ocupacao.vagos() > 0) {
                pendencias.add(new Pendencia(Gravidade.INFO, ocupacao.vagos(),
                        plural(ocupacao.vagos(), "Imóvel vago", "Imóveis vagos"),
                        "Prontos para receber um novo contrato.", Tela.IMOVEIS));
            }
        }
        return new Painel(hoje, numeros, ocupacao, List.copyOf(pendencias), emAtraso);
    }

    /**
     * Os aluguéis em atraso (passaram dos 5 dias de tolerância), do vencimento mais antigo
     * para o mais recente; no mesmo dia, pelo código do imóvel (BOARD-61).
     */
    static List<Aluguel> emAtraso(List<Aluguel> emAberto, LocalDate hoje) {
        return emAberto.stream()
                .filter(a -> a.getSituacao(hoje) == SituacaoAluguel.EM_ATRASO)
                .sorted(Comparator.comparing(Aluguel::getVencimento)
                        .thenComparing(a -> a.getContrato().getImovel().getCodigo()))
                .toList();
    }

    private NumerosDoMes numerosDoMes(LocalDate hoje, List<Aluguel> emAtraso) throws SQLException {
        YearMonth mes = YearMonth.from(hoje);
        List<Aluguel> doMes = alugueis.listarDoMes(mes);
        List<Aluguel> pagos = filtrar(doMes, Aluguel::estaPago);
        List<Aluguel> aReceber = filtrar(doMes, a -> a.getSituacao(hoje) == SituacaoAluguel.A_VENCER
                || a.getSituacao(hoje) == SituacaoAluguel.EM_TOLERANCIA);
        BigDecimal comEncargos = emAtraso.stream()
                .map(a -> a.base().add(a.encargosEm(hoje).total()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new NumerosDoMes(mes, Valor.de(doMes), Valor.de(pagos), Valor.de(aReceber), Valor.de(emAtraso),
                comEncargos);
    }

    private static void pendenciasDeAlugueis(List<Aluguel> emAberto, List<Aluguel> emAtraso, LocalDate hoje,
                                             List<Pendencia> pendencias) {
        if (!emAtraso.isEmpty()) {
            pendencias.add(new Pendencia(Gravidade.ALTA, emAtraso.size(),
                    plural(emAtraso.size(), "Aluguel em atraso", "Aluguéis em atraso"),
                    "Passaram dos " + RegrasNegocio.TOLERANCIA_DIAS + " dias de tolerância. Somam "
                            + Formatos.moeda(Valor.de(emAtraso).total()) + " sem encargos.", Tela.ALUGUEIS));
        }
        // Mais de um mês em atraso no mesmo contrato: hora de cobrar (e avisar o fiador).
        Map<Long, Long> atrasosPorContrato = emAtraso.stream()
                .collect(Collectors.groupingBy(a -> a.getContrato().getId(), Collectors.counting()));
        int paraCobranca = (int) atrasosPorContrato.values().stream().filter(n -> n > 1).count();
        if (paraCobranca > 0) {
            pendencias.add(new Pendencia(Gravidade.ALTA, paraCobranca,
                    plural(paraCobranca, "Inquilino com mais de 1 mês em atraso",
                            "Inquilinos com mais de 1 mês em atraso"),
                    "Encaminhar para cobrança e avisar o fiador.", Tela.ALUGUEIS));
        }
        List<Aluguel> tolerancia = filtrar(emAberto, a -> a.getSituacao(hoje) == SituacaoAluguel.EM_TOLERANCIA);
        if (!tolerancia.isEmpty()) {
            pendencias.add(new Pendencia(Gravidade.MEDIA, tolerancia.size(),
                    plural(tolerancia.size(), "Aluguel em tolerância", "Aluguéis em tolerância"),
                    "Venceram há até " + RegrasNegocio.TOLERANCIA_DIAS + " dias. Ainda dá para pagar sem multa.",
                    Tela.ALUGUEIS));
        }
    }

    /** Decisão do PO (27/10): contrato vencido não gera aluguel, então o Painel avisa para prorrogar ou encerrar. */
    private static void pendenciasDeContratos(List<Contrato> todos, LocalDate hoje, List<Pendencia> pendencias) {
        List<Contrato> ativos = todos.stream().filter(c -> c.getStatus() == StatusContrato.ATIVO).toList();
        long vencidos = ativos.stream().filter(c -> c.getDataTermino().isBefore(hoje)).count();
        LocalDate limite = hoje.plusDays(RegrasNegocio.DIAS_AVISO_VENCIMENTO);
        long vencendo = ativos.stream()
                .filter(c -> !c.getDataTermino().isBefore(hoje) && !c.getDataTermino().isAfter(limite)).count();
        if (vencidos > 0) {
            pendencias.add(new Pendencia(Gravidade.ALTA, (int) vencidos,
                    plural(vencidos, "Contrato vencido", "Contratos vencidos"),
                    "Não geram aluguel. Prorrogue ou encerre cada um.", Tela.CONTRATOS));
        }
        if (vencendo > 0) {
            pendencias.add(new Pendencia(Gravidade.MEDIA, (int) vencendo,
                    plural(vencendo, "Contrato vence", "Contratos vencem") + " nos próximos "
                            + RegrasNegocio.DIAS_AVISO_VENCIMENTO + " dias",
                    "Combine com o inquilino se vai renovar ou sair.", Tela.CONTRATOS));
        }
    }

    private static Ocupacao ocupacao(List<Imovel> todos) {
        List<Imovel> ativos = filtrar(todos, i -> !i.estaArquivado());
        return new Ocupacao(ativos.size(),
                filtrar(ativos, i -> i.getSituacao() == SituacaoImovel.OCUPADO).size(),
                filtrar(ativos, Imovel::estaDisponivel).size(),
                filtrar(ativos, i -> i.getSituacao() == SituacaoImovel.EM_MANUTENCAO).size());
    }

    private static <T> List<T> filtrar(List<T> lista, Predicate<T> condicao) {
        return lista.stream().filter(condicao).toList();
    }

    private static String plural(long quantidade, String singular, String plural) {
        return quantidade == 1 ? singular : plural;
    }
}
