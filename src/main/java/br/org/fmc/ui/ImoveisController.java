package br.org.fmc.ui;

import br.org.fmc.model.*;
import br.org.fmc.service.ServicoImoveis;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.sql.SQLException;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

/** Lista e formulario dos atributos existentes de Imovel. */
public class ImoveisController {
    @FXML private VBox pagina;
    private final ServicoImoveis servico = new ServicoImoveis();
    private final TableView<Imovel> tabela = new TableView<>();
    private final Label mensagem = new Label();

    @FXML
    private void initialize() {
        Label titulo = new Label("Imóveis");
        titulo.getStyleClass().add("titulo");
        mensagem.getStyleClass().add("texto");
        mensagem.setWrapText(true);
        VBox textos = new VBox(6, titulo, mensagem);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Button novo = new Button("Novo imóvel");
        novo.getStyleClass().add("botao-primario");
        novo.setOnAction(e -> formulario(null));
        Permissoes.mostrarSe(novo, Acao.CRIAR_IMOVEL);
        HBox topo = new HBox(16, textos, novo);
        topo.setAlignment(Pos.CENTER_LEFT);
        coluna("Código", Imovel::getCodigo, 95);
        coluna("Unidade", i -> i.getUnidade() == null ? "—" : i.getUnidade(), 200);
        coluna("Endereço", Imovel::getEndereco, 230);
        coluna("Bairro", Imovel::getBairro, 110);
        coluna("Aluguel", i -> Formatos.moeda(i.getValorAluguel()), 120);
        coluna("Taxa de luz", i -> Formatos.moeda(i.getTaxaLuz()), 110);
        coluna("Situação", i -> i.getSituacao().toString(), 130);
        tabela.getStyleClass().add("tabela-fmc");
        tabela.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabela.setPlaceholder(new Label("Nenhum imóvel cadastrado."));
        Button editar = new Button("Editar imóvel selecionado");
        Permissoes.mostrarSe(editar, Acao.EDITAR_IMOVEL);
        editar.disableProperty().bind(tabela.getSelectionModel().selectedItemProperty().isNull());
        editar.setOnAction(e -> formulario(tabela.getSelectionModel().getSelectedItem()));
        Button excluir = new Button("Excluir imóvel selecionado");
        Permissoes.mostrarSe(excluir, Acao.EXCLUIR_IMOVEL);
        excluir.disableProperty().bind(tabela.getSelectionModel().selectedItemProperty().isNull());
        excluir.setOnAction(e -> excluirSelecionado());
        VBox cartao = new VBox(tabela);
        cartao.getStyleClass().add("cartao-tabela");
        VBox.setVgrow(tabela, Priority.ALWAYS);
        VBox.setVgrow(cartao, Priority.ALWAYS);
        pagina.getChildren().setAll(topo, cartao, new HBox(12, editar, excluir));
        carregar();
    }

    private void coluna(String titulo, Function<Imovel, String> valor, double largura) {
        TableColumn<Imovel, String> c = new TableColumn<>(titulo);
        c.setPrefWidth(largura);
        c.setMinWidth(largura);
        c.setCellValueFactory(item -> new SimpleStringProperty(valor.apply(item.getValue())));
        tabela.getColumns().add(c);
    }

    private void excluirSelecionado() {
        Imovel imovel = tabela.getSelectionModel().getSelectedItem();
        if (imovel == null) return;
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION);
        confirmar.initOwner(pagina.getScene().getWindow());
        confirmar.setTitle("Excluir imóvel");
        confirmar.setHeaderText("Excluir o imóvel " + imovel.getCodigo() + "?");
        confirmar.setContentText("Unidade: " + imovel.getUnidade() + "\nEndereço: " + imovel.getEndereco()
                + "\nEsta exclusão é permanente.");
        ButtonType sim = new ButtonType("Excluir", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmar.getButtonTypes().setAll(sim, cancelar);
        ((Button) confirmar.getDialogPane().lookupButton(cancelar)).setDefaultButton(true);
        ((Button) confirmar.getDialogPane().lookupButton(sim)).setDefaultButton(false);
        if (confirmar.showAndWait().orElse(cancelar) != sim) return;
        try {
            servico.excluir(imovel.getId());
            carregar();
            mensagem.setText("Imóvel excluído: " + imovel.getCodigo());
        } catch (SQLException | RuntimeException ex) {
            Alert erro = new Alert(Alert.AlertType.ERROR, "Não foi possível excluir o imóvel: " + ex.getMessage());
            erro.initOwner(pagina.getScene().getWindow());
            erro.showAndWait();
        }
    }

    private void carregar() {
        try {
            tabela.getItems().setAll(servico.listar());
            mensagem.setText(tabela.getItems().size() + " imóvel(is) cadastrado(s)");
        } catch (SQLException | RuntimeException e) {
            mensagem.setText("Não foi possível carregar os imóveis: " + e.getMessage());
        }
    }

    private void formulario(Imovel original) {
        TextField codigo = new TextField(original == null ? "" : original.getCodigo());
        TextField unidade = new TextField(original == null ? "" : original.getUnidade());
        TextField endereco = new TextField(original == null ? "" : original.getEndereco());
        TextField bairro = new TextField(original == null ? "" : original.getBairro());
        TextField aluguel = new TextField(original == null ? "" : Formatos.numero(original.getValorAluguel()));
        TextField luz = new TextField(original == null ? "" : Formatos.numero(original.getTaxaLuz()));
        TextField iptu = new TextField(original == null ? "" : Formatos.numero(original.getValorIptu()));
        Mascara.dinheiro(aluguel);
        Mascara.dinheiro(luz);
        Mascara.dinheiro(iptu);
        ComboBox<TipoImovel> tipo = new ComboBox<>();
        tipo.getItems().setAll(TipoImovel.values());
        tipo.setValue(original == null ? TipoImovel.CASA : original.getTipo());
        ComboBox<SituacaoImovel> situacao = new ComboBox<>();
        situacao.getItems().setAll(SituacaoImovel.values());
        situacao.setValue(original == null ? SituacaoImovel.VAGO : original.getSituacao());
        GridPane campos = new GridPane();
        campos.setHgap(16);
        campos.setVgap(18);
        for (int n = 0; n < 2; n++) {
            ColumnConstraints coluna = new ColumnConstraints();
            coluna.setPercentWidth(50);
            coluna.setHgrow(Priority.ALWAYS);
            campos.getColumnConstraints().add(coluna);
        }
        campo(campos, "Código *", codigo, 0, 0, 1);
        campo(campos, "Unidade", unidade, 1, 0, 1);
        campo(campos, "Endereço *", endereco, 0, 1, 2);
        campo(campos, "Bairro", bairro, 0, 2, 2);
        campo(campos, "Tipo de imóvel *", tipo, 0, 3, 1);
        campo(campos, "Situação *", situacao, 1, 3, 1);
        campo(campos, "Aluguel (R$) *", aluguel, 0, 4, 1);
        campo(campos, "Taxa de luz (R$)", luz, 1, 4, 1);
        campo(campos, "IPTU (R$)", iptu, 0, 5, 1);
        Label erro = new Label();
        erro.setWrapText(true);
        erro.getStyleClass().add("aviso-erro");
        erro.setVisible(false);
        erro.setManaged(false);
        Label titulo = new Label(original == null ? "Novo imóvel" : "Editar " +
                (original.getUnidade() == null ? original.getCodigo() : original.getUnidade()));
        titulo.getStyleClass().add("janela-titulo");
        Label dica = new Label(original == null ? "Campos com * são obrigatórios." :
                original.getCodigo() + " · " + original.getEndereco());
        dica.getStyleClass().add("janela-sub");
        Button cancelar = new Button("Cancelar");
        Button salvar = new Button(original == null ? "Cadastrar imóvel" : "Salvar alterações");
        salvar.getStyleClass().add("botao-primario");
        HBox botoes = new HBox(12, cancelar, salvar);
        botoes.setAlignment(Pos.CENTER_RIGHT);
        Button fechar = new Button("×");
        fechar.getStyleClass().add("botao-icone");
        fechar.setAccessibleText("Fechar");
        VBox cabecalho = new VBox(6, titulo, dica);
        HBox.setHgrow(cabecalho, Priority.ALWAYS);
        HBox topo = new HBox(16, cabecalho, fechar);
        topo.setAlignment(Pos.TOP_LEFT);
        VBox corpo = new VBox(20, erro, campos);
        corpo.setPadding(new Insets(24));
        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        topo.getStyleClass().add("janela-topo");
        botoes.getStyleClass().add("janela-rodape");
        botoes.setPadding(new Insets(16, 24, 16, 24));
        VBox janela = new VBox(topo, rolagem, botoes);
        janela.getStyleClass().add("janela");
        janela.setMaxWidth(760);
        janela.setMaxHeight(660);
        VBox.setVgrow(rolagem, Priority.ALWAYS);
        StackPane veu = new StackPane(janela);
        veu.getStyleClass().add("veu");
        veu.setPadding(new Insets(24));
        cancelar.setOnAction(e -> Navegador.fecharJanela(veu));
        fechar.setOnAction(e -> Navegador.fecharJanela(veu));
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) Navegador.fecharJanela(veu);
        });
        salvar.setOnAction(e -> {
            try {
                servico.salvarFormulario(original == null ? null : original.getId(),
                        codigo.getText(), unidade.getText(), tipo.getValue(), endereco.getText(), bairro.getText(),
                        original == null ? null : original.getGrupo(), aluguel.getText(), luz.getText(),
                        iptu.getText(), situacao.getValue());
                Navegador.fecharJanela(veu);
                carregar();
                mensagem.setText("Imóvel salvo: " + codigo.getText().trim());
            } catch (SQLException ex) {
                erro.setText("Não foi possível salvar no banco de dados.");
                erro.setVisible(true);
                erro.setManaged(true);
            } catch (RuntimeException ex) {
                erro.setText(ex.getMessage());
                erro.setVisible(true);
                erro.setManaged(true);
            }
        });
        Navegador.mostrarJanela(veu);
        if (original == null) codigo.requestFocus(); else unidade.requestFocus();
    }

    private void campo(GridPane grade, String texto, Control controle, int coluna, int linha, int largura) {
        Label rotulo = new Label(texto);
        rotulo.setLabelFor(controle);
        controle.setMaxWidth(Double.MAX_VALUE);
        VBox caixa = new VBox(8, rotulo, controle);
        grade.add(caixa, coluna, linha, largura, 1);
        GridPane.setHgrow(caixa, Priority.ALWAYS);
    }
}
