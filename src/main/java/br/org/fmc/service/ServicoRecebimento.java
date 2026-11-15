package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.DispensaEncargos;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ReciboRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
     * [D11] BOARD-66 · Quem pode dispensar multa, juros e correção de um recebimento.
     * É o ÚNICO lugar com esta regra: a tela e o receber() perguntam aqui.
     *
     * TODO BOARD-66: regra provisória, depende de confirmação do cliente. Por enquanto só o
     * perfil Administrador pode dispensar. Se o cliente quiser ajustar pessoa a pessoa, crie uma
     * Acao (ex.: DISPENSAR_ENCARGOS) e troque o corpo por "usuario.pode(Acao.DISPENSAR_ENCARGOS)".
     * Lembre de ajustar também SEM_PERMISSAO_PARA_DISPENSAR.
     */
    public static boolean podeDispensarEncargos(Usuario usuario) {
        return usuario != null && usuario.isAtivo() && usuario.getPerfil() == Perfil.ADMINISTRADOR;
    }

    /** Explica, na tela e no erro, por que a pessoa não pode dispensar. Acompanha a regra acima. */
    public static final String SEM_PERMISSAO_PARA_DISPENSAR =
            "Só o Administrador pode dispensar multa, juros e correção.";

    /** O usuário logado pode dispensar encargos? (false se ninguém estiver logado) */
    public boolean usuarioPodeDispensarEncargos() {
        return Sessao.estaLogado() && podeDispensarEncargos(Sessao.usuario());
    }

    /** Total a receber na data informada: o valor devido mais os encargos, a menos que sejam dispensados. */
    public BigDecimal totalAReceber(Aluguel aluguel, LocalDate dataPagamento, boolean dispensarEncargos) {
        Encargos encargos = calcularEncargos(aluguel, dataPagamento);
        return dispensarEncargos ? aluguel.base() : aluguel.base().add(encargos.total());
    }

    /** Multa, juros e correção se o aluguel for pago na data informada (antes de qualquer dispensa). */
    public Encargos calcularEncargos(Aluguel aluguel, LocalDate dataPagamento) {
        if (aluguel == null || dataPagamento == null) {
            throw new RegraNegocioException("Informe o aluguel e a data do pagamento.");
        }
        return aluguel.encargosEm(dataPagamento);
    }

    /** O recibo de um aluguel já recebido, para ver de novo, imprimir ou salvar em PDF (BOARD-60). */
    public java.util.Optional<Recibo> reciboDoAluguel(Aluguel aluguel) throws SQLException {
        Sessao.exigir(Acao.VER_ALUGUEIS);
        if (aluguel == null || aluguel.getId() == null) {
            return java.util.Optional.empty();
        }
        return recibos.buscarDoAluguel(aluguel);
    }

    /** Recebe o aluguel cobrando todos os encargos do contrato. */
    public Recibo receber(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            String documento) throws SQLException {
        return receber(aluguel, dataPagamento, formaPagamento, documento, false, null);
    }

    /**
     * Recebe o aluguel e emite o recibo. Com dispensarEncargos, multa, juros e correção não são
     * cobrados (BOARD-66): fica gravado o que foi dispensado, o motivo, quem autorizou e quando.
     */
    public Recibo receber(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            String documento, boolean dispensarEncargos, String motivoDispensa) throws SQLException {
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
        if (!dispensarEncargos) {
            return recibos.registrar(aluguel, dataPagamento, formaPagamento, encargos, documento, Sessao.usuario());
        }
        if (!podeDispensarEncargos(Sessao.usuario())) {
            throw new AcessoNegadoException(SEM_PERMISSAO_PARA_DISPENSAR);
        }
        if (!encargos.temEncargos()) {
            throw new RegraNegocioException("Este pagamento não tem multa, juros ou correção para dispensar.");
        }
        if (motivoDispensa == null || motivoDispensa.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da dispensa de multa, juros e correção.");
        }
        DispensaEncargos dispensa = DispensaEncargos.de(encargos, motivoDispensa, Sessao.usuario(),
                LocalDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS));
        return recibos.registrar(aluguel, dataPagamento, formaPagamento, encargos.dispensados(), documento,
                Sessao.usuario(), dispensa);
    }
}
