package br.org.fmc.model;

/** Tipos de documento digitalizado que podem ser anexados ao contrato. */
public enum TipoDocumento {
    CONTRATO_ASSINADO("Contrato assinado"),
    RG_CPF_INQUILINO("RG / CPF do inquilino"),
    COMPROVANTE_RENDA("Comprovante de renda"),
    COMPROVANTE_RESIDENCIA("Comprovante de residência"),
    DOCUMENTOS_FIADOR("Documentos do fiador"),
    COMPROVANTE_CAUCAO("Comprovante da caução"),
    VISTORIA("Laudo de vistoria"),
    TERMO_ADITIVO("Termo aditivo / prorrogação"),
    TERMO_ENTREGA_CHAVES("Termo de entrega das chaves"),
    OUTRO("Outro documento");

    private final String descricao;

    TipoDocumento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
