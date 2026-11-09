package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.SituacaoContato;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContatoCobrancaRepository;
import br.org.fmc.repository.ContratoRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Lista de cobrança com o acompanhamento dos contatos ([D7] BOARD-64): quem precisa de ligação,
 * quem prometeu pagar e quando, e quem não cumpriu a promessa.
 */
public class ServicoCobranca {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Em que pé está a cobrança: a situação (para ordenar e colorir) e duas linhas de texto
     * para a tela, ex.: "Prometeu para 15/11" / "Faltam 3 dias".
     */
    public record Acompanhamento(SituacaoContato situacao, String texto, String detalhe) {
    }

    public record Linha(Contrato contrato, List<Aluguel> alugueis, BigDecimal valorOriginal,
            BigDecimal valorAtualizado, ContatoCobranca ultimoContato, Acompanhamento acompanhamento) {
        public int mesesEmAberto() {
            return alugueis.size();
        }

        public Aluguel aluguelMaisAntigo() {
            return alugueis.get(0);
        }

        public Fiador fiador() {
            return contrato.getGarantia() instanceof Fianca fianca ? fianca.getFiador() : null;
        }

        public SituacaoContato situacao() {
            return acompanhamento.situacao();
        }
    }

    private final AluguelRepository alugueis = new AluguelRepository();
    private final ContratoRepository contratos = new ContratoRepository();
    private final ContatoCobrancaRepository contatos = new ContatoCobrancaRepository();
    private Clock relogio = Clock.systemDefaultZone();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /**
     * Mais de um mês em aberto: dois ou mais aluguéis EM ATRASO (passada a tolerância) no mesmo
     * contrato. Quem entra vem do ServicoAlugueis.listaDeCobranca, a mesma regra do Painel
     * (BOARD-61), para os números da aba e do Painel sempre baterem. A lista sai em ordem de
     * urgência: promessas não cumpridas, nunca contatados, sem retorno (o contato mais antigo
     * primeiro) e, por último, as promessas ainda no prazo.
     */
    public List<Linha> listarParaCobranca() throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        return montarLista(ServicoAlugueis.listaDeCobranca(alugueis.listarEmAberto(), hoje()),
                contatos.ultimosPorContrato(), hoje());
    }

    /** Junta o último contato e o acompanhamento a cada linha da lista de cobrança; também usado pelo Painel. */
    static List<Linha> montarLista(List<ServicoAlugueis.Cobranca> cobrancas, Map<Long, ContatoCobranca> ultimos,
            LocalDate hoje) {
        List<Linha> linhas = new ArrayList<>();
        for (ServicoAlugueis.Cobranca c : cobrancas) {
            ContatoCobranca ultimo = ultimos.get(c.contrato().getId());
            linhas.add(new Linha(c.contrato(), c.alugueis(), c.base(), c.devidoHoje(hoje), ultimo,
                    acompanhamento(ultimo, hoje)));
        }
        // sort é estável: dentro da mesma situação, fica a ordem da lista de cobrança (mais meses primeiro).
        linhas.sort(Comparator.comparing(Linha::situacao)
                .thenComparing(l -> l.situacao() == SituacaoContato.SEM_RETORNO
                        ? l.ultimoContato().registradoEm() : LocalDateTime.MIN));
        return List.copyOf(linhas);
    }

    /** Em que pé está a cobrança, pelo último contato registrado (nulo = nunca contatado). */
    public static Acompanhamento acompanhamento(ContatoCobranca ultimo, LocalDate hoje) {
        if (ultimo == null) {
            return new Acompanhamento(SituacaoContato.NUNCA_CONTATADO, "Nunca contatado", "Ligar");
        }
        if (ultimo.ehPromessa()) {
            LocalDate data = ultimo.dataPrometida();
            long dias = ChronoUnit.DAYS.between(hoje, data);
            String prometeu = "Prometeu para " + data.format(DIA_MES);
            if (dias < 0) {
                return new Acompanhamento(SituacaoContato.PROMESSA_NAO_CUMPRIDA, "Promessa vencida",
                        "Era " + data.format(DIA_MES) + " · " + ha(-dias));
            }
            SituacaoContato situacao = dias <= RegrasNegocio.DIAS_AVISO_PROMESSA
                    ? SituacaoContato.PROMESSA_VENCENDO : SituacaoContato.PROMESSA_NO_PRAZO;
            return new Acompanhamento(situacao, prometeu, faltam(dias));
        }
        String texto = ultimo.tipo() == null ? "Contato registrado" : ultimo.tipo().getRotulo();
        long dias = ChronoUnit.DAYS.between(ultimo.registradoEm().toLocalDate(), hoje);
        return new Acompanhamento(SituacaoContato.SEM_RETORNO, texto,
                (dias <= 0 ? "Hoje" : capitalizar(ha(dias))) + " · " + ultimo.feitoPor().getNome());
    }

    /** "Vence hoje", "Falta 1 dia", "Faltam 3 dias". */
    public static String faltam(long dias) {
        if (dias <= 0) {
            return "Vence hoje";
        }
        return dias == 1 ? "Falta 1 dia" : "Faltam " + dias + " dias";
    }

    private static String ha(long dias) {
        return dias == 1 ? "há 1 dia" : "há " + dias + " dias";
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Registra o que deu o contato. Quem prometeu pagar precisa de uma data entre hoje e
     * 90 dias à frente; a observação é opcional (ex.: "vai pagar quando receber o 13º").
     */
    public ContatoCobranca registrarContato(long contratoId, ResultadoContato tipo, LocalDate dataPrometida,
            String observacao) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_CONTATO_COBRANCA);
        List<String> erros = new ArrayList<>();
        LocalDate hoje = hoje();
        if (tipo == null) {
            erros.add("Escolha o que deu o contato.");
        } else if (tipo == ResultadoContato.PROMETEU_PAGAR) {
            if (dataPrometida == null) {
                erros.add("Informe a data que o inquilino prometeu pagar.");
            } else if (dataPrometida.isBefore(hoje)) {
                erros.add("A data prometida não pode ser antes de hoje.");
            } else if (dataPrometida.isAfter(hoje.plusDays(RegrasNegocio.DIAS_MAXIMO_PROMESSA))) {
                erros.add("A data prometida pode ser de no máximo " + RegrasNegocio.DIAS_MAXIMO_PROMESSA
                        + " dias a partir de hoje.");
            }
        }
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }
        Contrato contrato = contratos.buscarPorId(contratoId)
                .orElseThrow(() -> new RegraNegocioException("Contrato não encontrado."));
        return contatos.salvar(new ContatoCobranca(null, contrato.getId(), LocalDateTime.now(relogio), tipo,
                dataPrometida, observacao, Sessao.usuario()));
    }

    /** Todos os contatos do contrato, do mais recente para o mais antigo. */
    public List<ContatoCobranca> historico(long contratoId) throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        return contatos.listarDoContrato(contratoId);
    }
}
