package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContatoCobrancaRepository;
import br.org.fmc.repository.ContratoRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consolida aluguéis em aberto por contrato e registra contatos de cobrança. */
public class ServicoCobranca {

    public record Linha(Contrato contrato, List<Aluguel> alugueis, BigDecimal valorOriginal,
            BigDecimal valorAtualizado, ContatoCobranca ultimoContato) {
        public int mesesEmAberto() {
            return alugueis.size();
        }

        public Aluguel aluguelMaisAntigo() {
            return alugueis.get(0);
        }

        public Fiador fiador() {
            return contrato.getGarantia() instanceof Fianca fianca ? fianca.getFiador() : null;
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

    /** Mais de um mês em aberto: agrupa dois ou mais aluguéis não recebidos por contrato. */
    public List<Linha> listarParaCobranca() throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        LocalDate hoje = hoje();
        Map<Long, Contrato> contratosPorId = new HashMap<>();
        for (Contrato contrato : contratos.listarTodos()) {
            contratosPorId.put(contrato.getId(), contrato);
        }
        Map<Long, List<Aluguel>> abertos = new HashMap<>();
        for (Aluguel aluguel : alugueis.listarEmAberto()) {
            if (!aluguel.getVencimento().isAfter(hoje)) {
                abertos.computeIfAbsent(aluguel.getContrato().getId(), ignorado -> new ArrayList<>()).add(aluguel);
            }
        }
        Map<Long, ContatoCobranca> ultimos = contatos.ultimosPorContrato();
        List<Linha> linhas = new ArrayList<>();
        for (Map.Entry<Long, List<Aluguel>> item : abertos.entrySet()) {
            List<Aluguel> doContrato = item.getValue();
            if (doContrato.size() < 2) {
                continue;
            }
            doContrato.sort(Comparator.comparing(Aluguel::getVencimento));
            BigDecimal original = doContrato.stream().map(Aluguel::base).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal atualizado = doContrato.stream()
                    .map(a -> a.base().add(a.encargosEm(hoje).total()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Contrato contrato = contratosPorId.get(item.getKey());
            if (contrato != null) {
                linhas.add(new Linha(contrato, List.copyOf(doContrato), original, atualizado,
                        ultimos.get(item.getKey())));
            }
        }
        linhas.sort(Comparator.comparingInt(Linha::mesesEmAberto).reversed()
                .thenComparing(l -> l.aluguelMaisAntigo().getVencimento())
                .thenComparing(l -> l.contrato().getImovel().getCodigo()));
        return List.copyOf(linhas);
    }

    public ContatoCobranca registrarContato(long contratoId, String resultado) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_CONTATO_COBRANCA);
        if (resultado == null || resultado.isBlank()) {
            throw new RegraNegocioException("Descreva o resultado do contato.");
        }
        Contrato contrato = contratos.buscarPorId(contratoId)
                .orElseThrow(() -> new RegraNegocioException("Contrato não encontrado."));
        return contatos.salvar(contrato, resultado, Sessao.usuario(), LocalDateTime.now(relogio));
    }
}
