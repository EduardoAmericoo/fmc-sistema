package br.org.fmc.ui;

import br.org.fmc.model.Usuario;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.ServicoSistema;
import br.org.fmc.service.Sessao;
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
 * Repare que o controller não fala com o banco: ele pede aos services.
 */
public class PrincipalController {

    private final ServicoSistema servicoSistema = new ServicoSistema();
    private final ServicoAcesso servicoAcesso = new ServicoAcesso();

    @FXML
    private BorderPane raiz;

    @FXML
    private VBox menu;

    @FXML
    private Label usuarioLogado;

    @FXML
    private Label statusBanco;

    @FXML
    private void initialize() {
        Navegador.configurar(raiz);
        if (Sessao.estaLogado()) {
            Usuario u = Sessao.usuario();
            usuarioLogado.setText(u.getNome() + "\n" + u.getPerfil().getDescricao());
        }
        abrir("painel");
        try {
            statusBanco.setText("Banco de dados conectado (schema v" + servicoSistema.versaoDoBanco() + ")");
        } catch (Exception e) {
            statusBanco.setText("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Clique em qualquer botão do menu que abre uma tela. */
    @FXML
    private void abrirTela(ActionEvent evento) {
        Button botao = (Button) evento.getSource();
        abrir((String) botao.getUserData());
    }

    /** Botão Sair: encerra a sessão e volta para o login. */
    @FXML
    private void sair() {
        servicoAcesso.sair();
        Navegador.mostrarLogin();
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
