package br.org.fmc.ui;

import br.org.fmc.model.Contrato;
import br.org.fmc.model.DocumentoContrato;
import br.org.fmc.model.TipoDocumento;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoDocumentos;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * Controla a janela "Enviar documento digitalizado" (ui/enviar-documento.fxml).
 * Só cuida da janela: quem confere formato, tamanho e data e copia o arquivo é o ServicoDocumentos.
 */
public class EnviarDocumentoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Última pasta usada, para a próxima escolha já abrir lá (ex.: a pasta do scanner). */
    private static File ultimaPasta;

    private ServicoDocumentos servico;
    private Contrato contrato;
    private Consumer<DocumentoContrato> depoisDeEnviar;
    private Path arquivo;

    @FXML
    private StackPane veu;
    @FXML
    private HBox avisoErros;
    @FXML
    private Label textoErros;
    @FXML
    private TextField campoContrato;
    @FXML
    private ComboBox<TipoDocumento> campoTipo;
    @FXML
    private DatePicker campoData;
    @FXML
    private HBox caixaArquivo;
    @FXML
    private TextField campoArquivo;
    @FXML
    private Label dicaArquivo;
    @FXML
    private TextField campoObservacao;

    public static void abrir(Contrato contrato, ServicoDocumentos servico, Consumer<DocumentoContrato> depoisDeEnviar) {
        FXMLLoader carregador = new FXMLLoader(
                EnviarDocumentoController.class.getResource("/br/org/fmc/ui/enviar-documento.fxml"));
        try {
            Node janela = carregador.load();
            EnviarDocumentoController controle = carregador.getController();
            controle.preencher(contrato, servico, depoisDeEnviar);
            Navegador.mostrarJanela(janela);
            controle.campoTipo.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela enviar-documento.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        campoTipo.getItems().setAll(TipoDocumento.values());
        campoData.setConverter(new StringConverter<>() {
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

        // Arrastar o arquivo (do Explorer ou da pasta do scanner) para a caixa também escolhe.
        caixaArquivo.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        caixaArquivo.setOnDragDropped(e -> {
            boolean ok = e.getDragboard().hasFiles() && !e.getDragboard().getFiles().isEmpty();
            if (ok) {
                usarArquivo(e.getDragboard().getFiles().get(0));
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        mostrar(avisoErros, false);
    }

    private void preencher(Contrato c, ServicoDocumentos servico, Consumer<DocumentoContrato> depois) {
        this.contrato = c;
        this.servico = servico;
        this.depoisDeEnviar = depois;
        campoContrato.setText(c.getNumero() + " · " + c.getImovel().getCodigo() + " · " + c.getInquilino().getNome());
        campoData.setValue(servico.hoje());
    }

    @FXML
    private void escolherArquivo() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Escolher documento digitalizado");
        seletor.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF ou foto (PDF, JPG, JPEG, PNG)",
                        "*.pdf", "*.PDF", "*.jpg", "*.JPG", "*.jpeg", "*.JPEG", "*.png", "*.PNG"),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*"));
        if (ultimaPasta != null && ultimaPasta.isDirectory()) {
            seletor.setInitialDirectory(ultimaPasta);
        }
        File escolhido = seletor.showOpenDialog(veu.getScene().getWindow());
        if (escolhido != null) {
            ultimaPasta = escolhido.getParentFile();
            usarArquivo(escolhido);
        }
    }

    private void usarArquivo(File escolhido) {
        arquivo = escolhido.toPath();
        campoArquivo.setText(escolhido.getName());
        dicaArquivo.setText(ServicoDocumentos.megas(escolhido.length()) + " · PDF, JPG, JPEG ou PNG, até 10 MB.");
    }

    /** Botão "Enviar documento". */
    @FXML
    private void enviar() {
        LocalDate data = campoData.getConverter().fromString(campoData.getEditor().getText());
        try {
            DocumentoContrato doc = servico.enviar(contrato.getId(), campoTipo.getValue(), data, arquivo,
                    campoObservacao.getText());
            fechar();
            depoisDeEnviar.accept(doc);
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
