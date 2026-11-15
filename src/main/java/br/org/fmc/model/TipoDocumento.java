package br.org.fmc.model;

/**
 * Tipos de documento digitalizado que podem ser anexados ao contrato, agrupados pela etapa da
 * locação (como no protótipo) e com a regra de quando são obrigatórios.
 *
 * Os nomes das constantes não mudam depois de usados: são eles que ficam gravados no banco.
 * Para mudar só o texto mostrado, mude a descrição.
 */
public enum TipoDocumento {
    // Locação
    PROPOSTA_LOCACAO("Proposta de locação", Etapa.LOCACAO, Obrigatorio.SEMPRE),
    RG_CPF_INQUILINO("Documentos pessoais (RG e CPF)", Etapa.LOCACAO, Obrigatorio.SEMPRE),
    COMPROVANTE_RENDA("Comprovante de renda", Etapa.LOCACAO, Obrigatorio.SEMPRE),
    COMPROVANTE_RESIDENCIA("Comprovante de residência", Etapa.LOCACAO, Obrigatorio.NAO),
    CONSULTA_CPF("Consulta de CPF", Etapa.LOCACAO, Obrigatorio.SEMPRE),
    DOCUMENTOS_FIADOR("Documentos do fiador", Etapa.LOCACAO, Obrigatorio.SE_FIADOR),
    COMPROVANTE_CAUCAO("Recibo de caução", Etapa.LOCACAO, Obrigatorio.SE_CAUCAO),
    // Entrada
    VISTORIA("Termo de vistoria de entrada", Etapa.ENTRADA, Obrigatorio.SEMPRE),
    CONTRATO_ASSINADO("Contrato de locação", Etapa.ENTRADA, Obrigatorio.SEMPRE),
    TERMO_ENTREGA_CHAVES("Termo de recebimento de chaves", Etapa.ENTRADA, Obrigatorio.SEMPRE),
    // Durante a locação
    TERMO_ADITIVO("Aditivo de reajuste / prorrogação", Etapa.DURANTE, Obrigatorio.NAO),
    NOTIFICACAO_COBRANCA("Notificação de cobrança", Etapa.DURANTE, Obrigatorio.NAO),
    // Saída
    VISTORIA_SAIDA("Laudo de vistoria de saída", Etapa.SAIDA, Obrigatorio.SE_ENCERRADO),
    DEVOLUCAO_CHAVES("Termo de devolução de chaves", Etapa.SAIDA, Obrigatorio.SE_ENCERRADO),
    // Outros
    OUTRO("Outro documento", Etapa.OUTROS, Obrigatorio.NAO);

    /** As etapas da locação, na ordem em que acontecem. */
    public enum Etapa {
        LOCACAO("Locação"),
        ENTRADA("Entrada"),
        DURANTE("Durante a locação"),
        SAIDA("Saída"),
        OUTROS("Outros");

        private final String descricao;

        Etapa(String descricao) {
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

    /** Quando o documento é obrigatório no contrato. */
    public enum Obrigatorio { SEMPRE, SE_FIADOR, SE_CAUCAO, SE_ENCERRADO, NAO }

    private final String descricao;
    private final Etapa etapa;
    private final Obrigatorio obrigatorio;

    TipoDocumento(String descricao, Etapa etapa, Obrigatorio obrigatorio) {
        this.descricao = descricao;
        this.etapa = etapa;
        this.obrigatorio = obrigatorio;
    }

    public String getDescricao() {
        return descricao;
    }

    public Etapa getEtapa() {
        return etapa;
    }

    /**
     * Este documento é obrigatório neste contrato? Os do fiador só com fiança, o recibo de caução
     * só com caução, e os de saída só quando o contrato foi encerrado.
     */
    public boolean obrigatorioPara(Contrato contrato) {
        return switch (obrigatorio) {
            case SEMPRE -> true;
            case SE_FIADOR -> contrato.getGarantia() instanceof Fianca;
            case SE_CAUCAO -> contrato.getGarantia() instanceof Caucao;
            case SE_ENCERRADO -> contrato.getStatus() == StatusContrato.ENCERRADO;
            case NAO -> false;
        };
    }

    @Override
    public String toString() {
        return descricao;
    }
}
