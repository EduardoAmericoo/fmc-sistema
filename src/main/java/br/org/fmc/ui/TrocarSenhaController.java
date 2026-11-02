package br.org.fmc.ui;

import br.org.fmc.model.RegraSenha;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.Sessao;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela de troca de senha (ui/trocar-senha.fxml), BOARD-62.
 * Só cuida da tela: as regras (senha atual certa, senha forte) ficam no ServicoAcesso.
 */
public class TrocarSenhaController {

    private final ServicoAcesso servico = new ServicoAcesso();
    private final boolean obrigatoria = Navegador.trocaObrigatoria();

    @FXML
    private Label titulo;
    @FXML
    private Label explicacao;
    @FXML
    private Label rotuloAtual;
    @FXML
    private PasswordField campoAtual;
    @FXML
    private PasswordField campoNova;
    @FXML
    private PasswordField campoConfirmacao;
    @FXML
    private VBox itensRegra;
    @FXML
    private Label mensagemErro;
    @FXML
    private Button botaoVoltar;

    @FXML
    private void initialize() {
        String nome = Sessao.usuario().getNome().split(" ")[0];
        if (obrigatoria) {
            titulo.setText("Olá, " + nome + "! Crie a sua senha");
            explicacao.setText("Você entrou com uma senha provisória. Para continuar, escolha uma senha só sua.");
            rotuloAtual.setText("Senha provisória (a que você recebeu)");
            botaoVoltar.setText("Sair");
        } else {
            titulo.setText("Trocar minha senha");
            explicacao.setText("Escolha uma senha nova. Ela passa a valer já no próximo login.");
            botaoVoltar.setText("Cancelar");
        }

        // Um item para cada regra, que fica verde quando a senha nova cumpre.
        for (RegraSenha.Item item : RegraSenha.Item.values()) {
            Label rotulo = new Label(item.getDescricao(), new FontIcon("fth-check-circle"));
            rotulo.setUserData(item);
            rotulo.getStyleClass().add("item-regra");
            HBox linha = new HBox(rotulo);
            linha.setAlignment(Pos.CENTER_LEFT);
            itensRegra.getChildren().add(linha);
        }
        campoNova.textProperty().addListener((obs, antes, agora) -> atualizarRegra(agora));
        atualizarRegra("");
        esconderErro();
        campoAtual.requestFocus();
    }

    private void atualizarRegra(String senha) {
        for (var linha : itensRegra.getChildren()) {
            Label rotulo = (Label) ((HBox) linha).getChildren().get(0);
            boolean ok = ((RegraSenha.Item) rotulo.getUserData()).atendidoPor(senha);
            rotulo.getStyleClass().remove("ok");
            if (ok) {
                rotulo.getStyleClass().add("ok");
            }
        }
    }

    /** Botão "Salvar senha nova" (ou Enter). */
    @FXML
    private void salvar() {
        esconderErro();
        try {
            servico.trocarSenha(campoAtual.getText(), campoNova.getText(), campoConfirmacao.getText());
            Navegador.mostrarSistema();
        } catch (RegraNegocioException e) {
            mostrarErro(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErro("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** "Sair" (troca obrigatória: volta ao login) ou "Cancelar" (volta ao sistema). */
    @FXML
    private void voltar() {
        if (obrigatoria) {
            servico.sair();
            Navegador.mostrarLogin();
        } else {
            Navegador.mostrarSistema();
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
