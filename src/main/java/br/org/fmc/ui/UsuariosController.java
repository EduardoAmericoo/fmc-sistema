package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import br.org.fmc.service.AcessoNegadoException;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.Sessao;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela "Usuários" (ui/usuarios.fxml).
 * Só cuida da tela: as regras (quem pode, e-mail repetido, permissões...) ficam no ServicoAcesso.
 */
public class UsuariosController {

    private final ServicoAcesso servico = new ServicoAcesso();

    /** Usuário com as permissões abertas na janela; null quando ela está fechada. */
    private Usuario selecionado;

    @FXML
    private Label contagem;
    @FXML
    private Button botaoNovo;
    @FXML
    private TableView<Usuario> tabela;
    @FXML
    private TableColumn<Usuario, Usuario> colunaNome;
    @FXML
    private TableColumn<Usuario, Usuario> colunaPerfil;
    @FXML
    private TableColumn<Usuario, Usuario> colunaPermissoes;
    @FXML
    private TableColumn<Usuario, Usuario> colunaSituacao;
    @FXML
    private TableColumn<Usuario, Usuario> colunaAcoes;

    @FXML
    private HBox toast;
    @FXML
    private Label textoToast;

    @FXML
    private StackPane veuNovo;
    @FXML
    private HBox avisoNovo;
    @FXML
    private Label textoAvisoNovo;
    @FXML
    private TextField campoNome;
    @FXML
    private TextField campoEmail;
    @FXML
    private ComboBox<Perfil> campoPerfil;
    @FXML
    private PasswordField campoSenha;

    @FXML
    private StackPane veuPermissoes;
    @FXML
    private Label tituloPermissoes;
    @FXML
    private Label dicaPermissoes;
    @FXML
    private HBox avisoPermissoes;
    @FXML
    private Label textoAvisoPermissoes;
    @FXML
    private VBox caixasPermissoes;

    @FXML
    private void initialize() {
        montarTabela();
        campoPerfil.getItems().setAll(Perfil.values());
        // Cada botão só aparece para quem tem a permissão dele.
        Permissoes.mostrarSe(botaoNovo, Acao.CRIAR_USUARIO);
        colunaAcoes.setVisible(Sessao.pode(Acao.EDITAR_PERMISSOES));
        montarCaixinhas();

        // Esc fecha a janela aberta.
        veuNovo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fecharNovo();
            }
        });
        veuPermissoes.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fecharPermissoes();
            }
        });

        esconder(veuNovo);
        esconder(veuPermissoes);
        esconder(toast);
        carregarLista();
    }

    private void montarTabela() {
        // Coluna "Usuário": círculo com as iniciais, nome e, embaixo, o e-mail.
        colunaNome.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaNome.setCellFactory(c -> celula(u -> {
            Label avatar = new Label(Iniciais.de(u.getNome()));
            avatar.getStyleClass().add("avatar");
            Label nome = new Label(u.getNome());
            nome.getStyleClass().add("celula-nome");
            Label email = new Label(u.getEmail());
            email.getStyleClass().add("celula-detalhe");
            HBox caixa = new HBox(12, avatar, new VBox(2, nome, email));
            caixa.setAlignment(Pos.CENTER_LEFT);
            return caixa;
        }));
        colunaPerfil.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaPerfil.setCellFactory(c -> celula(u -> selo(u.getPerfil().getDescricao(), "selo-neutro")));
        colunaPermissoes.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaPermissoes.setCellFactory(c -> celula(u -> u.temPermissoesPersonalizadas()
                ? selo("Personalizadas", "selo-info")
                : selo("Padrão do perfil", "selo-neutro")));
        colunaSituacao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaSituacao.setCellFactory(c -> celula(u -> u.isAtivo()
                ? selo("Ativo", "selo-sucesso")
                : selo("Desativado", "selo-neutro")));

        // Coluna de ações: botão para ajustar as permissões.
        colunaAcoes.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAcoes.setCellFactory(c -> celula(u -> {
            Button ajustar = new Button(null, new FontIcon("fth-sliders"));
            ajustar.getStyleClass().add("botao-icone");
            ajustar.setTooltip(new Tooltip("Ajustar permissões de " + u.getNome()));
            ajustar.setOnAction(e -> abrirPermissoes(u));
            return ajustar;
        }));

        // Clicar na linha também abre as permissões (para quem pode ajustá-las).
        tabela.setRowFactory(t -> {
            TableRow<Usuario> linha = new TableRow<>();
            linha.setOnMouseClicked(e -> {
                if (Sessao.pode(Acao.EDITAR_PERMISSOES) && !linha.isEmpty()) {
                    abrirPermissoes(linha.getItem());
                }
            });
            return linha;
        });
        tabela.setPlaceholder(vazio("fth-users", "Nenhum usuário para mostrar", ""));
    }

    /**
     * Uma caixinha para cada ação, agrupadas por tela (Imóveis, Inquilinos...).
     * O userData de cada caixinha guarda a ação que ela representa.
     * Marcar "criar" ou "editar" marca junto o "ver" da tela; desmarcar o "ver" desmarca o resto da tela.
     */
    private void montarCaixinhas() {
        String telaAtual = null;
        for (Acao acao : Acao.values()) {
            if (!acao.getTela().equals(telaAtual)) {
                telaAtual = acao.getTela();
                Label grupo = new Label(telaAtual);
                grupo.getStyleClass().add("grupo-permissao");
                grupo.setMaxWidth(Double.MAX_VALUE);
                caixasPermissoes.getChildren().add(grupo);
            }
            CheckBox caixa = new CheckBox(acao.getDescricao());
            caixa.setUserData(acao);
            caixa.setWrapText(true);
            caixa.getStyleClass().add("item-permissao");
            caixa.setMaxWidth(Double.MAX_VALUE);
            caixa.selectedProperty().addListener((obs, antes, marcada) -> manterRequisitos(acao, marcada));
            caixasPermissoes.getChildren().add(caixa);
        }
    }

    private void manterRequisitos(Acao acao, boolean marcada) {
        for (CheckBox outra : caixinhas()) {
            Acao a = (Acao) outra.getUserData();
            if (marcada && a == acao.getRequisito()) {
                outra.setSelected(true);
            } else if (!marcada && a.getRequisito() == acao) {
                outra.setSelected(false);
            }
        }
    }

    private java.util.List<CheckBox> caixinhas() {
        return caixasPermissoes.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .toList();
    }

    /** Célula de tabela que desenha um componente a partir do usuário da linha. */
    private static TableCell<Usuario, Usuario> celula(java.util.function.Function<Usuario, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Usuario u, boolean vazio) {
                super.updateItem(u, vazio);
                setGraphic(vazio || u == null ? null : desenho.apply(u));
            }
        };
    }

    private static Label selo(String texto, String tipo) {
        Label selo = new Label(texto);
        selo.getStyleClass().addAll("selo", tipo);
        return selo;
    }

    private static VBox vazio(String icone, String titulo, String dica) {
        Label rotulo = new Label(titulo);
        rotulo.getStyleClass().add("tabela-vazia-titulo");
        Label texto = new Label(dica);
        texto.getStyleClass().add("texto");
        VBox caixa = new VBox(8, new FontIcon(icone), rotulo, texto);
        caixa.setAlignment(Pos.CENTER);
        caixa.getStyleClass().add("tabela-vazia");
        return caixa;
    }

    /** Botão "Novo usuário": abre a janela vazia. */
    @FXML
    private void novo() {
        campoNome.clear();
        campoEmail.clear();
        campoSenha.clear();
        campoPerfil.setValue(Perfil.SECRETARIA);
        esconder(avisoNovo);
        Navegador.mostrarJanela(veuNovo);
        campoNome.requestFocus();
    }

    @FXML
    private void fecharNovo() {
        Navegador.fecharJanela(veuNovo);
    }

    /** Botão "Criar usuário". */
    @FXML
    private void criar() {
        try {
            Usuario novo = servico.criarUsuario(campoNome.getText(), campoEmail.getText(),
                    campoPerfil.getValue(), campoSenha.getText());
            fecharNovo();
            carregarLista();
            mostrarToast("Usuário criado: " + novo.getNome() + ". O acesso já funciona.");
        } catch (RegraNegocioException e) {
            mostrarAviso(avisoNovo, textoAvisoNovo, String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarAviso(avisoNovo, textoAvisoNovo, "Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Abre a janela com as permissões do usuário, já marcadas como estão hoje. */
    private void abrirPermissoes(Usuario usuario) {
        selecionado = usuario;
        tituloPermissoes.setText("Permissões de " + usuario.getNome());
        dicaPermissoes.setText("Perfil " + usuario.getPerfil().getDescricao() + ". "
                + (usuario.temPermissoesPersonalizadas()
                        ? "Esta pessoa tem permissões ajustadas pelo Administrador."
                        : "Esta pessoa segue o padrão do perfil."));
        for (CheckBox caixa : caixinhas()) {
            caixa.setSelected(usuario.getPermissoes().contains((Acao) caixa.getUserData()));
        }
        esconder(avisoPermissoes);
        Navegador.mostrarJanela(veuPermissoes);
    }

    @FXML
    private void fecharPermissoes() {
        selecionado = null;
        tabela.getSelectionModel().clearSelection();
        Navegador.fecharJanela(veuPermissoes);
    }

    /** Botão "Salvar permissões". */
    @FXML
    private void salvarPermissoes() {
        Set<Acao> marcadas = EnumSet.noneOf(Acao.class);
        for (CheckBox caixa : caixinhas()) {
            if (caixa.isSelected()) {
                marcadas.add((Acao) caixa.getUserData());
            }
        }
        long id = selecionado.getId();
        aplicar(() -> servico.alterarPermissoes(id, marcadas), "Permissões salvas");
    }

    /** Botão "Voltar ao padrão do perfil". */
    @FXML
    private void restaurarPermissoes() {
        long id = selecionado.getId();
        aplicar(() -> servico.restaurarPermissoesDoPerfil(id), "Permissões de volta ao padrão do perfil");
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
            fecharPermissoes();
            carregarLista();
            mostrarToast(sucesso + ": " + atualizado.getNome() + ". Valem a partir do próximo login.");
        } catch (RegraNegocioException e) {
            mostrarAviso(avisoPermissoes, textoAvisoPermissoes, String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarAviso(avisoPermissoes, textoAvisoPermissoes, "Erro no banco de dados: " + e.getMessage());
        }
    }

    private void carregarLista() {
        try {
            tabela.getItems().setAll(servico.listarUsuarios());
            int total = tabela.getItems().size();
            contagem.setText((total == 1 ? "1 usuário" : total + " usuários")
                    + (Sessao.pode(Acao.EDITAR_PERMISSOES) ? " · Clique em um usuário para ajustar as permissões." : ""));
        } catch (AcessoNegadoException e) {
            // Quem não pode ver a lista de usuários vê só o aviso, no lugar dela.
            tabela.getItems().clear();
            contagem.setText("O seu perfil não tem acesso à lista de usuários.");
            tabela.setPlaceholder(vazio("fth-lock", "Acesso restrito",
                    "O seu perfil não tem acesso à lista de usuários."));
        } catch (SQLException e) {
            contagem.setText("Erro no banco de dados: " + e.getMessage());
        }
    }

    private static void mostrarAviso(HBox aviso, Label texto, String mensagem) {
        texto.setText(mensagem);
        aviso.setVisible(true);
        aviso.setManaged(true);
    }

    /** Aviso rápido no canto da tela, que some sozinho depois de 3 segundos. */
    private void mostrarToast(String texto) {
        textoToast.setText(texto);
        toast.setVisible(true);
        toast.setManaged(true);
        PauseTransition espera = new PauseTransition(Duration.seconds(3));
        espera.setOnFinished(e -> esconder(toast));
        espera.play();
    }

    private static void esconder(Node componente) {
        componente.setVisible(false);
        componente.setManaged(false);
    }
}
