package br.org.fmc.ui;

import br.org.fmc.service.ServicoSistema;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controla a tela principal (ui/principal.fxml).
 *
 * Por enquanto só mostra se o banco está funcionando. Cada funcionalidade do
 * MVP vai ganhar a sua própria tela e o seu próprio controller neste pacote.
 * Repare que o controller não fala com o banco: ele pede ao service.
 */
public class PrincipalController {

    private final ServicoSistema servico = new ServicoSistema();

    @FXML
    private Label statusBanco;

    @FXML
    private void initialize() {
        try {
            statusBanco.setText("Banco de dados conectado (schema v" + servico.versaoDoBanco() + ")");
        } catch (Exception e) {
            statusBanco.setText("Erro no banco de dados: " + e.getMessage());
        }
    }
}
