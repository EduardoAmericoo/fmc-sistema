package br.org.fmc.ui;

import br.org.fmc.model.Contrato;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoContratos;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

/**
 * Controla a janela "Encerrar contrato" (ui/encerrar-contrato.fxml), BOARD-174.
 * Só cuida da janela: quem confere as regras e grava é o ServicoContratos.
 */
public class EncerrarContratoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ServicoContratos servico;
    private Contrato contrato;
    private Consumer<Contrato> depoisDeEncerrar;

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
    private Label consequencias;
    @FXML
    private DatePicker campoSaida;
    @FXML
    private Label dicaSaida;
    @FXML
    private ComboBox<MotivoEncerramento> campoMotivo;
    @FXML
    private Label rotuloObservacao;
    @FXML
    private TextField campoObservacao;

    /**
     * Abre a janela para o contrato escolhido.
     *
     * @param depoisDeEncerrar o que a tela de Contratos faz depois (recarregar a lista e mostrar o aviso)
     */
    public static void abrir(Contrato contrato, ServicoContratos servico, Consumer<Contrato> depoisDeEncerrar) {
        FXMLLoader carregador = new FXMLLoader(
                EncerrarContratoController.class.getResource("/br/org/fmc/ui/encerrar-contrato.fxml"));
        try {
            Node janela = carregador.load();
            EncerrarContratoController controle = carregador.getController();
            controle.preencher(contrato, servico, depoisDeEncerrar);
            Navegador.mostrarJanela(janela);
            controle.campoSaida.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela encerrar-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        campoMotivo.getItems().setAll(MotivoEncerramento.values());
        // "Outro motivo" pede uma explicação.
        campoMotivo.valueProperty().addListener((obs, antes, agora) -> rotuloObservacao.setText(
                agora == MotivoEncerramento.OUTRO ? "Observação *" : "Observação"));
        campoSaida.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate data) {
                return data == null ? "" : data.format(DATA);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.trim(), DATA);
                } catch (DateTimeParseException e) {
                    return null; // data inválida: o service avisa que falta a data
                }
            }
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
        this.depoisDeEncerrar = depois;

        titulo.setText("Encerrar contrato " + c.getNumero());
        subtitulo.setText(c.getImovel().getCodigo() + " · " + c.getInquilino().getNome() + " · vigência de "
                + Formatos.data(c.getDataEntregaChaves()) + " a " + Formatos.data(c.getDataTermino()) + ".");
        consequencias.setText("O imóvel " + c.getImovel().getCodigo() + " volta a ficar vago e pode receber outro "
                + "contrato. Os aluguéis até o dia da saída continuam valendo, inclusive os que estão em aberto. "
                + "O contrato não é apagado: fica no histórico como Encerrado.");
        dicaSaida.setText("Até este dia o contrato gera aluguel.");

        campoSaida.setValue(LocalDate.now());
        // Contrato vencido ou vencendo: o motivo mais provável é o fim do prazo.
        boolean fimDoPrazo = !c.getDataTermino().isAfter(LocalDate.now().plusDays(RegrasNegocio.DIAS_AVISO_VENCIMENTO));
        campoMotivo.setValue(fimDoPrazo ? MotivoEncerramento.FIM_DO_PRAZO : null);
        campoObservacao.clear();
    }

    /** Botão "Encerrar contrato". */
    @FXML
    private void confirmar() {
        LocalDate saida = campoSaida.getConverter().fromString(campoSaida.getEditor().getText());
        try {
            Contrato encerrado = servico.encerrar(contrato.getId(), saida, campoMotivo.getValue(),
                    campoObservacao.getText());
            fechar();
            depoisDeEncerrar.accept(encerrado);
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

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }
}
