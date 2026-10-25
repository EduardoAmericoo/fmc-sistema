package br.org.fmc.ui;

import br.org.fmc.model.Usuario;
import br.org.fmc.service.FalhaLogin;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import java.sql.SQLException;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela de login (ui/login.fxml).
 * Só cuida da tela: pega o que foi digitado, pede ao ServicoAcesso e mostra cada aviso
 * embaixo do campo certo. As regras (e-mail válido, limite de tentativas) ficam no service.
 */
public class LoginController {

    /** Onde o sistema lembra o último e-mail que entrou (no registro do Windows do usuário). */
    private static final Preferences PREFERENCIAS = Preferences.userNodeForPackage(LoginController.class);
    private static final String ULTIMO_EMAIL = "ultimoEmail";

    private final ServicoAcesso servico = new ServicoAcesso();

    @FXML
    private TextField campoEmail;
    @FXML
    private Label erroEmail;
    @FXML
    private PasswordField campoSenha;
    @FXML
    private TextField campoSenhaVisivel;
    @FXML
    private Button botaoOlho;
    @FXML
    private FontIcon iconeOlho;
    @FXML
    private Label avisoCapsLock;
    @FXML
    private Label erroSenha;
    @FXML
    private HBox avisoGeral;
    @FXML
    private Label textoAvisoGeral;

    @FXML
    private void initialize() {
        // As duas caixas de senha têm sempre o mesmo texto; só uma aparece por vez.
        campoSenhaVisivel.textProperty().bindBidirectional(campoSenha.textProperty());
        mostrar(campoSenhaVisivel, false);
        botaoOlho.setTooltip(new Tooltip("Mostrar senha"));

        // O aviso de um campo some assim que a pessoa começa a corrigir.
        campoEmail.textProperty().addListener((obs, antes, agora) -> {
            limpar(campoEmail, erroEmail);
            mostrar(avisoGeral, false);
        });
        campoSenha.textProperty().addListener((obs, antes, agora) -> {
            if (!agora.isEmpty()) { // a senha errada é apagada pelo sistema: isso não conta como "corrigir"
                limpar(campoSenha, erroSenha);
                mostrar(avisoGeral, false);
            }
        });

        // Caps Lock: confere ao entrar no campo de senha e a cada tecla.
        for (TextInputControl senha : new TextInputControl[] {campoSenha, campoSenhaVisivel}) {
            senha.focusedProperty().addListener((obs, antes, focado) -> conferirCapsLock());
            senha.setOnKeyReleased(e -> conferirCapsLock());
        }
        campoEmail.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.CAPS) {
                conferirCapsLock();
            }
        });

        limparTudo();
        mostrar(avisoCapsLock, false);

        // Lembra o último e-mail: a pessoa só digita a senha.
        String ultimo = PREFERENCIAS.get(ULTIMO_EMAIL, "");
        campoEmail.setText(ultimo);
        Platform.runLater(() -> (ultimo.isEmpty() ? campoEmail : campoSenha).requestFocus());
    }

    /** Botão Entrar (ou Enter). */
    @FXML
    private void entrar() {
        limparTudo();
        try {
            Usuario usuario = servico.login(campoEmail.getText(), campoSenha.getText());
            PREFERENCIAS.put(ULTIMO_EMAIL, usuario.getEmail());
            if (usuario.temSenhaProvisoria()) {
                Navegador.mostrarTrocaDeSenha(true); // 1º acesso: precisa criar a própria senha (BOARD-62)
            } else {
                Navegador.mostrarSistema();
            }
        } catch (FalhaLogin e) {
            mostrarFalha(e);
        } catch (RegraNegocioException e) {
            mostrarGeral(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarGeral("Não foi possível conferir o acesso no banco de dados. Tente de novo.");
        }
    }

    private void mostrarFalha(FalhaLogin e) {
        String email = e.mensagem(FalhaLogin.Campo.EMAIL);
        String senha = e.mensagem(FalhaLogin.Campo.SENHA);
        String geral = e.mensagem(FalhaLogin.Campo.GERAL);
        if (email != null) {
            marcar(campoEmail, erroEmail, email);
        }
        if (senha != null) {
            campoSenha.clear(); // primeiro limpa (isso apagaria o aviso), depois marca
            marcar(campoSenha, erroSenha, senha);
        }
        if (geral != null) {
            mostrarGeral(geral);
        }
        // O cursor vai para o primeiro campo com problema.
        (email != null ? campoEmail : senhaAtual()).requestFocus();
    }

    /** Botão do olho: mostra ou esconde a senha. */
    @FXML
    private void alternarSenha() {
        boolean mostrarTexto = campoSenha.isVisible();
        mostrar(campoSenha, !mostrarTexto);
        mostrar(campoSenhaVisivel, mostrarTexto);
        iconeOlho.setIconLiteral(mostrarTexto ? "fth-eye-off" : "fth-eye");
        String dica = mostrarTexto ? "Esconder senha" : "Mostrar senha";
        botaoOlho.setAccessibleText(dica);
        botaoOlho.getTooltip().setText(dica);
        TextInputControl atual = senhaAtual();
        atual.requestFocus();
        atual.positionCaret(atual.getText().length());
    }

    private TextInputControl senhaAtual() {
        return campoSenha.isVisible() ? campoSenha : campoSenhaVisivel;
    }

    private void conferirCapsLock() {
        mostrar(avisoCapsLock, (campoSenha.isFocused() || campoSenhaVisivel.isFocused()) && capsLockLigado());
    }

    /** O JavaFX não informa o Caps Lock; quem sabe é a parte antiga do Java (AWT). */
    private static boolean capsLockLigado() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK);
        } catch (UnsupportedOperationException e) { // inclui o HeadlessException
            return false; // sistema que não informa: simplesmente não mostra o aviso
        }
    }

    private void marcar(TextInputControl campo, Label aviso, String texto) {
        aviso.setText(texto);
        mostrar(aviso, true);
        if (!campo.getStyleClass().contains("campo-erro")) {
            campo.getStyleClass().add("campo-erro");
        }
        if (campo == campoSenha && !campoSenhaVisivel.getStyleClass().contains("campo-erro")) {
            campoSenhaVisivel.getStyleClass().add("campo-erro");
        }
    }

    private void limpar(TextInputControl campo, Label aviso) {
        mostrar(aviso, false);
        campo.getStyleClass().remove("campo-erro");
        if (campo == campoSenha) {
            campoSenhaVisivel.getStyleClass().remove("campo-erro");
        }
    }

    private void limparTudo() {
        limpar(campoEmail, erroEmail);
        limpar(campoSenha, erroSenha);
        mostrar(avisoGeral, false);
    }

    private void mostrarGeral(String texto) {
        textoAvisoGeral.setText(texto);
        mostrar(avisoGeral, true);
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }
}
