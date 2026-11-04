package br.org.fmc.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A situação de um contrato para quem consulta a lista (BOARD-59). Cada contrato cai em
 * exatamente uma delas, olhando o status e a data de término:
 * encerrado; ativo já vencido; ativo vencendo nos próximos 60 dias; ou ativo com prazo folgado.
 */
public enum SituacaoContrato {
    ATIVO("Ativos"),
    VENCENDO("Vencendo em " + RegrasNegocio.DIAS_AVISO_VENCIMENTO + " dias"),
    VENCIDO("Vencidos"),
    ENCERRADO("Encerrados");

    private final String descricao;

    SituacaoContrato(String descricao) {
        this.descricao = descricao;
    }

    /** Em que situação o contrato está na data informada. */
    public static SituacaoContrato de(Contrato contrato, LocalDate hoje) {
        if (contrato.getStatus() == StatusContrato.ENCERRADO) {
            return ENCERRADO;
        }
        long dias = ChronoUnit.DAYS.between(hoje, contrato.getDataTermino());
        if (dias < 0) {
            return VENCIDO;
        }
        return dias <= RegrasNegocio.DIAS_AVISO_VENCIMENTO ? VENCENDO : ATIVO;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
