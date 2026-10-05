package br.org.fmc.ui;

import br.org.fmc.db.Database;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controla a tela principal (ui/principal.fxml).
 *
 * Por enquanto só mostra se o banco está funcionando. Cada funcionalidade do
 * MVP vai ganhar a sua própria tela e o seu próprio controller neste pacote.
 */
public class PrincipalController {

    @FXML
    private Label statusBanco;

    @FXML
    private void initialize() {
        try (Connection conexao = Database.conectar();
             Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("SELECT valor FROM sistema WHERE chave = 'versao_schema'")) {
            String versao = rs.next() ? rs.getString(1) : "?";
            statusBanco.setText("Banco de dados conectado (schema v" + versao + ")");
        } catch (Exception e) {
            statusBanco.setText("Erro no banco de dados: " + e.getMessage());
        }
    }
}
