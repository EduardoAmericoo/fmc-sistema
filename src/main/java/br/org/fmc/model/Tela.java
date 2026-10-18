package br.org.fmc.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * As telas do menu e a permissão que cada uma exige (BOARD-54).
 *
 * O arquivo é o nome do .fxml em resources/br/org/fmc/ui/ (ex.: imoveis.fxml).
 * Tela sem ação exigida (o Painel) abre para qualquer usuário logado.
 */
public enum Tela {
    PAINEL("painel", "Painel", null),
    ALUGUEIS("alugueis", "Aluguéis", Acao.VER_FINANCEIRO),
    IMOVEIS("imoveis", "Imóveis", Acao.VER_CADASTROS),
    INQUILINOS("inquilinos", "Inquilinos", Acao.VER_CADASTROS),
    CONTRATOS("contratos", "Contratos", Acao.VER_CADASTROS),
    USUARIOS("usuarios", "Usuários", Acao.GERENCIAR_USUARIOS);

    private final String arquivo;
    private final String titulo;
    private final Acao acaoExigida;

    Tela(String arquivo, String titulo, Acao acaoExigida) {
        this.arquivo = arquivo;
        this.titulo = titulo;
        this.acaoExigida = acaoExigida;
    }

    /** Este perfil pode abrir esta tela? */
    public boolean liberadaPara(Perfil perfil) {
        return acaoExigida == null || perfil.permite(acaoExigida);
    }

    /** Acha a tela pelo nome do arquivo (ex.: "imoveis"). */
    public static Optional<Tela> porArquivo(String arquivo) {
        return Arrays.stream(values()).filter(t -> t.arquivo.equals(arquivo)).findFirst();
    }

    public String getArquivo() {
        return arquivo;
    }

    public String getTitulo() {
        return titulo;
    }

    /** Pode ser nulo: a tela não exige nenhuma permissão. */
    public Acao getAcaoExigida() {
        return acaoExigida;
    }

    @Override
    public String toString() {
        return titulo;
    }
}
