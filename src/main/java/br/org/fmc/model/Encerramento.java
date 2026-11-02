package br.org.fmc.model;

import java.time.LocalDate;

/**
 * O encerramento de um contrato (BOARD-174): o inquilino devolveu as chaves e saiu do imóvel,
 * no fim do prazo ou antes.
 *
 * Guarda o dia da saída (a partir dele o contrato não gera mais aluguel), o motivo,
 * uma observação opcional, quando foi registrado e quem registrou.
 */
public class Encerramento {

    private final LocalDate dataSaida;
    private final MotivoEncerramento motivo;
    private final String observacao;
    private final LocalDate registradoEm;
    private final String feitoPor;

    public Encerramento(LocalDate dataSaida, MotivoEncerramento motivo, String observacao,
                        LocalDate registradoEm, String feitoPor) {
        this.dataSaida = Valida.obrigatorio(dataSaida, "a data de saída");
        this.motivo = Valida.obrigatorio(motivo, "o motivo do encerramento");
        this.observacao = Valida.textoOpcional(observacao);
        this.registradoEm = Valida.obrigatorio(registradoEm, "a data do registro");
        this.feitoPor = Valida.textoObrigatorio(feitoPor, "quem encerrou o contrato");
    }

    public LocalDate getDataSaida() {
        return dataSaida;
    }

    public MotivoEncerramento getMotivo() {
        return motivo;
    }

    /** Observação livre, ou null. */
    public String getObservacao() {
        return observacao;
    }

    public LocalDate getRegistradoEm() {
        return registradoEm;
    }

    public String getFeitoPor() {
        return feitoPor;
    }
}
