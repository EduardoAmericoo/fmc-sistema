package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Recibo;
import br.org.fmc.repository.ReciboRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;

/** Regras para receber um aluguel e emitir seu recibo. */
public class ServicoRecebimento {

    private final ReciboRepository recibos = new ReciboRepository();
    private Clock relogio = Clock.systemDefaultZone();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    /**
     * Multa, juros e correção se o aluguel for pago na data informada. Dispensar encargos é a
     * [D11] BOARD-66 (com motivo e registro de quem dispensou): ainda não existe.
     */
    public Encargos calcularEncargos(Aluguel aluguel, LocalDate dataPagamento) {
        if (aluguel == null || dataPagamento == null) {
            throw new RegraNegocioException("Informe o aluguel e a data do pagamento.");
        }
        return aluguel.encargosEm(dataPagamento);
    }

    public Recibo receber(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            String documento) throws SQLException {
        Sessao.exigir(Acao.RECEBER_ALUGUEL);
        if (aluguel == null) {
            throw new RegraNegocioException("Escolha um aluguel.");
        }
        if (aluguel.estaPago()) {
            throw new RegraNegocioException("Este aluguel já foi recebido.");
        }
        if (dataPagamento == null) {
            throw new RegraNegocioException("Informe a data do pagamento.");
        }
        if (dataPagamento.isAfter(hoje())) {
            throw new RegraNegocioException("A data do pagamento não pode estar no futuro.");
        }
        if (formaPagamento == null) {
            throw new RegraNegocioException("Escolha a forma de pagamento.");
        }
        Encargos encargos = calcularEncargos(aluguel, dataPagamento);
        return recibos.registrar(aluguel, dataPagamento, formaPagamento, encargos, documento, Sessao.usuario());
    }
}
