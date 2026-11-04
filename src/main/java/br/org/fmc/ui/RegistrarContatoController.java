package br.org.fmc.ui;

import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCobranca;
import br.org.fmc.service.ServicoCobranca.Linha;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/** Janela para salvar o resultado de uma tentativa de cobrança. */
public class RegistrarContatoController {

    private Linha linha;
    private ServicoCobranca servico;
    private Consumer<ContatoCobranca> depoisDeRegistrar;

    @FXML private StackPane veu;
    @FXML private Label subtitulo;
    @FXML private Label resumoDivida;
    @FXML private Label ultimoContato;
    @FXML private TextArea campoResultado;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;

    public static void abrir(Linha linha, ServicoCobranca servico,
            Consumer<ContatoCobranca> depoisDeRegistrar) {
        FXMLLoader carregador = new FXMLLoader(
                RegistrarContatoController.class.getResource("/br/org/fmc/ui/registrar-contato.fxml"));
        try {
            Node janela = carregador.load();
            RegistrarContatoController controle = carregador.getController();
            controle.preencher(linha, servico, depoisDeRegistrar);
            Navegador.mostrarJanela(janela);
            controle.campoResultado.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel abrir registrar-contato.fxml", e);
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

    private void preencher(Linha linha, ServicoCobranca servico,
            Consumer<ContatoCobranca> depoisDeRegistrar) {
        this.linha = linha;
        this.servico = servico;
        this.depoisDeRegistrar = depoisDeRegistrar;
        String telefone = linha.contrato().getInquilino().getTelefone();
        subtitulo.setText(linha.contrato().getInquilino().getNome()
                + (telefone == null ? "" : " · " + telefone));
        resumoDivida.setText("Valor atualizado informado ao inquilino: " + Formatos.moeda(linha.valorAtualizado())
                + " (" + linha.mesesEmAberto() + " meses em aberto: " + competencias() + ").");
        if (linha.ultimoContato() == null) {
            ultimoContato.setText("Nenhum contato registrado ainda.");
        } else {
            ContatoCobranca anterior = linha.ultimoContato();
            ultimoContato.setText("Último contato em " + Formatos.data(anterior.registradoEm().toLocalDate())
                    + " por " + anterior.feitoPor().getNome() + ": " + anterior.resultado());
        }
    }

    private String competencias() {
        return String.join(", ", linha.alugueis().stream()
                .map(a -> String.format("%02d/%d", a.getCompetencia().getMonthValue(), a.getCompetencia().getYear()))
                .toList());
    }

    @FXML
    private void salvar() {
        try {
            ContatoCobranca contato = servico.registrarContato(linha.contrato().getId(), campoResultado.getText());
            fechar();
            depoisDeRegistrar.accept(contato);
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Nao foi possivel salvar o contato: " + e.getMessage());
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
