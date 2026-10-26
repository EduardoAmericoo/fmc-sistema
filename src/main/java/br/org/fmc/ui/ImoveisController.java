package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoImoveis;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
 * Controla a tela "Imóveis" (ui/imoveis.fxml), no mesmo modelo da tela de Inquilinos.
 * Só cuida da tela: as regras (código repetido, situação pelo contrato, arquivar, excluir)
 * ficam no ServicoImoveis.
 */
public class ImoveisController {

    private final ServicoImoveis servico = new ServicoImoveis();

    /** Todos os imóveis carregados do banco; a tabela mostra só os que passam nos filtros. */
    private List<Imovel> todos = List.of();

    /** Imóvel aberto no formulário; null quando é um cadastro novo. */
    private Imovel emEdicao;

    /** Imóvel que está sendo arquivado (janela "Arquivar imóvel"). */
    private Imovel aArquivar;

    @FXML
    private Label contagem;
    @FXML
    private Button botaoNovo;
    @FXML
    private TextField campoBusca;
    @FXML
    private ComboBox<SituacaoImovel> filtroSituacao;
    @FXML
    private CheckBox verArquivados;
    @FXML
    private TableView<Imovel> tabela;
    @FXML
    private TableColumn<Imovel, Imovel> colunaImovel;
    @FXML
    private TableColumn<Imovel, Imovel> colunaEndereco;
    @FXML
    private TableColumn<Imovel, Imovel> colunaTipo;
    @FXML
    private TableColumn<Imovel, Imovel> colunaAluguel;
    @FXML
    private TableColumn<Imovel, Imovel> colunaSituacao;
    @FXML
    private TableColumn<Imovel, Imovel> colunaAcoes;

    @FXML
    private HBox toast;
    @FXML
    private Label textoToast;

    @FXML
    private StackPane veu;
    @FXML
    private Label tituloJanela;
    @FXML
    private Label subtituloJanela;
    @FXML
    private HBox avisoErros;
    @FXML
    private Label textoErros;
    @FXML
    private TextField campoCodigo;
    @FXML
    private TextField campoUnidade;
    @FXML
    private ComboBox<TipoImovel> campoTipo;
    @FXML
    private ComboBox<SituacaoImovel> campoSituacao;
    @FXML
    private Label dicaSituacao;
    @FXML
    private TextField campoEndereco;
    @FXML
    private TextField campoBairro;
    @FXML
    private TextField campoAluguel;
    @FXML
    private TextField campoLuz;
    @FXML
    private TextField campoIptu;
    @FXML
    private Button botaoSalvar;

    @FXML
    private StackPane veuArquivar;
    @FXML
    private Label tituloArquivar;
    @FXML
    private HBox avisoArquivar;
    @FXML
    private Label textoAvisoArquivar;
    @FXML
    private TextField campoMotivo;

    @FXML
    private void initialize() {
        montarTabela();

        Mascara.dinheiro(campoAluguel);
        Mascara.dinheiro(campoLuz);
        Mascara.dinheiro(campoIptu);
        campoTipo.getItems().setAll(TipoImovel.values());

        // Filtros: situação (vazio = todas), busca e arquivados.
        filtroSituacao.getItems().setAll(SituacaoImovel.values());
        filtroSituacao.setButtonCell(celulaSituacao());
        filtroSituacao.setCellFactory(l -> celulaSituacao());
        filtroSituacao.valueProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        campoBusca.textProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        verArquivados.selectedProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        campoBusca.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                limparFiltros();
            }
        });

        Permissoes.mostrarSe(botaoNovo, Acao.CRIAR_IMOVEL);
        colunaAcoes.setVisible(Sessao.pode(Acao.EDITAR_IMOVEL) || Sessao.pode(Acao.ARQUIVAR_IMOVEL)
                || Sessao.pode(Acao.EXCLUIR_IMOVEL));

        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        veuArquivar.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fecharArquivar();
            }
        });

        esconder(veu);
        esconder(veuArquivar);
        esconder(toast);
        carregarLista();
    }

    private void montarTabela() {
        colunaImovel.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaImovel.setCellFactory(c -> celula(i -> duasLinhas(i.getCodigo(),
                i.getUnidade() == null ? "—" : i.getUnidade())));
        colunaEndereco.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaEndereco.setCellFactory(c -> celula(i -> duasLinhas(i.getEndereco(),
                i.getBairro() == null ? "—" : i.getBairro())));
        colunaTipo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaTipo.setCellFactory(c -> celula(i -> new Label(i.getTipo().toString())));
        colunaAluguel.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAluguel.setCellFactory(c -> {
            TableCell<Imovel, Imovel> celula = celula(i -> new Label(Formatos.moeda(i.getValorAluguel())));
            celula.getStyleClass().add("celula-numero"); // valores alinhados à direita
            return celula;
        });
        colunaSituacao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaSituacao.setCellFactory(c -> celula(ImoveisController::seloSituacao));
        colunaAcoes.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAcoes.setCellFactory(c -> celula(this::botoesDaLinha));

        // Clicar na linha abre para editar (para quem pode; imóvel arquivado não se edita).
        tabela.setRowFactory(t -> {
            TableRow<Imovel> linha = new TableRow<>();
            linha.setOnMouseClicked(e -> {
                if (!linha.isEmpty() && Sessao.pode(Acao.EDITAR_IMOVEL) && !linha.getItem().estaArquivado()
                        && !cliqueEmBotao(e.getTarget())) {
                    abrirParaEditar(linha.getItem());
                }
            });
            return linha;
        });
    }

    /** Ocupado (rende aluguel), vago, em manutenção ou arquivado. */
    private static Label seloSituacao(Imovel i) {
        if (i.estaArquivado()) {
            Label selo = selo("Arquivado", "selo-neutro");
            selo.setTooltip(new Tooltip("Arquivado em " + Formatos.data(i.getArquivadoEm())
                    + (i.getMotivoArquivamento() == null ? "" : " · " + i.getMotivoArquivamento())));
            return selo;
        }
        return switch (i.getSituacao()) {
            case OCUPADO -> selo("Ocupado", "selo-sucesso");
            case VAGO -> selo("Vago", "selo-info");
            case EM_MANUTENCAO -> selo("Em manutenção", "selo-alerta");
        };
    }

    /** Botões da linha, cada um só para quem tem a permissão dele. */
    private Node botoesDaLinha(Imovel i) {
        HBox botoes = new HBox(4);
        botoes.setAlignment(Pos.CENTER);
        if (!i.estaArquivado()) {
            if (Sessao.pode(Acao.EDITAR_IMOVEL)) {
                botoes.getChildren().add(botaoIcone("fth-edit-2", "Editar " + i.getCodigo(), () -> abrirParaEditar(i)));
            }
            if (Sessao.pode(Acao.ARQUIVAR_IMOVEL)) {
                botoes.getChildren().add(botaoIcone("fth-archive", "Arquivar " + i.getCodigo() + " (ex.: vendido)",
                        () -> abrirArquivar(i)));
            }
        } else if (Sessao.pode(Acao.ARQUIVAR_IMOVEL)) {
            botoes.getChildren().add(botaoIcone("fth-rotate-ccw", "Reativar " + i.getCodigo(), () -> reativar(i)));
        }
        if (Sessao.pode(Acao.EXCLUIR_IMOVEL)) {
            botoes.getChildren().add(botaoIcone("fth-trash-2", "Excluir " + i.getCodigo() + " (cadastro por engano)",
                    () -> excluir(i)));
        }
        return botoes;
    }

    /** Mostra só os imóveis que passam nos filtros e atualiza a contagem e a mensagem de lista vazia. */
    private void aplicarFiltros() {
        boolean arquivados = verArquivados.isSelected();
        List<Imovel> filtrados = ServicoImoveis.filtrar(todos, filtroSituacao.getValue(), campoBusca.getText(),
                arquivados);
        tabela.getItems().setAll(filtrados);
        long ativos = todos.stream().filter(i -> !i.estaArquivado()).count();
        long totalArquivados = todos.size() - ativos;
        boolean filtrando = filtroSituacao.getValue() != null || !campoBusca.getText().isBlank();
        if (arquivados) {
            contagem.setText(filtrando ? filtrados.size() + " de " + totalArquivados + " arquivados"
                    : totalArquivados == 1 ? "1 imóvel arquivado" : totalArquivados + " imóveis arquivados");
        } else {
            contagem.setText(filtrando ? filtrados.size() + " de " + ativos + (ativos == 1 ? " imóvel" : " imóveis")
                    : ativos == 1 ? "1 imóvel cadastrado" : ativos + " imóveis cadastrados");
        }
        tabela.setPlaceholder(vazio(
                arquivados ? "fth-archive" : filtrando ? "fth-search" : "fth-home",
                arquivados && !filtrando ? "Nenhum imóvel arquivado"
                        : filtrando ? "Nenhum imóvel encontrado com esses filtros"
                        : "Nenhum imóvel cadastrado ainda",
                filtrando ? "Confira a busca ou clique em \"Limpar filtros\"."
                        : arquivados ? "Imóveis vendidos ou fora de uso aparecem aqui."
                        : Sessao.pode(Acao.CRIAR_IMOVEL) ? "Clique em \"Novo imóvel\" para começar."
                        : "Os imóveis aparecem aqui."));
    }

    @FXML
    private void limparFiltros() {
        campoBusca.clear();
        filtroSituacao.setValue(null);
        verArquivados.setSelected(false);
        campoBusca.requestFocus();
    }

    /** Botão "Novo imóvel": abre a janela vazia. */
    @FXML
    private void novo() {
        emEdicao = null;
        tituloJanela.setText("Novo imóvel");
        subtituloJanela.setText("Campos com * são obrigatórios.");
        botaoSalvar.setText("Cadastrar imóvel");
        for (TextField campo : new TextField[] {campoCodigo, campoUnidade, campoEndereco, campoBairro, campoAluguel,
            campoLuz, campoIptu}) {
            campo.clear();
        }
        campoTipo.setValue(TipoImovel.APARTAMENTO);
        prepararSituacao(SituacaoImovel.VAGO);
        abrirJanela();
    }

    private void abrirParaEditar(Imovel i) {
        emEdicao = i;
        tituloJanela.setText("Editar " + i.getCodigo());
        subtituloJanela.setText(i.getUnidade() == null ? i.getEndereco() : i.getUnidade() + " · " + i.getEndereco());
        botaoSalvar.setText("Salvar alterações");
        campoCodigo.setText(i.getCodigo());
        campoUnidade.setText(textoOuVazio(i.getUnidade()));
        campoEndereco.setText(i.getEndereco());
        campoBairro.setText(textoOuVazio(i.getBairro()));
        campoAluguel.setText(Formatos.numero(i.getValorAluguel()));
        campoLuz.setText(valorOuVazio(i.getTaxaLuz()));
        campoIptu.setText(valorOuVazio(i.getValorIptu()));
        campoTipo.setValue(i.getTipo());
        prepararSituacao(i.getSituacao());
        abrirJanela();
    }

    /**
     * "Ocupado" vem do contrato: imóvel ocupado não muda de situação à mão; os outros
     * só podem ficar vagos ou em manutenção.
     */
    private void prepararSituacao(SituacaoImovel atual) {
        boolean ocupado = atual == SituacaoImovel.OCUPADO;
        campoSituacao.getItems().setAll(ocupado ? List.of(SituacaoImovel.OCUPADO)
                : List.of(SituacaoImovel.VAGO, SituacaoImovel.EM_MANUTENCAO));
        campoSituacao.setValue(atual);
        campoSituacao.setDisable(ocupado);
        dicaSituacao.setText(ocupado ? "Ocupado pelo contrato. Muda quando o contrato for encerrado."
                : "\"Ocupado\" é definido pelo contrato, na tela Contratos.");
    }

    private void abrirJanela() {
        esconder(avisoErros);
        Navegador.mostrarJanela(veu);
        campoCodigo.requestFocus();
    }

    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
        tabela.getSelectionModel().clearSelection();
    }

    /** Botão "Cadastrar imóvel" / "Salvar alterações". */
    @FXML
    private void salvar() {
        try {
            boolean novo = emEdicao == null;
            servico.salvarFormulario(novo ? null : emEdicao.getId(), campoCodigo.getText(), campoUnidade.getText(),
                    campoTipo.getValue(), campoEndereco.getText(), campoBairro.getText(),
                    novo ? null : emEdicao.getGrupo(), campoAluguel.getText(), campoLuz.getText(),
                    campoIptu.getText(), campoSituacao.getValue());
            fechar();
            carregarLista();
            mostrarToast((novo ? "Imóvel cadastrado: " : "Alterações salvas: ") + campoCodigo.getText().trim());
        } catch (RegraNegocioException e) {
            mostrarAviso(avisoErros, textoErros, String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarAviso(avisoErros, textoErros, "Não foi possível salvar no banco de dados. Tente de novo.");
        }
    }

    /** Botão da caixa: abre a janela "Arquivar imóvel", com o motivo. */
    private void abrirArquivar(Imovel i) {
        aArquivar = i;
        tituloArquivar.setText("Arquivar " + i.getCodigo() + "?");
        campoMotivo.clear();
        esconder(avisoArquivar);
        Navegador.mostrarJanela(veuArquivar);
        campoMotivo.requestFocus();
    }

    @FXML
    private void fecharArquivar() {
        aArquivar = null;
        Navegador.fecharJanela(veuArquivar);
    }

    @FXML
    private void confirmarArquivar() {
        if (aArquivar == null) {
            return;
        }
        try {
            Imovel arquivado = servico.arquivar(aArquivar.getId(), campoMotivo.getText());
            fecharArquivar();
            carregarLista();
            mostrarToast("Imóvel arquivado: " + arquivado.getCodigo() + ". Veja em \"Ver arquivados\".");
        } catch (RegraNegocioException e) {
            mostrarAviso(avisoArquivar, textoAvisoArquivar, String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarAviso(avisoArquivar, textoAvisoArquivar, "Não foi possível arquivar no banco de dados. Tente de novo.");
        }
    }

    /** Botão de reativar (na lista de arquivados). */
    private void reativar(Imovel i) {
        try {
            servico.reativar(i.getId());
            carregarLista();
            mostrarToast("Imóvel reativado: " + i.getCodigo());
        } catch (RegraNegocioException e) {
            avisarErro(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            avisarErro("Não foi possível reativar no banco de dados. Tente de novo.");
        }
    }

    /** Botão da lixeira: exclui de verdade, só cadastro feito por engano (o service confere). */
    private void excluir(Imovel i) {
        ButtonType sim = new ButtonType("Excluir", ButtonBar.ButtonData.OK_DONE);
        ButtonType nao = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Use só para cadastro feito por engano. Imóvel vendido ou fora de uso deve ser arquivado, "
                        + "para o histórico ficar guardado. A exclusão não pode ser desfeita.", sim, nao);
        confirmar.initOwner(tabela.getScene().getWindow());
        confirmar.setTitle("Imóveis");
        confirmar.setHeaderText("Excluir o imóvel " + i.getCodigo() + "?");
        ((Button) confirmar.getDialogPane().lookupButton(nao)).setDefaultButton(true);
        ((Button) confirmar.getDialogPane().lookupButton(sim)).setDefaultButton(false);
        if (confirmar.showAndWait().orElse(nao) != sim) {
            return;
        }
        try {
            servico.excluir(i.getId());
            carregarLista();
            mostrarToast("Imóvel excluído: " + i.getCodigo());
        } catch (RegraNegocioException e) {
            avisarErro(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            avisarErro("Não foi possível excluir no banco de dados. Tente de novo.");
        }
    }

    private void carregarLista() {
        try {
            todos = servico.listar();
            aplicarFiltros();
        } catch (RegraNegocioException e) {
            contagem.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            contagem.setText("Não foi possível carregar os imóveis do banco de dados.");
        }
    }

    private void avisarErro(String texto) {
        Alert alerta = new Alert(Alert.AlertType.WARNING, texto);
        alerta.initOwner(tabela.getScene().getWindow());
        alerta.setTitle("Imóveis");
        alerta.setHeaderText("Não foi possível concluir");
        alerta.show();
    }

    /** Aviso rápido no canto da tela, que some sozinho depois de 4 segundos. */
    private void mostrarToast(String texto) {
        textoToast.setText(texto);
        mostrar(toast, true);
        PauseTransition espera = new PauseTransition(Duration.seconds(4));
        espera.setOnFinished(e -> esconder(toast));
        espera.play();
    }

    /** Mostra "Todas as situações" quando nada está escolhido no filtro. */
    private static ListCell<SituacaoImovel> celulaSituacao() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SituacaoImovel item, boolean vazia) {
                super.updateItem(item, vazia);
                setText(vazia || item == null ? "Todas as situações" : item.toString());
            }
        };
    }

    /** Célula de tabela que desenha um componente a partir do imóvel da linha. */
    private static TableCell<Imovel, Imovel> celula(Function<Imovel, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Imovel i, boolean vazio) {
                super.updateItem(i, vazio);
                setGraphic(vazio || i == null ? null : desenho.apply(i));
            }
        };
    }

    private static VBox duasLinhas(String principal, String detalhe) {
        Label linha1 = new Label(principal);
        linha1.getStyleClass().add("celula-nome");
        Label linha2 = new Label(detalhe);
        linha2.getStyleClass().add("celula-detalhe");
        return new VBox(2, linha1, linha2);
    }

    private static Label selo(String texto, String tipo) {
        Label selo = new Label(texto);
        selo.getStyleClass().addAll("selo", tipo);
        return selo;
    }

    private static Button botaoIcone(String icone, String dica, Runnable acao) {
        Button botao = new Button(null, new FontIcon(icone));
        botao.getStyleClass().add("botao-icone");
        botao.setTooltip(new Tooltip(dica));
        botao.setOnAction(e -> acao.run());
        return botao;
    }

    /** O clique foi num dos botões da linha? (aí a linha não abre o formulário) */
    private static boolean cliqueEmBotao(Object alvo) {
        for (Node n = alvo instanceof Node no ? no : null; n != null; n = n.getParent()) {
            if (n instanceof Button) {
                return true;
            }
        }
        return false;
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

    private static void mostrarAviso(HBox aviso, Label texto, String mensagem) {
        texto.setText(mensagem);
        mostrar(aviso, true);
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }

    private static void esconder(Node componente) {
        mostrar(componente, false);
    }

    private static String textoOuVazio(String texto) {
        return texto == null ? "" : texto;
    }

    /** Zero aparece como campo vazio, para não poluir o formulário. */
    private static String valorOuVazio(BigDecimal valor) {
        return valor == null || valor.signum() == 0 ? "" : Formatos.numero(valor);
    }
}
