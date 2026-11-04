package br.org.fmc.ui;

import br.org.fmc.model.Contrato;
import br.org.fmc.model.Reajuste;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoContratos;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Controla a janela "Aplicar reajuste anual" (ui/reajustar-contrato.fxml), [D9] BOARD-46.
 * Só cuida da janela: quem confere se pode reajustar e grava é o ServicoContratos.
 * A prévia do aluguel novo só ajuda a pessoa a conferir antes de aplicar.
 */
public class ReajustarContratoController {

    private final ServicoContratos servico = new ServicoContratos();
    private Contrato contrato;
    private Consumer<Reajuste> depoisDeReajustar;

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
    private Label aluguelAtual;
    @FXML
    private Label seloAniversario;
    @FXML
    private TextField campoPercentual;
    @FXML
    private Label previa;

    /**
     * Abre a janela para o contrato escolhido.
     *
     * @param depoisDeReajustar o que fazer depois (fechar os detalhes, recarregar a lista e avisar)
     */
    public static void abrir(Contrato contrato, Consumer<Reajuste> depoisDeReajustar) {
        FXMLLoader carregador = new FXMLLoader(
                ReajustarContratoController.class.getResource("/br/org/fmc/ui/reajustar-contrato.fxml"));
        try {
            Node janela = carregador.load();
            ReajustarContratoController controle = carregador.getController();
            controle.preencher(contrato, depoisDeReajustar);
            Navegador.mostrarJanela(janela);
            controle.campoPercentual.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela reajustar-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        campoPercentual.textProperty().addListener((obs, antes, agora) -> atualizarPrevia());
        // Esc fecha a janela.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        mostrar(avisoErros, false);
    }

    private void preencher(Contrato c, Consumer<Reajuste> depois) {
        this.contrato = c;
        this.depoisDeReajustar = depois;
        titulo.setText("Reajuste anual do contrato " + c.getNumero());
        subtitulo.setText(c.getImovel().getCodigo() + " · " + c.getInquilino().getNome()
                + ". O aluguel novo vale para os aluguéis que vencem depois do aniversário e ainda não foram pagos.");
        aluguelAtual.setText(Formatos.moeda(c.getValorAluguel()));
        seloAniversario.setText("Venceu em " + Formatos.data(c.proximoReajuste()));
        atualizarPrevia();
    }

    /** Prévia: o aluguel novo com o percentual digitado, ou o que está faltando. */
    private void atualizarPrevia() {
        if (contrato == null) {
            return;
        }
        try {
            BigDecimal p = ServicoContratos.lerPercentual(campoPercentual.getText());
            previa.setText("Aluguel novo: " + Formatos.moeda(Reajuste.novoValor(contrato.getValorAluguel(), p))
                    + " (era " + Formatos.moeda(contrato.getValorAluguel()) + "). Próximo reajuste em "
                    + Formatos.data(contrato.proximoReajuste().plusMonths(12)) + ".");
        } catch (RegraNegocioException e) {
            previa.setText("Digite o percentual para ver o aluguel novo.");
        }
    }

    /** Botão "Aplicar reajuste". */
    @FXML
    private void confirmar() {
        try {
            Reajuste r = servico.reajustar(contrato.getId(), campoPercentual.getText());
            fechar();
            depoisDeReajustar.accept(r);
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Não foi possível gravar o reajuste no banco de dados. Tente de novo.");
        }
    }

    /** Botões "Cancelar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }
}
