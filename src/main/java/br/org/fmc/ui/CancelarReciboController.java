package br.org.fmc.ui;

import br.org.fmc.model.Lancamento;
import br.org.fmc.model.Recibo;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoRecebimento;
import br.org.fmc.util.Formatos;
import br.org.fmc.util.ReciboImpresso;
import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/** [D10] BOARD-65 · Janela que confirma o cancelamento de um recibo e pede o motivo. */
public class CancelarReciboController {

    private Recibo recibo;
    private ServicoRecebimento servico;
    private Consumer<Lancamento> depoisDeCancelar;

    @FXML private StackPane veu;
    @FXML private Label titulo;
    @FXML private Label subtitulo;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private Label valor;
    @FXML private Label pagoEm;
    @FXML private Label conta;
    @FXML private Label consequencias;
    @FXML private TextArea campoMotivo;

    /** @param depoisDeCancelar recebe o lançamento de estorno, para quem abriu atualizar a tela */
    public static void abrir(Recibo recibo, ServicoRecebimento servico, Consumer<Lancamento> depoisDeCancelar) {
        FXMLLoader carregador = new FXMLLoader(
                CancelarReciboController.class.getResource("/br/org/fmc/ui/cancelar-recibo.fxml"));
        try {
            Node janela = carregador.load();
            CancelarReciboController controle = carregador.getController();
            controle.preencher(recibo, servico, depoisDeCancelar);
            Navegador.mostrarJanela(janela);
            Platform.runLater(controle.campoMotivo::requestFocus);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela cancelar-recibo.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Recibo recibo, ServicoRecebimento servico, Consumer<Lancamento> depoisDeCancelar) {
        this.recibo = recibo;
        this.servico = servico;
        this.depoisDeCancelar = depoisDeCancelar;
        titulo.setText("Cancelar recibo " + recibo.getNumero());
        subtitulo.setText(recibo.getAluguel().getContrato().getInquilino().getNome() + " · "
                + new ReciboImpresso(recibo).mes() + " · " + recibo.getAluguel().getContrato().getNumero());
        valor.setText(Formatos.moeda(recibo.getValorTotal()));
        pagoEm.setText(Formatos.data(recibo.getDataPagamento()) + " · " + recibo.getFormaPagamento().getDescricao());
        conta.setText(recibo.getConta().toString());
        consequencias.setText("Ao cancelar, o aluguel volta a ficar em aberto e pode ser recebido de novo, com "
                + "um recibo novo. " + Formatos.moeda(recibo.getValorTotal()) + " saem do " + recibo.getConta()
                + " hoje (estorno). O número " + recibo.getNumero() + " fica registrado como cancelado e não "
                + "volta a ser usado. Não dá para desfazer.");
    }

    @FXML
    private void confirmar() {
        try {
            Lancamento estorno = servico.cancelarRecibo(recibo, campoMotivo.getText());
            fechar();
            depoisDeCancelar.accept(estorno);
        } catch (RegraNegocioException e) { // inclui AcessoNegadoException (sem permissão)
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Não foi possível cancelar o recibo: " + e.getMessage());
        }
    }

    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
