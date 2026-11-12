package br.org.fmc.ui;

import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoRecuperacaoSenha;
import br.org.fmc.util.Correio;
import java.awt.Desktop;
import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Janela "Esqueci a senha": passo 1 pede o e-mail e manda o código; passo 2 recebe o código
 * e a senha nova. Quando dá certo, volta ao login com o e-mail preenchido.
 */
public class RecuperarSenhaController {

    private final ServicoRecuperacaoSenha servico = new ServicoRecuperacaoSenha();
    private Consumer<String> depoisDeTrocar;
    private boolean noPassoDoCodigo;

    @FXML private StackPane veu;
    @FXML private Label subtitulo;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private VBox passoEmail;
    @FXML private TextField campoEmail;
    @FXML private VBox passoCodigo;
    @FXML private Label textoEnviado;
    @FXML private HBox avisoSimulado;
    @FXML private Label textoSimulado;
    @FXML private TextField campoCodigo;
    @FXML private PasswordField campoSenha;
    @FXML private PasswordField campoConfirmacao;
    @FXML private Button botaoReenviar;
    @FXML private Button botaoPrincipal;

    /** Abre a janela; depoisDeTrocar recebe o e-mail, para o login já vir preenchido. */
    public static void abrir(String emailDigitado, Consumer<String> depoisDeTrocar) {
        FXMLLoader carregador = new FXMLLoader(
                RecuperarSenhaController.class.getResource("/br/org/fmc/ui/recuperar-senha.fxml"));
        try {
            Node janela = carregador.load();
            RecuperarSenhaController controle = carregador.getController();
            controle.depoisDeTrocar = depoisDeTrocar;
            controle.campoEmail.setText(emailDigitado == null ? "" : emailDigitado.trim());
            Navegador.mostrarJanela(janela);
            controle.campoEmail.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir recuperar-senha.fxml", e);
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
        // o código aceita só números, até 6
        campoCodigo.textProperty().addListener((obs, antes, agora) -> {
            String limpo = agora.replaceAll("\\D", "");
            if (limpo.length() > 6) {
                limpo = limpo.substring(0, 6);
            }
            if (!limpo.equals(agora)) {
                campoCodigo.setText(limpo);
            }
        });
        irParaEmail();
        if (!servico.disponivel()) {
            mostrarErros("A recuperação por e-mail não está configurada neste computador. "
                    + "Peça uma senha provisória ao Administrador.");
            botaoPrincipal.setDisable(true);
        }
    }

    private void irParaEmail() {
        noPassoDoCodigo = false;
        subtitulo.setText("Passo 1 de 2 · Receba um código no seu e-mail");
        mostrar(passoEmail, true);
        mostrar(passoCodigo, false);
        botaoPrincipal.setText("Enviar código");
    }

    private void irParaCodigo(String mensagem) {
        noPassoDoCodigo = true;
        subtitulo.setText("Passo 2 de 2 · Digite o código e crie a senha nova");
        textoEnviado.setText(mensagem + " Confira também a caixa de spam.");
        mostrar(passoEmail, false);
        mostrar(passoCodigo, true);
        Correio correio = servico.correio();
        boolean simulado = correio != null && correio.simulado();
        mostrar(avisoSimulado, simulado);
        if (simulado) {
            textoSimulado.setText("Modo de teste: o e-mail não sai de verdade. Ele foi salvo como arquivo na "
                    + "caixa de saída do sistema.");
        }
        botaoPrincipal.setText("Trocar senha");
        campoCodigo.requestFocus();
    }

    @FXML
    private void continuar() {
        mostrar(avisoErros, false);
        try {
            if (!noPassoDoCodigo) {
                irParaCodigo(servico.pedirCodigo(campoEmail.getText()));
            } else {
                servico.redefinir(campoEmail.getText(), campoCodigo.getText(), campoSenha.getText(),
                        campoConfirmacao.getText());
                String email = campoEmail.getText().trim();
                fechar();
                depoisDeTrocar.accept(email);
            }
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Não foi possível acessar o banco de dados agora.");
        }
    }

    @FXML
    private void reenviar() {
        mostrar(avisoErros, false);
        try {
            irParaCodigo(servico.pedirCodigo(campoEmail.getText()));
            campoCodigo.clear();
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Não foi possível acessar o banco de dados agora.");
        }
    }

    /** Só no modo de teste: abre a pasta com os e-mails salvos. */
    @FXML
    private void abrirCaixaDeSaida() {
        if (servico.correio() instanceof Correio.Simulado simulado) {
            try {
                java.nio.file.Files.createDirectories(simulado.pasta());
                Desktop.getDesktop().open(simulado.pasta().toFile());
            } catch (IOException | UnsupportedOperationException e) {
                mostrarErros("Abra a pasta " + simulado.pasta() + " para ver o e-mail.");
            }
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
