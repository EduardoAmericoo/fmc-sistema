package br.org.fmc.ui;

import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Recibo;
import br.org.fmc.util.Formatos;
import br.org.fmc.util.ReciboImpresso;
import br.org.fmc.util.ReciboPdf;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;

/**
 * BOARD-60 · Janela do recibo: as duas vias no desenho do protótipo, com Imprimir e Salvar PDF.
 * Os textos vêm do ReciboImpresso, o mesmo que o PDF usa.
 */
public class ReciboController {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private Recibo recibo;

    @FXML private StackPane veu;
    @FXML private Label titulo;
    @FXML private Label subtitulo;
    @FXML private VBox vias;
    @FXML private HBox avisoSucesso;
    @FXML private Label textoSucesso;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;

    /**
     * Abre o recibo. Com acabouDeReceber, mostra em cima o aviso de que o recebimento foi registrado.
     */
    public static void abrir(Recibo recibo, boolean acabouDeReceber) {
        FXMLLoader carregador = new FXMLLoader(ReciboController.class.getResource("/br/org/fmc/ui/recibo.fxml"));
        try {
            Node janela = carregador.load();
            ReciboController controle = carregador.getController();
            controle.preencher(recibo, acabouDeReceber);
            Navegador.mostrarJanela(janela);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir recibo.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Recibo recibo, boolean acabouDeReceber) {
        this.recibo = recibo;
        ReciboImpresso r = new ReciboImpresso(recibo);
        titulo.setText("Recibo " + recibo.getNumero()); // a fonte do título (Urbanist) não tem o "º"
        subtitulo.setText(recibo.getAluguel().getContrato().getInquilino().getNome() + " · " + r.mes());
        mostrar(avisoSucesso, acabouDeReceber);
        textoSucesso.setText("Recebimento registrado. Imprima ou salve o recibo para entregar ao inquilino.");
        for (String nomeVia : ReciboImpresso.VIAS) {
            vias.getChildren().add(via(r, nomeVia));
        }
    }

    /** Uma via do recibo, igual ao papel: cabeçalho, mês, composição, texto e assinatura. */
    static VBox via(ReciboImpresso r, String nomeVia) {
        // cabeçalho: Fundação, caixa do mês e composição do valor
        VBox fundacao = new VBox(2, rotulo(ReciboImpresso.FUNDACAO, "recibo-fundacao"),
                rotulo(ReciboImpresso.ADMINISTRACAO, "recibo-pequeno"), rotulo(ReciboImpresso.CIDADE, "recibo-pequeno"));
        HBox.setHgrow(fundacao, Priority.ALWAYS);
        VBox mes = new VBox(2, rotulo("MÊS", "recibo-pequeno"), rotulo(r.mes(), "recibo-mes-nome"));
        mes.setAlignment(Pos.CENTER);
        mes.getStyleClass().add("recibo-mes");

        GridPane composicao = new GridPane();
        composicao.getStyleClass().add("recibo-composicao");
        ColumnConstraints esquerda = new ColumnConstraints();
        esquerda.setHgrow(Priority.ALWAYS);
        composicao.getColumnConstraints().addAll(esquerda, new ColumnConstraints());
        int linha = 0;
        for (ReciboImpresso.Item item : r.composicao()) {
            composicao.add(rotulo(item.rotulo(), "recibo-item"), 0, linha);
            composicao.add(rotulo(Formatos.moeda(item.valor()), "recibo-item"), 1, linha++);
        }
        Region traco = new Region();
        traco.getStyleClass().add("recibo-traco");
        composicao.add(traco, 0, linha++, 2, 1);
        composicao.add(rotulo("TOTAL", "recibo-total"), 0, linha);
        composicao.add(rotulo(Formatos.moeda(r.total()), "recibo-total"), 1, linha);
        composicao.setMinWidth(210);
        composicao.setPrefWidth(230);

        HBox topo = new HBox(18, fundacao, mes, composicao);

        Label tituloRecibo = rotulo("RECIBO", "recibo-titulo");
        Label numero = rotulo(r.numero(), "recibo-numero");
        VBox centro = new VBox(2, tituloRecibo, numero);
        centro.setAlignment(Pos.CENTER);

        // texto do recibo, com o nome e o valor em negrito
        TextFlow recebemos = paragrafo(new Text("Recebemos de "), negrito(r.pagador()));
        TextFlow quantia = paragrafo(new Text("a quantia de "),
                negrito(Formatos.moeda(r.total()) + " " + r.quantiaPorExtenso()));
        TextFlow referente = paragrafo(new Text(r.referente()));

        // vencimento e forma de pagamento (a usada marcada com X)
        HBox formas = new HBox(14);
        formas.setAlignment(Pos.CENTER_LEFT);
        formas.getChildren().add(paragrafo(new Text("Vencimento: "), negrito(r.vencimento())));
        for (FormaPagamento forma : r.formas()) {
            Label caixa = new Label(r.foiPago(forma) ? "X" : "");
            caixa.getStyleClass().add("recibo-caixa");
            HBox opcao = new HBox(5, caixa, rotulo(forma.getDescricao().toUpperCase(BRASIL), "recibo-item"));
            opcao.setAlignment(Pos.CENTER_LEFT);
            formas.getChildren().add(opcao);
        }

        Label pagamento = rotulo(r.pagamento(), "recibo-pequeno");
        Label local = rotulo(r.localEData(), "recibo-local");
        HBox localLinha = new HBox(local);
        localLinha.setAlignment(Pos.CENTER);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox contrato = new HBox(rotulo(r.contrato(), "recibo-item"), espaco, rotulo(r.emitidoPor(), "recibo-pequeno"));

        Region linhaFinal = new Region();
        linhaFinal.getStyleClass().add("recibo-traco");
        Region espaco2 = new Region();
        HBox.setHgrow(espaco2, Priority.ALWAYS);
        HBox rodape = new HBox(rotulo(nomeVia, "recibo-pequeno"), espaco2,
                rotulo("Assinatura: ______________________________", "recibo-pequeno"));

        VBox via = new VBox(10, topo, centro, recebemos, quantia, referente, formas, pagamento, localLinha,
                contrato, linhaFinal, rodape);
        via.getStyleClass().add("recibo-via");
        return via;
    }

    private static Label rotulo(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().add(estilo);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Text negrito(String texto) {
        Text t = new Text(texto);
        t.getStyleClass().addAll("recibo-texto", "recibo-negrito");
        return t;
    }

    private static TextFlow paragrafo(Text... partes) {
        for (Text t : partes) {
            if (t.getStyleClass().isEmpty()) {
                t.getStyleClass().add("recibo-texto");
            }
        }
        TextFlow flow = new TextFlow(partes);
        flow.setMinHeight(Region.USE_PREF_SIZE);
        return flow;
    }

    /** Abre a janela de impressão do Windows e imprime as duas vias numa folha. */
    @FXML
    private void imprimir() {
        PrinterJob trabalho = PrinterJob.createPrinterJob();
        if (trabalho == null) {
            mostrarErros("Nenhuma impressora encontrada neste computador. Use \"Salvar PDF\" e imprima depois.");
            return;
        }
        if (!trabalho.showPrintDialog(veu.getScene().getWindow())) {
            return; // a pessoa cancelou
        }
        PageLayout pagina = trabalho.getJobSettings().getPageLayout();
        double largura = vias.getBoundsInParent().getWidth();
        double altura = vias.getBoundsInParent().getHeight();
        double escala = Math.min(pagina.getPrintableWidth() / largura, pagina.getPrintableHeight() / altura);
        Scale reducao = new Scale(escala, escala);
        vias.getTransforms().add(reducao);
        boolean ok = trabalho.printPage(pagina, vias);
        vias.getTransforms().remove(reducao);
        if (ok) {
            trabalho.endJob();
        } else {
            mostrarErros("Não foi possível imprimir. Confira a impressora ou use \"Salvar PDF\".");
        }
    }

    /** Salva as duas vias em PDF, no lugar que a pessoa escolher. */
    @FXML
    private void salvarPdf() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar recibo em PDF");
        seletor.setInitialFileName(new ReciboImpresso(recibo).nomeArquivo());
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File arquivo = seletor.showSaveDialog(veu.getScene().getWindow());
        if (arquivo == null) {
            return;
        }
        try {
            ReciboPdf.gerar(recibo, arquivo.toPath());
            mostrar(avisoErros, false);
            textoSucesso.setText("Recibo salvo em " + arquivo.getName() + ".");
            mostrar(avisoSucesso, true);
        } catch (IOException e) {
            mostrarErros("Não foi possível salvar o PDF: " + e.getMessage());
        }
    }

    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
