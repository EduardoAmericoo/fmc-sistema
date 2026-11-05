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

    /**
     * Mais de um mês em aberto: dois ou mais aluguéis EM ATRASO (passada a tolerância) no mesmo
     * contrato. A escolha de quem entra e a ordem vêm do ServicoAlugueis.listaDeCobranca, a mesma
     * regra do Painel (BOARD-61), para os números da aba e do Painel sempre baterem; aqui só se
     * junta o último contato registrado ([D7] BOARD-64).
     */
    public List<Linha> listarParaCobranca() throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        LocalDate hoje = hoje();
        Map<Long, ContatoCobranca> ultimos = contatos.ultimosPorContrato();
        List<Linha> linhas = new ArrayList<>();
        for (ServicoAlugueis.Cobranca c : ServicoAlugueis.listaDeCobranca(alugueis.listarEmAberto(), hoje)) {
            linhas.add(new Linha(c.contrato(), c.alugueis(), c.base(), c.devidoHoje(hoje),
                    ultimos.get(c.contrato().getId())));
        }
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
