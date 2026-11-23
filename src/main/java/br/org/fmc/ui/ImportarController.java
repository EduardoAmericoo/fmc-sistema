package br.org.fmc.ui;

import br.org.fmc.service.ImportacaoPlanilha;
import br.org.fmc.service.ImportacaoPlanilha.Problema;
import br.org.fmc.service.ImportacaoPlanilha.Resultado;
import br.org.fmc.service.ModeloPlanilha;
import br.org.fmc.service.RegraNegocioException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tela "Importar planilha" (revisão da SR2). Só desenha e chama a ImportacaoPlanilha:
 * baixar o modelo, conferir a planilha (sem gravar) e importar tudo ou nada.
 */
public class ImportarController {

    private final ImportacaoPlanilha importacao = new ImportacaoPlanilha();
    private Path planilha;

    @FXML private Button botaoEscolher;
    @FXML private HBox avisoOk;
    @FXML private Label textoOk;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private VBox blocoResultado;
    @FXML private HBox veredito;
    @FXML private FontIcon iconeVeredito;
    @FXML private Label textoVeredito;
    @FXML private Label qtdImoveis;
    @FXML private Label qtdInquilinos;
    @FXML private Label qtdContratos;
    @FXML private Label qtdProblemas;
    @FXML private VBox cartaoProblemas;
    @FXML private TableView<Problema> tabelaProblemas;
    @FXML private TableColumn<Problema, String> colunaOnde;
    @FXML private TableColumn<Problema, String> colunaProblema;
    @FXML private Label nomeArquivo;
    @FXML private Button botaoConferirDeNovo;
    @FXML private Button botaoImportar;

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        mostrar(blocoResultado, false);
        mostrar(avisoOk, false);
        colunaOnde.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().local()));
        colunaProblema.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().mensagem()));
        tabelaProblemas.setPlaceholder(new Label("Nenhum problema."));
    }

    @FXML
    private void baixarModelo() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar o modelo de planilha");
        seletor.setInitialFileName("Modelo de importação - Sistema FMC.xlsx");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Planilha do Excel", "*.xlsx"));
        File destino = seletor.showSaveDialog(botaoEscolher.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            ModeloPlanilha.gerar(destino.toPath());
            mostrar(avisoErros, false);
            textoOk.setText("Modelo salvo em " + destino.getName() + ". Abra no Excel, preencha e volte aqui no passo 3.");
            mostrar(avisoOk, true);
        } catch (IOException e) {
            erro("Não foi possível salvar o modelo: " + e.getMessage());
        }
    }

    @FXML
    private void escolherPlanilha() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Escolher a planilha preenchida");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Planilha do Excel", "*.xlsx"));
        File escolhida = seletor.showOpenDialog(botaoEscolher.getScene().getWindow());
        if (escolhida != null) {
            planilha = escolhida.toPath();
            conferir();
        }
    }

    @FXML
    private void conferirDeNovo() {
        conferir();
    }

    /** Ensaio: confere todas as linhas sem gravar nada e mostra o que corrigir. */
    private void conferir() {
        Resultado r = executar(() -> importacao.conferir(planilha));
        if (r == null) {
            return;
        }
        mostrarResultado(r, false);
    }

    @FXML
    private void importar() {
        Alert pergunta = new Alert(Alert.AlertType.CONFIRMATION, "Importar " + resumo(ultimo)
                + " para o sistema? Os contratos começam a gerar aluguel a partir do próximo vencimento; "
                + "os meses de antes contam como pagos.", ButtonType.OK, ButtonType.CANCEL);
        pergunta.setHeaderText("Importar planilha");
        pergunta.initOwner(botaoImportar.getScene().getWindow());
        if (pergunta.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        Resultado r = executar(() -> importacao.importar(planilha));
        if (r == null) {
            return;
        }
        mostrarResultado(r, true);
    }

    private Resultado ultimo;

    private interface Acao {
        Resultado executar() throws IOException, SQLException;
    }

    /** Roda a conferência ou a importação com o cursor de espera, e mostra o erro se houver. */
    private Resultado executar(Acao acao) {
        Node raiz = botaoEscolher.getScene() == null ? botaoEscolher : botaoEscolher.getScene().getRoot();
        raiz.setCursor(Cursor.WAIT);
        try {
            mostrar(avisoErros, false);
            mostrar(avisoOk, false);
            ultimo = acao.executar();
            return ultimo;
        } catch (RegraNegocioException e) {
            erro(String.join("\n", e.getErros()));
        } catch (IOException e) {
            erro(e.getMessage());
        } catch (SQLException e) {
            erro(Falha.doBanco(e) + " Nada foi gravado.");
        } finally {
            raiz.setCursor(Cursor.DEFAULT);
        }
        return null;
    }

    private void mostrarResultado(Resultado r, boolean importado) {
        mostrar(blocoResultado, true);
        qtdImoveis.setText(String.valueOf(r.imoveis()));
        qtdInquilinos.setText(String.valueOf(r.inquilinos()));
        qtdContratos.setText(String.valueOf(r.contratos()));
        qtdProblemas.setText(String.valueOf(r.problemas().size()));
        tabelaProblemas.getItems().setAll(r.problemas());
        mostrar(cartaoProblemas, !r.semProblemas());
        nomeArquivo.setText(planilha == null ? "" : "Planilha: " + planilha.getFileName());
        veredito.getStyleClass().removeAll("veredito-ok", "veredito-alerta");
        if (!r.semProblemas()) {
            veredito.getStyleClass().add("veredito-alerta");
            iconeVeredito.setIconLiteral("fth-alert-triangle");
            textoVeredito.setText(r.problemas().size() + (r.problemas().size() == 1 ? " problema" : " problemas")
                    + " para corrigir na planilha. Nada foi gravado. Corrija, salve no Excel e clique em "
                    + "\"Conferir de novo\".");
        } else if (importado) {
            veredito.getStyleClass().add("veredito-ok");
            iconeVeredito.setIconLiteral("fth-check-circle");
            textoVeredito.setText("Importação concluída: " + resumo(r) + ". Confira nas telas de Imóveis, "
                    + "Inquilinos e Contratos.");
        } else if (r.total() == 0) {
            veredito.getStyleClass().add("veredito-alerta");
            iconeVeredito.setIconLiteral("fth-info");
            textoVeredito.setText("A planilha não tem nenhuma linha preenchida (a linha de exemplo é ignorada).");
        } else {
            veredito.getStyleClass().add("veredito-ok");
            iconeVeredito.setIconLiteral("fth-check-circle");
            textoVeredito.setText("Tudo certo: " + resumo(r) + " prontos para importar. Nada foi gravado ainda.");
        }
        boolean podeImportar = r.semProblemas() && r.total() > 0 && !importado;
        mostrar(botaoImportar, podeImportar);
        mostrar(botaoConferirDeNovo, !importado);
    }

    private static String resumo(Resultado r) {
        if (r == null) {
            return "";
        }
        return r.imoveis() + (r.imoveis() == 1 ? " imóvel, " : " imóveis, ") + r.inquilinos()
                + (r.inquilinos() == 1 ? " inquilino e " : " inquilinos e ") + r.contratos()
                + (r.contratos() == 1 ? " contrato" : " contratos");
    }

    private void erro(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
