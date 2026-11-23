package br.org.fmc.ui;

import br.org.fmc.model.Tela;
import br.org.fmc.model.Usuario;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.ServicoSistema;
import br.org.fmc.service.Sessao;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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
    private HBox cartaoUsuario;

    @FXML
    private Label avatar;

    @FXML
    private Label usuarioNome;

    @FXML
    private Label usuarioPerfil;

    @FXML
    private void initialize() {
        Navegador.configurar(raiz, this::abrir);
        if (Sessao.estaLogado()) {
            Usuario u = Sessao.usuario();
            avatar.setText(Iniciais.de(u.getNome()));
            usuarioNome.setText(u.getNome());
            usuarioPerfil.setText(u.getPerfil().getDescricao());
        }
        esconderTelasSemPermissao();
        abrir("painel");
        String statusBanco;
        try {
            statusBanco = "Banco de dados conectado (schema v" + servicoSistema.versaoDoBanco() + ")";
        } catch (Exception e) {
            statusBanco = Falha.doBanco(e);
        }
        Tooltip.install(cartaoUsuario, new Tooltip(statusBanco));
    }

    /** Clique em qualquer botão do menu que abre uma tela. */
    @FXML
    private void abrirTela(ActionEvent evento) {
        Button botao = (Button) evento.getSource();
        abrir((String) botao.getUserData());
    }

    /** Botão da chave, no cartão do usuário: troca a própria senha (BOARD-62). */
    @FXML
    private void trocarSenha() {
        Navegador.mostrarTrocaDeSenha(false);
    }

    /** Botão Sair: encerra a sessão e volta para o login. */
    @FXML
    private void sair() {
        servicoAcesso.sair();
        Navegador.mostrarLogin();
    }

    /**
     * Cada botão do menu só aparece se o perfil de quem entrou pode abrir a tela dele.
     * A seção (Operação, Cadastros, Gestão) some quando nenhuma tela dela aparece.
     */
    private void esconderTelasSemPermissao() {
        for (Node secao : menu.getChildren()) {
            boolean algumaVisivel = false;
            for (Node item : itensDa(secao)) {
                if (item.getUserData() instanceof String arquivo) {
                    boolean pode = Tela.porArquivo(arquivo).map(Sessao::podeAbrir).orElse(false);
                    item.setVisible(pode);
                    item.setManaged(pode);
                    algumaVisivel |= pode;
                }
            }
            secao.setVisible(algumaVisivel);
            secao.setManaged(algumaVisivel);
        }
    }

    private void abrir(String tela) {
        Navegador.abrir(tela);
        for (Node secao : menu.getChildren()) {
            for (Node item : itensDa(secao)) {
                item.getStyleClass().remove("ativo");
                if (tela.equals(item.getUserData())) {
                    item.getStyleClass().add("ativo");
                }
            }
        }
    }

    /** Os botões de uma seção do menu (o título da seção fica de fora: não tem userData). */
    private static List<Node> itensDa(Node secao) {
        return secao instanceof Pane caixa ? caixa.getChildren() : List.of(secao);
    }
}
