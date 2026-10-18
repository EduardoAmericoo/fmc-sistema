package br.org.fmc.ui;

import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controla a tela de login (ui/login.fxml).
 * Só cuida da tela: pega o que foi digitado, pede ao ServicoAcesso e mostra o resultado.
 */
public class LoginController {

    private final ServicoAcesso servico = new ServicoAcesso();

    @FXML
    private TextField campoEmail;

    @FXML
    private PasswordField campoSenha;

    @FXML
    private Label mensagemErro;

    @FXML
    private void initialize() {
        esconderErro();
    }

    /** Botão Entrar (ou Enter). */
    @FXML
    private void entrar() {
        esconderErro();
        try {
            servico.login(campoEmail.getText(), campoSenha.getText());
            Navegador.mostrarSistema();
        } catch (RegraNegocioException e) {
            mostrarErro(e.getMessage());
            campoSenha.clear();
            campoSenha.requestFocus();
        } catch (SQLException e) {
            mostrarErro("Erro no banco de dados: " + e.getMessage());
        }
    }

    private void mostrarErro(String mensagem) {
        mensagemErro.setText(mensagem);
        mensagemErro.setVisible(true);
        mensagemErro.setManaged(true);
    }

    private void esconderErro() {
        mensagemErro.setText("");
        mensagemErro.setVisible(false);
        mensagemErro.setManaged(false);
    }
}
