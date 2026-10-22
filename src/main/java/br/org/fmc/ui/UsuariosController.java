package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.service.AcessoNegadoException;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Controla a tela "Usuários" (ui/usuarios.fxml).
 * Só cuida da tela: as regras (quem pode, e-mail repetido, permissões...) ficam no ServicoAcesso.
 */
public class UsuariosController {

    private final ServicoAcesso servico = new ServicoAcesso();

    /** Usuário com as permissões abertas nas caixinhas; null quando o cartão está fechado. */
    private Usuario selecionado;

    @FXML
    private TableView<Usuario> tabela;
    @FXML
    private TableColumn<Usuario, String> colunaNome;
    @FXML
    private TableColumn<Usuario, String> colunaEmail;
    @FXML
    private TableColumn<Usuario, String> colunaPerfil;
    @FXML
    private TableColumn<Usuario, String> colunaPermissoes;
    @FXML
    private TableColumn<Usuario, String> colunaSituacao;

    @FXML
    private ScrollPane ladoDireito;
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
    private VBox cartaoPermissoes;
    @FXML
    private Label tituloPermissoes;
    @FXML
    private Label dicaPermissoes;
    @FXML
    private VBox caixasPermissoes;
    @FXML
    private Label mensagemPermissoes;

    @FXML
    private void initialize() {
        esconder(aviso);
        colunaNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        colunaEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colunaPerfil.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerfil().getDescricao()));
        colunaPermissoes.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().temPermissoesPersonalizadas() ? "Personalizadas" : "Padrão do perfil"));
        colunaSituacao.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isAtivo() ? "Ativo" : "Desativado"));
        tabela.setPlaceholder(new Label("Nenhum usuário para mostrar."));

        campoPerfil.getItems().setAll(Perfil.values());
        campoPerfil.setValue(Perfil.SECRETARIA);
        Permissoes.mostrarSe(ladoDireito, Acao.GERENCIAR_USUARIOS);

        // Uma caixinha para cada ação do sistema; o userData guarda qual ação ela representa.
        for (Acao acao : Acao.values()) {
            CheckBox caixa = new CheckBox(acao.getDescricao());
            caixa.setUserData(acao);
            caixa.setWrapText(true);
            caixasPermissoes.getChildren().add(caixa);
        }
        tabela.getSelectionModel().selectedItemProperty().addListener((obs, antes, agora) -> {
            if (agora != null) {
                abrirPermissoes(agora);
            }
        });

        fecharPermissoes();
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
            mostrar(mensagem, "Usuário criado: " + novo.getNome() + ". O acesso já funciona com o e-mail "
                    + novo.getEmail() + " e a senha inicial.", false);
            limparCampos();
            carregarLista();
        } catch (RegraNegocioException e) {
            mostrar(mensagem, String.join("\n", e.getErros()), true);
        } catch (SQLException e) {
            mostrar(mensagem, "Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    /** Mostra as permissões do usuário escolhido na lista, já marcadas como estão hoje. */
    private void abrirPermissoes(Usuario usuario) {
        selecionado = usuario;
        tituloPermissoes.setText("Permissões de " + usuario.getNome());
        dicaPermissoes.setText("Perfil " + usuario.getPerfil().getDescricao() + ". "
                + (usuario.temPermissoesPersonalizadas()
                        ? "Esta pessoa tem permissões ajustadas pelo Administrador."
                        : "Esta pessoa segue o padrão do perfil.")
                + " Marque ou desmarque para ajustar só para ela.");
        for (Node item : caixasPermissoes.getChildren()) {
            CheckBox caixa = (CheckBox) item;
            caixa.setSelected(usuario.getPermissoes().contains((Acao) caixa.getUserData()));
        }
        esconder(mensagemPermissoes);
        cartaoPermissoes.setVisible(true);
        cartaoPermissoes.setManaged(true);
    }

    /** Botão "Salvar permissões". */
    @FXML
    private void salvarPermissoes() {
        Set<Acao> marcadas = EnumSet.noneOf(Acao.class);
        for (Node item : caixasPermissoes.getChildren()) {
            CheckBox caixa = (CheckBox) item;
            if (caixa.isSelected()) {
                marcadas.add((Acao) caixa.getUserData());
            }
        }
        aplicar(() -> servico.alterarPermissoes(selecionado.getId(), marcadas), "Permissões salvas.");
    }

    /** Botão "Voltar ao padrão do perfil". */
    @FXML
    private void restaurarPermissoes() {
        aplicar(() -> servico.restaurarPermissoesDoPerfil(selecionado.getId()),
                "Permissões de volta ao padrão do perfil.");
    }

    /** Botão "Fechar": esconde o cartão de permissões. */
    @FXML
    private void fecharPermissoes() {
        selecionado = null;
        tabela.getSelectionModel().clearSelection();
        cartaoPermissoes.setVisible(false);
        cartaoPermissoes.setManaged(false);
    }

    /** Uma alteração de permissões que pode dar erro de regra ou de banco. */
    private interface Alteracao {
        Usuario executar() throws SQLException;
    }

    private void aplicar(Alteracao alteracao, String sucesso) {
        if (selecionado == null) {
            return;
        }
        try {
            Usuario atualizado = alteracao.executar();
            carregarLista();
            // Selecionar de novo reabre o cartão (e limpa a mensagem), por isso a mensagem vem depois.
            tabela.getItems().stream()
                    .filter(u -> u.getId().equals(atualizado.getId()))
                    .findFirst()
                    .ifPresent(u -> tabela.getSelectionModel().select(u));
            mostrar(mensagemPermissoes, sucesso + " Valem a partir do próximo login da pessoa.", false);
        } catch (RegraNegocioException e) {
            mostrar(mensagemPermissoes, String.join("\n", e.getErros()), true);
        } catch (SQLException e) {
            mostrar(mensagemPermissoes, "Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    private void carregarLista() {
        try {
            tabela.getItems().setAll(servico.listarUsuarios());
        } catch (AcessoNegadoException e) {
            tabela.getItems().clear();
            ladoDireito.setVisible(false);
            ladoDireito.setManaged(false);
            aviso.setText("Somente o Administrador pode ver e criar usuários.");
            aviso.setVisible(true);
            aviso.setManaged(true);
        } catch (SQLException e) {
            mostrar(mensagem, "Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    private void limparCampos() {
        campoNome.clear();
        campoEmail.clear();
        campoSenha.clear();
        campoPerfil.setValue(Perfil.SECRETARIA);
    }

    private void mostrar(Label rotulo, String texto, boolean erro) {
        rotulo.setText(texto);
        rotulo.getStyleClass().setAll("label", erro ? "erro" : "sucesso");
        rotulo.setVisible(true);
        rotulo.setManaged(true);
    }

    private void limparMensagem() {
        esconder(mensagem);
    }

    private static void esconder(Label rotulo) {
        rotulo.setText("");
        rotulo.setVisible(false);
        rotulo.setManaged(false);
    }
}
