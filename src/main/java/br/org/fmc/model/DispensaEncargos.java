package br.org.fmc.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [D11] BOARD-66 · Multa, juros e correção que deixaram de ser cobrados num recebimento,
 * com o motivo, quem autorizou e quando. Fica guardada junto do recibo para auditoria.
 *
 * Quem pode autorizar é decidido em ServicoRecebimento.podeDispensarEncargos().
 */
public final class DispensaEncargos {

    private final BigDecimal multa;
    private final BigDecimal juros;
    private final BigDecimal correcao;
    private final String motivo;
    private final Usuario autorizadoPor;
    private final LocalDateTime autorizadoEm;

    public DispensaEncargos(BigDecimal multa, BigDecimal juros, BigDecimal correcao, String motivo,
            Usuario autorizadoPor, LocalDateTime autorizadoEm) {
        this.multa = Valida.naoNegativo(Valida.obrigatorio(multa, "a multa dispensada"), "a multa dispensada");
        this.juros = Valida.naoNegativo(Valida.obrigatorio(juros, "os juros dispensados"), "os juros dispensados");
        this.correcao = Valida.naoNegativo(Valida.obrigatorio(correcao, "a correção dispensada"),
                "a correção dispensada");
        this.motivo = Valida.textoObrigatorio(motivo, "o motivo da dispensa");
        this.autorizadoPor = Valida.obrigatorio(autorizadoPor, "quem autorizou a dispensa");
        this.autorizadoEm = Valida.obrigatorio(autorizadoEm, "a data da dispensa");
    }

    /** Dispensa todos os encargos calculados pelo contrato. */
    public static DispensaEncargos de(Encargos calculados, String motivo, Usuario autorizadoPor,
            LocalDateTime autorizadoEm) {
        return new DispensaEncargos(calculados.getMulta(), calculados.getJuros(), calculados.getCorrecao(),
                motivo, autorizadoPor, autorizadoEm);
    }

    /** Soma do que foi dispensado. */
    public BigDecimal total() {
        return multa.add(juros).add(correcao);
    }

    public BigDecimal getMulta() {
        return multa;
    }

    public BigDecimal getJuros() {
        return juros;
    }

    public BigDecimal getCorrecao() {
        return correcao;
    }

    public String getMotivo() {
        return motivo;
    }

    public Usuario getAutorizadoPor() {
        return autorizadoPor;
    }

    public LocalDateTime getAutorizadoEm() {
        return autorizadoEm;
    }
}
