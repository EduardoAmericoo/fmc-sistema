package br.org.fmc.ui;

import static br.org.fmc.ui.Detalhe.coluna;
import static br.org.fmc.ui.Detalhe.linha;
import static br.org.fmc.ui.Detalhe.texto;
import static br.org.fmc.ui.Detalhe.titulo;
import static br.org.fmc.ui.Detalhe.tresColunas;
import static br.org.fmc.ui.Detalhe.veredito;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Encerramento;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Prorrogacao;
import br.org.fmc.model.Reajuste;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.SituacaoContrato;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controla a janela "Detalhes do contrato" (ui/detalhes-contrato.fxml), BOARD-59.
 * Só mostra o que já está no contrato carregado: não grava nada nem chama o banco.
 *
 * O desenho segue a janela "Contrato" do protótipo: três colunas (Imóvel, Locatário e
 * Garantia), com o rótulo à esquerda e o valor à direita; embaixo, prazo, vigência e
 * vencimento; e a situação do contrato em caixas coloridas (verde, amarela, vermelha, cinza).
 */
public class DetalhesContratoController {

    @FXML
    private StackPane veu;
    @FXML
    private Label titulo;
    @FXML
    private Label seloSituacao;
    @FXML
    private VBox corpo;
    @FXML
    private Button botaoReajustar;

    /** O contrato aberto e o que fazer quando ele muda por esta janela. */
    private Contrato contrato;
    private Runnable depoisDeMudar;

    /** Abre a janela com os detalhes do contrato escolhido. */
    public static void abrir(Contrato contrato) {
        abrir(contrato, null);
    }

    /**
     * Abre a janela com os detalhes do contrato escolhido.
     *
     * @param depoisDeMudar o que a tela faz quando algo muda pela janela (ex.: reajuste aplicado)
     */
    public static void abrir(Contrato contrato, Runnable depoisDeMudar) {
        FXMLLoader carregador = new FXMLLoader(
                DetalhesContratoController.class.getResource("/br/org/fmc/ui/detalhes-contrato.fxml"));
        try {
            Node janela = carregador.load();
            DetalhesContratoController controle = carregador.getController();
            controle.depoisDeMudar = depoisDeMudar;
            controle.preencher(contrato);
            Navegador.mostrarJanela(janela);
            janela.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela detalhes-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        // Esc fecha a janela.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Contrato c) {
        LocalDate hoje = LocalDate.now();
        SituacaoContrato situacao = SituacaoContrato.de(c, hoje);
        titulo.setText("Contrato " + c.getNumero());
        seloSituacao.setText(switch (situacao) {
            case ATIVO -> "Ativo";
            case VENCENDO -> "Vencendo";
            case VENCIDO -> "Vencido";
            case ENCERRADO -> "Encerrado";
        });
        seloSituacao.getStyleClass().setAll("label", "selo", switch (situacao) {
            case ATIVO -> "selo-sucesso";
            case VENCENDO -> "selo-alerta";
            case VENCIDO -> "selo-erro";
            case ENCERRADO -> "selo-neutro";
        });

        corpo.getChildren().addAll(
                tresColunas(colunaImovel(c), colunaLocatario(c.getInquilino()), colunaGarantia(c)),
                tresColunas(linha("Prazo", c.getPrazo().toString()),
                        linha("Vigência", Formatos.data(c.getDataEntregaChaves()) + " a "
                                + Formatos.data(c.getDataTermino())),
                        linha("Vencimento", "Todo dia " + c.getDiaVencimento())),
                secaoSituacao(c, situacao, hoje));
        if (c.getStatus() == StatusContrato.ATIVO || !c.getReajustes().isEmpty()) {
            corpo.getChildren().add(secaoReajuste(c, hoje));
        }
        this.contrato = c;
        boolean podeReajustar = c.reajustePendente(hoje) && Sessao.pode(Acao.REAJUSTAR_CONTRATO);
        botaoReajustar.setVisible(podeReajustar);
        botaoReajustar.setManaged(podeReajustar);
    }

    /**
     * Reajuste anual ([D9] BOARD-46), como no protótipo: em vermelho quando venceu e ainda não
     * foi aplicado; em cinza, a data do próximo. Embaixo, os reajustes já feitos.
     */
    private static VBox secaoReajuste(Contrato c, LocalDate hoje) {
        VBox secao = new VBox(8, titulo("Reajuste anual"));
        if (c.reajustePendente(hoje)) {
            secao.getChildren().add(veredito("veredito-erro", "Reajuste vencido desde "
                    + Formatos.data(c.proximoReajuste()) + " e ainda não aplicado. O índice é a média do INPC "
                    + "e do IGP-DI dos últimos 12 meses; o aluguel novo vale para os aluguéis ainda não pagos."));
        } else if (c.getStatus() == StatusContrato.ATIVO) {
            secao.getChildren().add(veredito("veredito-neutro",
                    "Próximo reajuste em " + Formatos.data(c.proximoReajuste()) + "."));
        }
        List<Reajuste> feitos = c.getReajustes();
        for (int i = feitos.size() - 1; i >= 0; i--) {
            Reajuste r = feitos.get(i);
            secao.getChildren().add(veredito("veredito-neutro", "✓ Reajustado em " + Formatos.data(r.getAplicadoEm())
                    + " em " + r.getPercentual().stripTrailingZeros().toPlainString().replace('.', ',') + "%: de "
                    + Formatos.moeda(r.getValorAnterior()) + " para " + Formatos.moeda(r.getValorNovo())
                    + " (aniversário de " + Formatos.data(r.getAniversario()) + ", por " + r.getFeitoPor() + ")"));
        }
        return secao;
    }

    /** Botão "Aplicar reajuste": abre a janela do percentual; depois de aplicar, fecha e atualiza a lista. */
    @FXML
    private void reajustar() {
        ReajustarContratoController.abrir(contrato, r -> {
            fechar();
            if (depoisDeMudar != null) {
                depoisDeMudar.run();
            }
        });
    }

    // ---------- as três colunas ----------

    private static VBox colunaImovel(Contrato c) {
        Imovel i = c.getImovel();
        String endereco = i.getEndereco() + (i.getBairro() == null || i.getBairro().isBlank() ? "" : " · " + i.getBairro());
        return coluna("Imóvel",
                linha("Código", i.getCodigo()),
                linha("Unidade", texto(i.getUnidade())),
                linha("Endereço", endereco),
                linha("Aluguel", Formatos.moeda(c.getValorAluguel())),
                linha("Taxa de luz", Formatos.moeda(c.getTaxaLuz())),
                linha("IPTU", Formatos.moeda(c.getValorIptu())));
    }

    private static VBox colunaLocatario(Inquilino i) {
        List<Node> linhas = new ArrayList<>(List.of(
                linha("Nome", i.getNome()),
                linha("CPF", i.getCpf().formatado()),
                linha("Telefone", texto(i.getTelefone()))));
        if (i.getConjuge() != null) {
            linhas.add(linha("Cônjuge", i.getConjuge().getNome()));
        }
        linhas.add(linha("Renda familiar", Formatos.moeda(i.rendaConsiderada())));
        return coluna("Locatário", linhas.toArray(Node[]::new));
    }

    /** A coluna da garantia (fiador ou caução); a ficha do inquilino usa a mesma. */
    static VBox colunaGarantia(Contrato c) {
        if (c.getGarantia() instanceof Caucao caucao) {
            return coluna("Garantia · caução",
                    linha("Valor", Formatos.moeda(caucao.getValor())),
                    linha("Recebida em", Formatos.data(caucao.getDataRecebimento())),
                    linha("Forma", caucao.getFormaPagamento().toString()),
                    linha("Recibo", texto(caucao.getNumeroRecibo())));
        }
        Fiador fiador = ((Fianca) c.getGarantia()).getFiador();
        List<Node> linhas = new ArrayList<>(List.of(
                linha("Nome", fiador.getNome()),
                linha("CPF", fiador.getCpf().formatado()),
                linha("Telefone", texto(fiador.getTelefone()))));
        Conjuge conjuge = fiador.getConjuge();
        if (conjuge != null) {
            linhas.add(linha("Cônjuge", conjuge.getNome()));
        }
        linhas.add(linha("Renda", Formatos.moeda(fiador.rendaConsiderada())));
        linhas.add(linha("Endereço", texto(fiador.getEndereco())));
        return coluna("Garantia · fiador", linhas.toArray(Node[]::new));
    }

    // ---------- situação do contrato ----------

    /**
     * A situação em caixas: a primeira diz o que está acontecendo com o contrato (mesma cor do
     * selo do topo); depois a regra de renda e o histórico (prorrogações e encerramento).
     */
    private static VBox secaoSituacao(Contrato c, SituacaoContrato situacao, LocalDate hoje) {
        long dias = ChronoUnit.DAYS.between(hoje, c.getDataTermino());
        VBox secao = new VBox(8, titulo("Situação do contrato"), switch (situacao) {
            case ATIVO -> veredito("veredito-ok", "Ativo até " + Formatos.data(c.getDataTermino())
                    + ". Gera aluguel todo mês, com vencimento no dia " + c.getDiaVencimento() + ".");
            case VENCENDO -> veredito("veredito-alerta", "Vence em " + Formatos.data(c.getDataTermino())
                    + (dias == 0 ? ", hoje." : ", daqui a " + dias + (dias == 1 ? " dia." : " dias."))
                    + " Combine com o inquilino se vai prorrogar ou sair.");
            case VENCIDO -> veredito("veredito-erro", "Venceu em " + Formatos.data(c.getDataTermino())
                    + " e não gera aluguel até ser prorrogado ou encerrado.");
            case ENCERRADO -> veredito("veredito-neutro", encerrado(c.getEncerramento()));
        });

        BigDecimal minimo = c.getValorAluguel().multiply(BigDecimal.valueOf(RegrasNegocio.MULTIPLO_RENDA));
        boolean rendaOk = c.atendeRegraRenda();
        secao.getChildren().add(veredito(rendaOk ? "veredito-ok" : "veredito-erro",
                "Renda familiar " + Formatos.moeda(c.getInquilino().rendaConsiderada()) + (rendaOk ? " ≥ " : " < ")
                        + RegrasNegocio.MULTIPLO_RENDA + "× o aluguel (" + Formatos.moeda(minimo) + ")"));

        if (c.getProrrogacoes().isEmpty()) {
            secao.getChildren().add(veredito("veredito-neutro", "✓ Contrato original, sem prorrogações"));
        }
        for (Prorrogacao p : c.getProrrogacoes()) {
            secao.getChildren().add(veredito("veredito-neutro", "✓ Prorrogado em " + Formatos.data(p.getData())
                    + " por " + p.getPrazo() + ": o término passou de " + Formatos.data(p.getTerminoAnterior())
                    + " para " + Formatos.data(p.getNovoTermino())));
        }
        Encerramento e = c.getEncerramento();
        if (e != null && e.getObservacao() != null && !e.getObservacao().isBlank()) {
            secao.getChildren().add(veredito("veredito-neutro", "Observação do encerramento: " + e.getObservacao()));
        }
        return secao;
    }

    private static String encerrado(Encerramento e) {
        if (e == null) {
            return "Contrato encerrado.";
        }
        return "Encerrado: chaves devolvidas em " + Formatos.data(e.getDataSaida()) + " · " + e.getMotivo()
                + ". Não gera mais aluguel; os aluguéis em aberto continuam sendo cobrados.";
    }

    /** Botões "Fechar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }
}
