package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Inquilino;
import br.org.fmc.service.DadosInquilino;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoInquilinos;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela "Inquilinos" (ui/inquilinos.fxml).
 * Só cuida da tela: as regras (CPF válido, CPF repetido, quem pode) ficam no ServicoInquilinos.
 */
public class InquilinosController {

    private final ServicoInquilinos servico = new ServicoInquilinos();

    /** Inquilino aberto na janela para editar; null quando é um cadastro novo. */
    private Inquilino emEdicao;

    /** Todos os inquilinos carregados do banco; a tabela mostra só os que atendem à busca (BOARD-56). */
    private List<Inquilino> todos = List.of();

    /** O que a tabela mostra quando não há cadastro nenhum, e quando a busca não encontra ninguém. */
    private VBox vazioSemCadastro;
    private VBox vazioBusca;
    private Label tituloVazioBusca;

    /** Quem está logado pode editar inquilinos? (mostra o lápis e abre a janela ao clicar na linha) */
    private boolean podeEditar;

    @FXML
    private Label contagem;
    @FXML
    private Button botaoNovo;
    @FXML
    private TextField campoBusca;
    @FXML
    private TableView<Inquilino> tabela;
    @FXML
    private TableColumn<Inquilino, Inquilino> colunaNome;
    @FXML
    private TableColumn<Inquilino, String> colunaCpf;
    @FXML
    private TableColumn<Inquilino, String> colunaTelefone;
    @FXML
    private TableColumn<Inquilino, String> colunaEstadoCivil;
    @FXML
    private TableColumn<Inquilino, String> colunaRenda;
    @FXML
    private TableColumn<Inquilino, Inquilino> colunaAcoes;

    @FXML
    private HBox toast;
    @FXML
    private Label textoToast;

    @FXML
    private StackPane veu;
    @FXML
    private Label tituloJanela;
    @FXML
    private HBox avisoErros;
    @FXML
    private Label textoErros;
    @FXML
    private TextField campoNome;
    @FXML
    private TextField campoCpf;
    @FXML
    private TextField campoTelefone;
    @FXML
    private TextField campoEmail;
    @FXML
    private ComboBox<EstadoCivil> campoEstadoCivil;
    @FXML
    private TextField campoProfissao;
    @FXML
    private TextField campoLocalTrabalho;
    @FXML
    private TextField campoRenda;
    @FXML
    private TextField campoOutrasRendas;
    @FXML
    private VBox blocoConjuge;
    @FXML
    private TextField campoConjugeNome;
    @FXML
    private TextField campoConjugeCpf;
    @FXML
    private TextField campoConjugeRenda;
    @FXML
    private Button botaoSalvar;
    @FXML
    private Label dicaCpf;
    @FXML
    private Label dicaConjugeCpf;

    @FXML
    private void initialize() {
        podeEditar = Sessao.pode(Acao.EDITAR_INQUILINO);
        montarTabela();

        // Máscaras: os campos se formatam sozinhos enquanto a pessoa digita (BOARD-165).
        Mascara.cpf(campoCpf, dicaCpf);
        Mascara.cpf(campoConjugeCpf, dicaConjugeCpf);
        Mascara.telefone(campoTelefone);
        Mascara.dinheiro(campoRenda);
        Mascara.dinheiro(campoOutrasRendas);
        Mascara.dinheiro(campoConjugeRenda);

        campoEstadoCivil.getItems().setAll(EstadoCivil.values());
        campoEstadoCivil.valueProperty().addListener((obs, antes, agora) -> mostrarConjuge());

        // Cada botão só aparece para quem tem a permissão dele (ex.: pode editar, mas não criar).
        Permissoes.mostrarSe(botaoNovo, Acao.CRIAR_INQUILINO);
        colunaAcoes.setVisible(podeEditar);

        // Esc fecha a janela do formulário.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });

        // Busca: filtra enquanto a pessoa digita; Esc limpa; Ctrl+F leva o cursor para a busca.
        campoBusca.textProperty().addListener((obs, antes, agora) -> aplicarBusca());
        campoBusca.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                limparBusca();
            }
        });
        campoBusca.sceneProperty().addListener((obs, antes, cena) -> {
            if (cena != null) {
                cena.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        () -> campoBusca.requestFocus());
            }
        });

        esconder(veu);
        esconder(toast);
        carregarLista();
    }

    private void montarTabela() {
        // Coluna "Inquilino": círculo com as iniciais, nome e, embaixo, a profissão ou o e-mail.
        colunaNome.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaNome.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Inquilino i, boolean vazio) {
                super.updateItem(i, vazio);
                if (vazio || i == null) {
                    setGraphic(null);
                    return;
                }
                Label avatar = new Label(Iniciais.de(i.getNome()));
                avatar.getStyleClass().add("avatar");
                Label nome = new Label(i.getNome());
                nome.getStyleClass().add("celula-nome");
                String detalhe = i.getProfissao() != null ? i.getProfissao() : i.getEmail();
                VBox textos = new VBox(2, nome);
                if (detalhe != null) {
                    Label linha = new Label(detalhe);
                    linha.getStyleClass().add("celula-detalhe");
                    textos.getChildren().add(linha);
                }
                HBox caixa = new HBox(12, avatar, textos);
                caixa.setAlignment(Pos.CENTER_LEFT);
                setGraphic(caixa);
            }
        });
        colunaCpf.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCpf().formatado()));
        colunaTelefone.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTelefone() == null ? "—" : c.getValue().getTelefone()));
        colunaEstadoCivil.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstadoCivil().getDescricao()));
        colunaRenda.setCellValueFactory(c -> new SimpleStringProperty(Formatos.moeda(c.getValue().rendaConsiderada())));
        colunaRenda.setCellFactory(c -> {
            TableCell<Inquilino, String> celula = new TableCell<>() {
                @Override
                protected void updateItem(String valor, boolean vazio) {
                    super.updateItem(valor, vazio);
                    setText(vazio ? null : valor);
                }
            };
            celula.getStyleClass().add("celula-numero"); // valores alinhados à direita
            return celula;
        });

        // Coluna de ações: botão de lápis para editar.
        colunaAcoes.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAcoes.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Inquilino i, boolean vazio) {
                super.updateItem(i, vazio);
                if (vazio || i == null) {
                    setGraphic(null);
                    return;
                }
                Button editar = new Button(null, new FontIcon("fth-edit-2"));
                editar.getStyleClass().add("botao-icone");
                editar.setTooltip(new Tooltip("Editar " + i.getNome()));
                editar.setOnAction(e -> abrirParaEditar(i));
                setGraphic(editar);
                setAlignment(Pos.CENTER);
            }
        });

        // Clicar na linha também abre para editar (para quem pode).
        tabela.setRowFactory(t -> {
            TableRow<Inquilino> linha = new TableRow<>();
            linha.setOnMouseClicked(e -> {
                if (podeEditar && !linha.isEmpty()) {
                    abrirParaEditar(linha.getItem());
                }
            });
            return linha;
        });

        FontIcon icone = new FontIcon("fth-users");
        Label titulo = new Label("Nenhum inquilino cadastrado ainda");
        titulo.getStyleClass().add("tabela-vazia-titulo");
        Label dica = new Label(Sessao.pode(Acao.CRIAR_INQUILINO) ? "Clique em \"Novo inquilino\" para começar." : "Os inquilinos aparecem aqui.");
        dica.getStyleClass().add("texto");
        VBox vazio = new VBox(8, icone, titulo, dica);
        vazio.setAlignment(Pos.CENTER);
        vazio.getStyleClass().add("tabela-vazia");
        vazioSemCadastro = vazio;

        // Busca sem resultado: diz o que foi buscado e oferece limpar.
        tituloVazioBusca = new Label();
        tituloVazioBusca.getStyleClass().add("tabela-vazia-titulo");
        Label dicaBusca = new Label("Confira se digitou certo. Dá para buscar por parte do nome ou do CPF.");
        dicaBusca.getStyleClass().add("texto");
        Button limpar = new Button("Limpar busca");
        limpar.getStyleClass().add("botao-secundario");
        limpar.setOnAction(e -> limparBusca());
        vazioBusca = new VBox(8, new FontIcon("fth-search"), tituloVazioBusca, dicaBusca, limpar);
        vazioBusca.setAlignment(Pos.CENTER);
        vazioBusca.getStyleClass().add("tabela-vazia");
        tabela.setPlaceholder(vazioSemCadastro);
    }

    /** Botão "Novo inquilino": abre a janela vazia. */
    @FXML
    private void novo() {
        emEdicao = null;
        tituloJanela.setText("Novo inquilino");
        botaoSalvar.setText("Cadastrar inquilino");
        for (TextField campo : new TextField[] {campoNome, campoCpf, campoTelefone, campoEmail, campoProfissao,
            campoLocalTrabalho, campoRenda, campoOutrasRendas, campoConjugeNome, campoConjugeCpf, campoConjugeRenda}) {
            campo.clear();
        }
        campoEstadoCivil.setValue(EstadoCivil.SOLTEIRO);
        abrirJanela();
    }

    private void abrirParaEditar(Inquilino i) {
        emEdicao = i;
        tituloJanela.setText("Editar inquilino");
        botaoSalvar.setText("Salvar alterações");
        campoNome.setText(i.getNome());
        campoCpf.setText(i.getCpf().formatado());
        campoTelefone.setText(textoOuVazio(i.getTelefone()));
        campoEmail.setText(textoOuVazio(i.getEmail()));
        campoEstadoCivil.setValue(i.getEstadoCivil());
        campoProfissao.setText(textoOuVazio(i.getProfissao()));
        campoLocalTrabalho.setText(textoOuVazio(i.getLocalTrabalho()));
        campoRenda.setText(valorOuVazio(i.getRendaMensal()));
        campoOutrasRendas.setText(valorOuVazio(i.getOutrasRendas()));
        Conjuge c = i.getConjuge();
        campoConjugeNome.setText(c == null ? "" : c.getNome());
        campoConjugeCpf.setText(c == null || c.getCpf() == null ? "" : c.getCpf().formatado());
        campoConjugeRenda.setText(c == null ? "" : valorOuVazio(c.getRenda()));
        abrirJanela();
    }

    private void abrirJanela() {
        esconder(avisoErros);
        mostrarConjuge();
        Navegador.mostrarJanela(veu); // por cima da tela inteira, inclusive do menu
        campoNome.requestFocus();
    }

    /** Botões "Cancelar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
        tabela.getSelectionModel().clearSelection();
    }

    /** Botão "Cadastrar inquilino" / "Salvar alterações". */
    @FXML
    private void salvar() {
        try {
            boolean cadastroNovo = emEdicao == null;
            Inquilino salvo = cadastroNovo
                    ? servico.cadastrar(lerFormulario())
                    : servico.editar(emEdicao.getId(), lerFormulario());
            fechar();
            carregarLista();
            mostrarToast((cadastroNovo ? "Inquilino cadastrado: " : "Alterações salvas: ") + salvo.getNome());
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Erro no banco de dados: " + e.getMessage());
        }
    }

    private DadosInquilino lerFormulario() {
        return new DadosInquilino(campoNome.getText(), campoCpf.getText(), campoTelefone.getText(),
                campoEmail.getText(), campoEstadoCivil.getValue(), campoProfissao.getText(),
                campoLocalTrabalho.getText(), campoRenda.getText(), campoOutrasRendas.getText(),
                campoConjugeNome.getText(), campoConjugeCpf.getText(), campoConjugeRenda.getText());
    }

    /** Os campos do cônjuge só aparecem para casado(a) ou união estável. */
    private void mostrarConjuge() {
        EstadoCivil estado = campoEstadoCivil.getValue();
        boolean temConjuge = estado != null && estado.temConjuge();
        blocoConjuge.setVisible(temConjuge);
        blocoConjuge.setManaged(temConjuge);
    }

    private void carregarLista() {
        try {
            todos = servico.listar();
            aplicarBusca();
        } catch (RegraNegocioException e) {
            contagem.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            contagem.setText("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Mostra na tabela só os inquilinos que atendem à busca e atualiza a contagem. */
    private void aplicarBusca() {
        String busca = campoBusca.getText();
        boolean buscando = !busca.isBlank();
        tabela.getItems().setAll(ServicoInquilinos.filtrar(todos, busca));
        int total = todos.size();
        int achados = tabela.getItems().size();
        if (buscando) {
            contagem.setText(achados + " de " + total + (total == 1 ? " inquilino" : " inquilinos"));
            tituloVazioBusca.setText("Nenhum inquilino encontrado para \"" + busca.trim() + "\"");
            tabela.setPlaceholder(vazioBusca);
        } else {
            contagem.setText(total == 1 ? "1 inquilino cadastrado" : total + " inquilinos cadastrados");
            tabela.setPlaceholder(vazioSemCadastro);
        }
    }

    /** Botão "Limpar busca" (ao lado da busca e na lista vazia) e a tecla Esc. */
    @FXML
    private void limparBusca() {
        campoBusca.clear();
        campoBusca.requestFocus();
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        avisoErros.setVisible(true);
        avisoErros.setManaged(true);
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

    private static void esconder(javafx.scene.Node componente) {
        componente.setVisible(false);
        componente.setManaged(false);
    }

    private static String textoOuVazio(String texto) {
        return texto == null ? "" : texto;
    }

    /** Zero aparece como campo vazio, para não poluir o formulário. */
    private static String valorOuVazio(BigDecimal valor) {
        return valor == null || valor.signum() == 0 ? "" : Formatos.numero(valor);
    }
}
