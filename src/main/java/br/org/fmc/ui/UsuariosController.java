package br.org.fmc.ui;

import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.service.AcessoNegadoException;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import java.sql.SQLException;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Controla a tela "Usuários" (ui/usuarios.fxml).
 * Só cuida da tela: as regras (quem pode, e-mail repetido...) ficam no ServicoAcesso.
 */
public class UsuariosController {

    private final ServicoAcesso servico = new ServicoAcesso();

    @FXML
    private TableView<Usuario> tabela;
    @FXML
    private TableColumn<Usuario, String> colunaNome;
    @FXML
    private TableColumn<Usuario, String> colunaEmail;
    @FXML
    private TableColumn<Usuario, String> colunaPerfil;
    @FXML
    private TableColumn<Usuario, String> colunaSituacao;

    @FXML
    private VBox formulario;
    @FXML
    private TextField campoNome;
    @FXML
    private TextField campoEmail;
    @FXML
    private ComboBox<Perfil> campoPerfil;
    @FXML
    private PasswordField campoSenha;
    @FXML
    private Label mensagem;
    @FXML
    private Label aviso;

    @FXML
    private void initialize() {
        aviso.setVisible(false);
        aviso.setManaged(false);
        colunaNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        colunaEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colunaPerfil.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerfil().getDescricao()));
        colunaSituacao.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAtivo() ? "Ativo" : "Desativado"));
        tabela.setPlaceholder(new Label("Nenhum usuário para mostrar."));

        campoPerfil.getItems().setAll(Perfil.values());
        campoPerfil.setValue(Perfil.SECRETARIA);

        limparMensagem();
        carregarLista();
    }

    /** Botão "Criar usuário". */
    @FXML
    private void criar() {
        limparMensagem();
        try {
            Usuario novo = servico.criarUsuario(campoNome.getText(), campoEmail.getText(),
                    campoPerfil.getValue(), campoSenha.getText());
            mostrar("Usuário criado: " + novo.getNome() + ". O acesso já funciona com o e-mail "
                    + novo.getEmail() + " e a senha inicial.", false);
            limparCampos();
            carregarLista();
        } catch (RegraNegocioException e) {
            mostrar(String.join("\n", e.getErros()), true);
        } catch (SQLException e) {
            mostrar("Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    private void carregarLista() {
        try {
            tabela.getItems().setAll(servico.listarUsuarios());
        } catch (AcessoNegadoException e) {
            tabela.getItems().clear();
            formulario.setVisible(false);
            formulario.setManaged(false);
            aviso.setText("Somente o Administrador pode ver e criar usuários.");
            aviso.setVisible(true);
            aviso.setManaged(true);
        } catch (SQLException e) {
            mostrar("Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    private void limparCampos() {
        campoNome.clear();
        campoEmail.clear();
        campoSenha.clear();
        campoPerfil.setValue(Perfil.SECRETARIA);
    }

    private void mostrar(String texto, boolean erro) {
        mensagem.setText(texto);
        mensagem.getStyleClass().setAll("label", erro ? "erro" : "sucesso");
        mensagem.setVisible(true);
        mensagem.setManaged(true);
    }

    private void limparMensagem() {
        mensagem.setText("");
        mensagem.setVisible(false);
        mensagem.setManaged(false);
    }
}
