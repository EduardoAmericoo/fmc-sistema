package br.org.fmc.ui;

import br.org.fmc.model.Contrato;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Prorrogacao;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoContratos;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a janela "Prorrogar contrato" (ui/prorrogar-contrato.fxml), BOARD-169.
 * Só cuida da janela: quem confere se pode prorrogar e grava é o ServicoContratos.
 * A prévia do novo término só ajuda a pessoa a decidir.
 */
public class ProrrogarContratoController {

    private ServicoContratos servico;
    private Contrato contrato;
    private Consumer<Contrato> depoisDeProrrogar;

    @FXML
    private StackPane veu;
    @FXML
    private Label titulo;
    @FXML
    private Label subtitulo;
    @FXML
    private HBox avisoErros;
    @FXML
    private Label textoErros;
    @FXML
    private Label terminoAtual;
    @FXML
    private Label seloSituacao;
    @FXML
    private Label explicacao;
    @FXML
    private ToggleGroup grupoPrazo;
    @FXML
    private ToggleButton opcao12;
    @FXML
    private ToggleButton opcao24;
    @FXML
    private ToggleButton opcao36;
    @FXML
    private Label novoTermino;
    @FXML
    private VBox historico;

    /**
     * Abre a janela para o contrato escolhido.
     *
     * @param depoisDeProrrogar o que a tela de Contratos faz depois (recarregar a lista e mostrar o aviso)
     */
    public static void abrir(Contrato contrato, ServicoContratos servico, Consumer<Contrato> depoisDeProrrogar) {
        FXMLLoader carregador = new FXMLLoader(
                ProrrogarContratoController.class.getResource("/br/org/fmc/ui/prorrogar-contrato.fxml"));
        try {
            Node janela = carregador.load();
            ProrrogarContratoController controle = carregador.getController();
            controle.preencher(contrato, servico, depoisDeProrrogar);
            Navegador.mostrarJanela(janela);
            controle.opcao12.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela prorrogar-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        opcao12.setUserData(PrazoContrato.MESES_12);
        opcao24.setUserData(PrazoContrato.MESES_24);
        opcao36.setUserData(PrazoContrato.MESES_36);
        // Sempre um prazo marcado (clicar no marcado não desmarca).
        grupoPrazo.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                grupoPrazo.selectToggle(antes);
            }
            atualizarNovoTermino();
        });
        // Esc fecha a janela.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        mostrar(avisoErros, false);
    }

    private void preencher(Contrato c, ServicoContratos servico, Consumer<Contrato> depois) {
        this.contrato = c;
        this.servico = servico;
        this.depoisDeProrrogar = depois;

        titulo.setText("Prorrogar contrato " + c.getNumero());
        subtitulo.setText(c.getImovel().getCodigo() + " · " + c.getInquilino().getNome()
                + ". O novo prazo conta a partir do término atual, e o aluguel continua o mesmo.");
        terminoAtual.setText(Formatos.data(c.getDataTermino()));

        long dias = ChronoUnit.DAYS.between(LocalDate.now(), c.getDataTermino());
        if (dias < 0) {
            seloSituacao.setText("Vencido há " + quantosDias(-dias));
            seloSituacao.getStyleClass().setAll("label", "selo", "selo-erro");
            explicacao.setText("Enquanto não for prorrogado, este contrato não gera aluguel. "
                    + "Ao prorrogar, ele volta a gerar aluguel, inclusive nos meses desde o vencimento.");
        } else {
            seloSituacao.setText(dias == 0 ? "Vence hoje" : "Vence em " + quantosDias(dias));
            seloSituacao.getStyleClass().setAll("label", "selo", "selo-alerta");
            explicacao.setText("Prorrogando agora, o inquilino continua sendo cobrado sem interrupção.");
        }

        montarHistorico(c.getProrrogacoes());
        grupoPrazo.selectToggle(opcao12);
        atualizarNovoTermino();
    }

    /** Prévia: o novo término com o prazo marcado. */
    private void atualizarNovoTermino() {
        PrazoContrato prazo = prazoEscolhido();
        if (contrato == null || prazo == null) {
            return;
        }
        novoTermino.setText("Novo término: " + Formatos.data(contrato.getDataTermino().plusMonths(prazo.getMeses()))
                + ". Os aluguéis continuam vencendo todo dia " + contrato.getDiaVencimento() + ".");
    }

    private void montarHistorico(List<Prorrogacao> prorrogacoes) {
        historico.getChildren().clear();
        if (prorrogacoes.isEmpty()) {
            Label nenhuma = new Label("Este contrato ainda não foi prorrogado.");
            nenhuma.getStyleClass().add("dica");
            historico.getChildren().add(nenhuma);
            return;
        }
        // Da mais recente para a mais antiga.
        for (int i = prorrogacoes.size() - 1; i >= 0; i--) {
            Prorrogacao p = prorrogacoes.get(i);
            Label linha1 = new Label(Formatos.data(p.getTerminoAnterior()) + " → " + Formatos.data(p.getNovoTermino())
                    + " (" + p.getPrazo() + ")");
            linha1.getStyleClass().add("celula-nome");
            Label linha2 = new Label("Feita em " + Formatos.data(p.getData()) + " por " + p.getFeitaPor());
            linha2.getStyleClass().add("celula-detalhe");
            HBox item = new HBox(10, new FontIcon("fth-clock"), new VBox(2, linha1, linha2));
            item.getStyleClass().add("item-historico");
            historico.getChildren().add(item);
        }
    }

    /** Botão "Prorrogar contrato". */
    @FXML
    private void confirmar() {
        try {
            Contrato prorrogado = servico.prorrogar(contrato.getId(), prazoEscolhido());
            fechar();
            depoisDeProrrogar.accept(prorrogado);
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Botões "Cancelar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    private PrazoContrato prazoEscolhido() {
        Toggle marcado = grupoPrazo.getSelectedToggle();
        return marcado == null ? null : (PrazoContrato) marcado.getUserData();
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static String quantosDias(long dias) {
        return dias == 1 ? "1 dia" : dias + " dias";
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }
}
