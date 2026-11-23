package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FotoVistoria;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoVistorias;
import br.org.fmc.service.ServicoVistorias.Comodo;
import br.org.fmc.service.Sessao;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Janela "Vistorias" (revisão da SR2): fotos da vistoria de entrada e de saída, por cômodo, e a
 * comparação lado a lado. Só desenha e chama o ServicoVistorias.
 */
public class VistoriasController {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ServicoVistorias servico = new ServicoVistorias();
    private Contrato contrato;
    private Runnable depoisDeMudar;

    @FXML private StackPane veu;
    @FXML private Label titulo;
    @FXML private Label subtitulo;
    @FXML private ToggleGroup grupoAbas;
    @FXML private ToggleButton abaEntrada;
    @FXML private ToggleButton abaSaida;
    @FXML private ToggleButton abaComparar;
    @FXML private VBox conteudo;
    @FXML private Button botaoLaudo;

    /** Campos do "adicionar fotos", guardados entre uma troca de aba e outra. */
    private final ComboBox<String> campoComodo = new ComboBox<>();
    private final TextField campoObservacao = new TextField();
    private final HBox avisoErros = new HBox(10);
    private final Label textoErros = new Label();
    private final HBox avisoOk = new HBox(10);
    private final Label textoOk = new Label();

    public static void abrir(Contrato contrato, EtapaVistoria etapa, Runnable depoisDeMudar) {
        FXMLLoader carregador = new FXMLLoader(VistoriasController.class.getResource("/br/org/fmc/ui/vistorias.fxml"));
        try {
            Node janela = carregador.load();
            VistoriasController controle = carregador.getController();
            controle.depoisDeMudar = depoisDeMudar;
            controle.preencher(contrato, etapa);
            Navegador.mostrarJanela(janela);
            janela.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir vistorias.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        abaEntrada.setUserData(EtapaVistoria.ENTRADA);
        abaSaida.setUserData(EtapaVistoria.SAIDA);
        grupoAbas.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true); // sempre uma aba marcada
                return;
            }
            mostrar(avisoErros, false);
            mostrar(avisoOk, false);
            desenhar();
        });
        campoComodo.setEditable(true);
        campoComodo.getItems().setAll(FotoVistoria.COMODOS);
        campoComodo.setPromptText("Cômodo (escolha ou digite)");
        campoComodo.getStyleClass().add("campo");
        campoComodo.setPrefWidth(240);
        campoObservacao.setPromptText("Observação (opcional): ex.: pintura nova, sem manchas");
        campoObservacao.getStyleClass().add("campo");
        HBox.setHgrow(campoObservacao, Priority.ALWAYS);
        avisoErros.getStyleClass().add("aviso-erro");
        avisoErros.getChildren().addAll(new FontIcon("fth-alert-circle"), textoErros);
        textoErros.setWrapText(true);
        avisoOk.getStyleClass().addAll("veredito", "veredito-ok");
        avisoOk.setAlignment(Pos.CENTER_LEFT);
        avisoOk.getChildren().addAll(new FontIcon("fth-check-circle"), textoOk);
        textoOk.setWrapText(true);
        mostrar(avisoErros, false);
        mostrar(avisoOk, false);
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Contrato c, EtapaVistoria etapa) {
        this.contrato = c;
        titulo.setText("Vistorias · contrato " + c.getNumero());
        subtitulo.setText(c.getImovel().getCodigo() + " · " + c.getImovel().getEndereco() + " · locatário "
                + c.getInquilino().getNome());
        (etapa == EtapaVistoria.SAIDA ? abaSaida : abaEntrada).setSelected(true);
        desenhar();
    }

    private EtapaVistoria etapaAtual() {
        Object dado = grupoAbas.getSelectedToggle() == null ? null : grupoAbas.getSelectedToggle().getUserData();
        return dado instanceof EtapaVistoria e ? e : null;
    }

    /** Refaz o conteúdo da aba escolhida. */
    private void desenhar() {
        conteudo.getChildren().clear();
        List<FotoVistoria> todas;
        try {
            todas = servico.listar(contrato.getId());
        } catch (SQLException | RegraNegocioException e) {
            conteudo.getChildren().add(Detalhe.veredito("veredito-erro", "Não foi possível carregar as fotos: "
                    + e.getMessage()));
            return;
        }
        long entrada = todas.stream().filter(f -> f.etapa() == EtapaVistoria.ENTRADA && !f.arquivada()).count();
        long saida = todas.stream().filter(f -> f.etapa() == EtapaVistoria.SAIDA && !f.arquivada()).count();
        abaEntrada.setText("Entrada · " + entrada);
        abaSaida.setText("Saída · " + saida);
        EtapaVistoria etapa = etapaAtual();
        botaoLaudo.setVisible(etapa != null);
        botaoLaudo.setManaged(etapa != null);
        if (etapa == null) {
            desenharComparacao();
        } else {
            desenharEtapa(etapa, todas);
        }
    }

    // ---------- entrada e saída ----------

    private void desenharEtapa(EtapaVistoria etapa, List<FotoVistoria> todas) {
        Label explicacao = new Label(etapa.getExplicacao() + ". Fotos por cômodo, tiradas no dia da "
                + (etapa == EtapaVistoria.ENTRADA ? "entrega" : "devolução") + " das chaves. A data e quem registrou "
                + "são gravados pelo sistema.");
        explicacao.getStyleClass().add("texto");
        explicacao.setWrapText(true);
        conteudo.getChildren().add(explicacao);
        if (Sessao.pode(Acao.REGISTRAR_VISTORIA)) {
            conteudo.getChildren().add(barraDeEnvio(etapa));
        }
        conteudo.getChildren().addAll(avisoOk, avisoErros);

        List<FotoVistoria> ativas = todas.stream().filter(f -> f.etapa() == etapa && !f.arquivada()).toList();
        if (ativas.isEmpty()) {
            conteudo.getChildren().add(Detalhe.veredito("veredito-neutro", "Ainda não há fotos da "
                    + etapa.getDescricao().toLowerCase() + ". Escolha o cômodo e clique em \"Adicionar fotos\", "
                    + "ou arraste as fotos para esta janela."));
        }
        for (String nome : comodosEmOrdem(ativas)) {
            FlowPane grade = new FlowPane(12, 12);
            ativas.stream().filter(f -> f.comodo().equals(nome)).forEach(f -> grade.getChildren().add(cartao(f, 260)));
            conteudo.getChildren().add(new VBox(8, Detalhe.titulo(nome + " · " + grade.getChildren().size()), grade));
        }
        List<FotoVistoria> arquivadas = todas.stream().filter(f -> f.etapa() == etapa && f.arquivada()).toList();
        if (!arquivadas.isEmpty()) {
            VBox lista = new VBox(6, Detalhe.titulo("Arquivadas (enviadas por engano) · " + arquivadas.size()));
            for (FotoVistoria f : arquivadas) {
                Label nome = new Label(f.comodo() + " · " + f.nomeOriginal() + " · arquivada em "
                        + f.arquivadaEm().format(DATA_HORA));
                nome.getStyleClass().add("texto");
                HBox linha = new HBox(10, nome);
                linha.setAlignment(Pos.CENTER_LEFT);
                if (Sessao.pode(Acao.REGISTRAR_VISTORIA)) {
                    Button reativar = new Button("Reativar", new FontIcon("fth-rotate-ccw"));
                    reativar.getStyleClass().add("botao-secundario");
                    reativar.setOnAction(e -> executar(() -> servico.reativar(f.id()), "Foto reativada."));
                    linha.getChildren().add(reativar);
                }
                lista.getChildren().add(linha);
            }
            conteudo.getChildren().add(lista);
        }
        // arrastar fotos do Explorador direto para a janela
        conteudo.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles() && Sessao.pode(Acao.REGISTRAR_VISTORIA) && etapaAtual() != null) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        conteudo.setOnDragDropped(e -> {
            boolean ok = e.getDragboard().hasFiles();
            if (ok) {
                enviar(etapaAtual(), e.getDragboard().getFiles());
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    private HBox barraDeEnvio(EtapaVistoria etapa) {
        Button adicionar = new Button("Adicionar fotos…", new FontIcon("fth-camera"));
        adicionar.getStyleClass().add("botao-primario");
        adicionar.setMinWidth(Button.USE_PREF_SIZE);
        adicionar.setOnAction(e -> {
            FileChooser seletor = new FileChooser();
            seletor.setTitle("Fotos da " + etapa.getDescricao().toLowerCase());
            seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fotos (JPG ou PNG)", "*.jpg",
                    "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
            List<File> escolhidas = seletor.showOpenMultipleDialog(veu.getScene().getWindow());
            if (escolhidas != null) {
                enviar(etapa, escolhidas);
            }
        });
        HBox barra = new HBox(10, campoComodo, campoObservacao, adicionar);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void enviar(EtapaVistoria etapa, List<File> arquivos) {
        String comodo = campoComodo.getEditor().getText();
        executar(() -> {
            List<FotoVistoria> salvas = servico.adicionar(contrato.getId(), etapa, comodo, campoObservacao.getText(),
                    arquivos.stream().map(File::toPath).toList());
            campoObservacao.clear();
            textoOk.setText(salvas.size() + (salvas.size() == 1 ? " foto guardada" : " fotos guardadas") + " em "
                    + salvas.get(0).comodo() + ", com data, quem registrou e lacre.");
        }, null);
    }

    // ---------- comparar ----------

    private void desenharComparacao() {
        Label explicacao = new Label("Para cada cômodo, a foto da entrega das chaves à esquerda e a da devolução à "
                + "direita. Use para conferir o que mudou antes de devolver a caução ou cobrar um conserto.");
        explicacao.getStyleClass().add("texto");
        explicacao.setWrapText(true);
        conteudo.getChildren().add(explicacao);
        List<Comodo> comodos;
        try {
            comodos = servico.comparacao(contrato.getId());
        } catch (SQLException | RegraNegocioException e) {
            conteudo.getChildren().add(Detalhe.veredito("veredito-erro", "Não foi possível comparar: " + e.getMessage()));
            return;
        }
        if (comodos.isEmpty()) {
            conteudo.getChildren().add(Detalhe.veredito("veredito-neutro", "Ainda não há fotos para comparar."));
            return;
        }
        for (Comodo comodo : comodos) {
            HBox lados = new HBox(16, lado("Entrada", comodo.entrada()), lado("Saída", comodo.saida()));
            lados.getStyleClass().add("comparacao-comodo");
            conteudo.getChildren().add(new VBox(8, Detalhe.titulo(comodo.nome()), lados));
        }
    }

    private VBox lado(String nome, List<FotoVistoria> fotos) {
        Label rotulo = new Label(nome);
        rotulo.getStyleClass().add("subtitulo");
        VBox coluna = new VBox(8, rotulo);
        HBox.setHgrow(coluna, Priority.ALWAYS);
        coluna.setMaxWidth(Double.MAX_VALUE);
        coluna.setPrefWidth(520);
        if (fotos.isEmpty()) {
            Label vazio = new Label(nome.equals("Saída") ? "Sem fotos da saída ainda." : "Sem fotos da entrada.");
            vazio.getStyleClass().add("dica");
            coluna.getChildren().add(vazio);
        }
        FlowPane grade = new FlowPane(10, 10);
        fotos.forEach(f -> grade.getChildren().add(cartao(f, 250)));
        coluna.getChildren().add(grade);
        return coluna;
    }

    // ---------- peças ----------

    /** Uma foto com a observação, quando e por quem, o lacre e os botões. */
    private VBox cartao(FotoVistoria f, double largura) {
        ImageView imagem = new ImageView(new Image(ServicoVistorias.caminho(f).toUri().toString(), largura * 2,
                largura * 1.5, true, true, true));
        imagem.setFitWidth(largura);
        imagem.setFitHeight(largura * 0.75);
        imagem.setPreserveRatio(true);
        StackPane moldura = new StackPane(imagem);
        moldura.getStyleClass().add("moldura-foto");
        moldura.setPrefSize(largura, largura * 0.75);
        moldura.setOnMouseClicked(e -> abrirNoComputador(ServicoVistorias.caminho(f)));
        VBox cartao = new VBox(4, moldura);
        cartao.getStyleClass().add("cartao-foto");
        cartao.setPrefWidth(largura + 20);
        cartao.setMaxWidth(largura + 20);
        if (f.observacao() != null) {
            Label obs = new Label(f.observacao());
            obs.setWrapText(true);
            obs.getStyleClass().add("celula-nome");
            cartao.getChildren().add(obs);
        }
        Label quando = new Label("Registrada em " + f.registradaEm().format(DATA_HORA) + " por " + f.registradaPor()
                + (f.tiradaEm() == null ? "" : "\nTirada em " + f.tiradaEm().format(DATA_HORA)));
        quando.setWrapText(true);
        quando.getStyleClass().add("celula-detalhe");
        boolean confere = ServicoVistorias.lacreConfere(f);
        Label lacre = new Label(confere ? "✓ Lacre confere" : "⚠ Foto alterada ou sumiu depois do registro",
                new FontIcon(confere ? "fth-lock" : "fth-alert-triangle"));
        lacre.getStyleClass().add(confere ? "lacre-ok" : "lacre-alerta");
        cartao.getChildren().addAll(quando, lacre);
        if (etapaAtual() != null && Sessao.pode(Acao.REGISTRAR_VISTORIA)) {
            Button arquivar = new Button("Arquivar", new FontIcon("fth-archive"));
            arquivar.getStyleClass().add("botao-secundario");
            arquivar.setOnAction(e -> executar(() -> servico.arquivar(f.id()),
                    "Foto arquivada. Ela continua guardada e pode ser reativada."));
            cartao.getChildren().add(arquivar);
        }
        return cartao;
    }

    /** Os cômodos na ordem da lista sugerida; os digitados à mão, depois. */
    private static List<String> comodosEmOrdem(List<FotoVistoria> fotos) {
        java.util.LinkedHashSet<String> ordem = new java.util.LinkedHashSet<>();
        for (String sugerido : FotoVistoria.COMODOS) {
            if (fotos.stream().anyMatch(f -> f.comodo().equals(sugerido))) {
                ordem.add(sugerido);
            }
        }
        fotos.forEach(f -> ordem.add(f.comodo()));
        return List.copyOf(ordem);
    }

    @FXML
    private void gerarLaudo() {
        EtapaVistoria etapa = etapaAtual();
        if (etapa == null) {
            return;
        }
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar laudo de vistoria");
        seletor.setInitialFileName("Laudo " + etapa.getDescricao().toLowerCase() + " " + contrato.getNumero() + ".pdf");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = seletor.showSaveDialog(veu.getScene().getWindow());
        if (destino == null) {
            return;
        }
        executar(() -> {
            servico.laudo(contrato.getId(), etapa, destino.toPath());
            textoOk.setText("Laudo salvo em " + destino.getAbsolutePath() + ". Imprima e peça as assinaturas.");
            abrirNoComputador(destino.toPath());
        }, null);
    }

    private interface Operacao {
        void executar() throws SQLException, IOException;
    }

    /** Roda a operação, mostra o erro ou o aviso de sucesso e redesenha a aba. */
    private void executar(Operacao operacao, String sucesso) {
        try {
            operacao.executar();
            desenhar();
            if (sucesso != null) {
                textoOk.setText(sucesso);
            }
            mostrar(avisoErros, false);
            mostrar(avisoOk, true);
            if (depoisDeMudar != null) {
                depoisDeMudar.run();
            }
        } catch (RegraNegocioException e) {
            desenhar();
            textoErros.setText(String.join("\n", e.getErros()));
            mostrar(avisoOk, false);
            mostrar(avisoErros, true);
        } catch (SQLException | IOException e) {
            desenhar();
            textoErros.setText(Falha.doBanco(e));
            mostrar(avisoOk, false);
            mostrar(avisoErros, true);
        }
    }

    private static void abrirNoComputador(Path arquivo) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            new Thread(() -> {
                try {
                    Desktop.getDesktop().open(arquivo.toFile());
                } catch (IOException e) {
                    // sem programa para abrir: a foto continua visível na janela
                }
            }).start();
        }
    }

    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
