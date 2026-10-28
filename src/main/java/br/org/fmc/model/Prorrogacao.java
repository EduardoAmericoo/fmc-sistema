package br.org.fmc.model;

import java.time.LocalDate;

/**
 * Uma prorrogação de contrato (BOARD-169): o contrato venceu ou está para vencer e
 * ganha um novo prazo, contado a partir do término anterior.
 *
 * Fica guardada no histórico do contrato: quando foi feita, o término antes e depois,
 * o prazo escolhido e quem fez.
 */
public class Prorrogacao {

    private Long id;
    private final LocalDate data;
    private final LocalDate terminoAnterior;
    private final LocalDate novoTermino;
    private final PrazoContrato prazo;
    private final String feitaPor;

    public Prorrogacao(LocalDate data, LocalDate terminoAnterior, PrazoContrato prazo, String feitaPor) {
        this.data = Valida.obrigatorio(data, "a data da prorrogação");
        this.terminoAnterior = Valida.obrigatorio(terminoAnterior, "o término anterior");
        this.prazo = Valida.obrigatorio(prazo, "o prazo da prorrogação");
        this.feitaPor = Valida.textoObrigatorio(feitaPor, "quem fez a prorrogação");
        this.novoTermino = terminoAnterior.plusMonths(prazo.getMeses());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalDate getTerminoAnterior() {
        return terminoAnterior;
    }

    public LocalDate getNovoTermino() {
        return novoTermino;
    }

    public PrazoContrato getPrazo() {
        return prazo;
    }

    public String getFeitaPor() {
        return feitaPor;
    }
}
