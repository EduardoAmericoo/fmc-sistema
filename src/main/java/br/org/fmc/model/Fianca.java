package br.org.fmc.model;

import java.util.ArrayList;
import java.util.List;

/** Garantia por fiador (a grande maioria dos contratos). */
public class Fianca extends Garantia {

    private Fiador fiador;

    public Fianca(Fiador fiador) {
        setFiador(fiador);
    }

    /** O fiador precisa ter renda informada e, se for casado, o cônjuge também entra. */
    @Override
    public List<String> validar(Contrato contrato) {
        List<String> erros = new ArrayList<>();
        if (fiador.getRendaMensal().signum() <= 0) {
            erros.add("Informe a renda do fiador.");
        }
        if (fiador.ehCasado() && fiador.getConjuge() == null) {
            erros.add("Fiador casado: informe também o cônjuge.");
        }
        return erros;
    }

    @Override
    public String getDescricao() {
        return "Fiador: " + fiador.getNome();
    }

    public Fiador getFiador() {
        return fiador;
    }

    public void setFiador(Fiador fiador) {
        this.fiador = Valida.obrigatorio(fiador, "o fiador");
    }
}
