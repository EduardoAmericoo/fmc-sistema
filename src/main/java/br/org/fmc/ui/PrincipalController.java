package br.org.fmc.ui;

import br.org.fmc.service.ServicoSistema;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Controla a janela principal (ui/principal.fxml): o menu lateral à esquerda
 * e a tela escolhida no centro.
 *
 * Cada botão do menu tem userData com o nome da tela (ex.: "imoveis").
 * Repare que o controller não fala com o banco: ele pede ao service.
 */
public class PrincipalController {

    private final ServicoSistema servico = new ServicoSistema();

    @FXML
    private BorderPane raiz;

    @FXML
    private VBox menu;

    @FXML
    private Label statusBanco;

    @FXML
    private void initialize() {
        Navegador.configurar(raiz);
        abrir("painel");
        try {
            statusBanco.setText("Banco de dados conectado (schema v" + servico.versaoDoBanco() + ")");
        } catch (Exception e) {
            statusBanco.setText("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Clique em qualquer botão do menu. */
    @FXML
    private void abrirTela(ActionEvent evento) {
        Button botao = (Button) evento.getSource();
        abrir((String) botao.getUserData());
    }

    private void abrir(String tela) {
        Navegador.abrir(tela);
        for (Node item : menu.getChildren()) {
            item.getStyleClass().remove("ativo");
            if (tela.equals(item.getUserData())) {
                item.getStyleClass().add("ativo");
            }
        }
    }
}
