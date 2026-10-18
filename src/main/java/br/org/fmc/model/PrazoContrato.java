package br.org.fmc.model;

/** Prazos de contrato aceitos pela Fundação. */
public enum PrazoContrato {
    MESES_12(12),
    MESES_24(24),
    MESES_36(36);

    private final int meses;

    PrazoContrato(int meses) {
        this.meses = meses;
    }

    public int getMeses() {
        return meses;
    }

    @Override
    public String toString() {
        return meses + " meses";
    }
}
