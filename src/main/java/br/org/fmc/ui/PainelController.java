package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.Tela;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoPainel;
import br.org.fmc.service.ServicoPainel.NumerosDoMes;
import br.org.fmc.service.ServicoPainel.Ocupacao;
import br.org.fmc.service.ServicoPainel.Painel;
import br.org.fmc.service.ServicoPainel.Pendencia;
import br.org.fmc.service.ServicoPainel.Tipo;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela "Painel" (ui/painel.fxml), BOARD-40 e BOARD-61.
 * Só desenha: quem calcula os números e escolhe as pendências é o ServicoPainel.
 */
public class PainelController {

    private static final Locale BRASIL = Locale.of("pt", "BR");
    private static final DateTimeFormatter DIA_POR_EXTENSO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", BRASIL);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM", BRASIL);
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy", BRASIL);

    private final ServicoPainel servico = new ServicoPainel();

    @FXML
    private Label dataDeHoje;
    @FXML
    private Button botaoAlugueis;
    @FXML
    private HBox cartoes;
    @FXML
    private VBox listaAtencao;
    @FXML
    private VBox cartaoAtrasos;
    @FXML
    private Label contagemAtrasos;
    @FXML
    private TableView<Aluguel> tabelaAtrasos;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaInquilino;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaVencimento;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaValor;

    private Painel painel;

    @FXML
    private void initialize() {
        Permissoes.mostrarSe(botaoAlugueis, Acao.VER_ALUGUEIS);
        Permissoes.mostrarSe(cartaoAtrasos, Acao.VER_ALUGUEIS);
        montarTabela();
        try {
            painel = servico.montar();
        } catch (RegraNegocioException e) {
            dataDeHoje.setText(String.join(" ", e.getErros()));
            return;
        } catch (SQLException e) {
            dataDeHoje.setText("Erro no banco de dados: " + e.getMessage());
            return;
        }
        dataDeHoje.setText(primeiraMaiuscula(painel.hoje().format(DIA_POR_EXTENSO)));
        montarCartoes();
        montarAtencao();
        tabelaAtrasos.getItems().setAll(painel.alugueisEmAtraso());
        int atrasos = painel.alugueisEmAtraso().size();
        contagemAtrasos.setText(atrasos == 0 ? "" : atrasos == 1 ? "1 aluguel" : atrasos + " aluguéis");
    }

    /** Botão "Ver aluguéis". */
    @FXML
    private void verAlugueis() {
        Navegador.irPara(Tela.ALUGUEIS);
    }

    // ---------- Números do mês (BOARD-40) ----------

    private void montarCartoes() {
        NumerosDoMes n = painel.numeros();
        if (n != null) {
            String mes = n.mes().format(MES);
            cartoes.getChildren().add(cartao("fth-dollar-sign", "Recebido em " + mes, Formatos.moeda(n.recebido().total()),
                    n.percentualRecebido() / 100.0,
                    "de " + Formatos.moeda(n.previsto().total()) + " previstos · " + n.percentualRecebido() + "%",
                    "kpi-principal"));
            cartoes.getChildren().add(cartao("fth-clock", "Em atraso, todos os meses",
                    Formatos.moeda(n.emAtraso().total()), null,
                    quantos(n.emAtraso().quantidade(), "aluguel", "aluguéis")
                            + (n.emAtraso().quantidade() == 0 ? ""
                            : " · " + Formatos.moeda(n.atrasoComEncargos()) + " com multa e juros"),
                    n.emAtraso().quantidade() == 0 ? null : "kpi-perigo"));
            cartoes.getChildren().add(cartao("fth-calendar", "A receber em " + mes, Formatos.moeda(n.aReceber().total()),
                    null, quantos(n.aReceber().quantidade(), "aluguel", "aluguéis") + " no prazo ou na tolerância",
                    null));
        }
        Ocupacao o = painel.ocupacao();
        if (o != null) {
            cartoes.getChildren().add(cartao("fth-home", "Ocupação", o.ocupados() + " de " + o.imoveis() + " imóveis",
                    o.imoveis() == 0 ? 0 : (double) o.ocupados() / o.imoveis(),
                    quantos(o.vagos(), "vago", "vagos")
                            + (o.emManutencao() == 0 ? "" : " · " + o.emManutencao() + " em manutenção"),
                    null));
        }
        mostrar(cartoes, !cartoes.getChildren().isEmpty());
    }

    /**
     * Um cartão de número, como o KpiCard do design system: rótulo com ícone, valor grande,
     * barra de progresso (opcional) e uma linha de detalhe.
     *
     * @param progresso de 0 a 1, ou null para não ter barra
     * @param tom       "kpi-principal" (azul escuro), "kpi-perigo" (vermelho) ou null
     */
    private static VBox cartao(String icone, String rotulo, String valor, Double progresso, String meta, String tom) {
        StackPane caixaIcone = new StackPane(new FontIcon(icone));
        caixaIcone.getStyleClass().add("kpi-icone");
        Label nome = new Label(rotulo);
        nome.getStyleClass().add("kpi-rotulo");
        nome.setWrapText(true);
        nome.setMinHeight(Region.USE_PREF_SIZE);
        HBox topo = new HBox(8, caixaIcone, nome);
        topo.setAlignment(Pos.CENTER_LEFT);

        Label numero = new Label(valor);
        numero.getStyleClass().add("kpi-valor");
        numero.setMinWidth(Region.USE_PREF_SIZE);

        VBox cartao = new VBox(12, topo, numero);
        if (progresso != null) {
            Region preenchido = new Region();
            preenchido.getStyleClass().add("kpi-barra-preenchida");
            StackPane barra = new StackPane(preenchido);
            barra.getStyleClass().add("kpi-barra");
            StackPane.setAlignment(preenchido, Pos.CENTER_LEFT);
            double fracao = Math.max(0, Math.min(1, progresso));
            preenchido.maxWidthProperty().bind(barra.widthProperty().multiply(fracao));
            cartao.getChildren().add(barra);
        }
        Label detalhe = new Label(meta);
        detalhe.getStyleClass().add("kpi-meta");
        detalhe.setWrapText(true);
        detalhe.setMinHeight(Region.USE_PREF_SIZE);
        cartao.getChildren().add(detalhe);

        cartao.getStyleClass().add("kpi");
        if (tom != null) {
            cartao.getStyleClass().add(tom);
        }
        cartao.setMaxWidth(Double.MAX_VALUE);
        cartao.setPrefWidth(0); // os cartões dividem a largura em partes iguais
        HBox.setHgrow(cartao, Priority.ALWAYS);
        return cartao;
    }

    // ---------- Precisa de atenção (BOARD-61) ----------

    private void montarAtencao() {
        if (painel.pendencias().isEmpty()) {
            FontIcon icone = new FontIcon("fth-check-circle");
            icone.getStyleClass().add("atencao-ok");
            Label titulo = new Label("Tudo em dia");
            titulo.getStyleClass().add("tabela-vazia-titulo");
            Label texto = new Label("Nenhum aluguel atrasado, contrato vencido ou imóvel vago.");
            texto.getStyleClass().add("texto");
            texto.setWrapText(true);
            VBox vazio = new VBox(8, icone, titulo, texto);
            vazio.setAlignment(Pos.CENTER);
            vazio.getStyleClass().add("tabela-vazia");
            listaAtencao.getChildren().setAll(vazio);
            return;
        }
        for (Pendencia p : painel.pendencias()) {
            listaAtencao.getChildren().add(itemAtencao(p));
        }
    }

    private static HBox itemAtencao(Pendencia p) {
        Label numero = new Label(String.valueOf(p.quantidade()));
        numero.getStyleClass().addAll("atencao-numero", switch (p.gravidade()) {
            case ALTA -> "atencao-alta";
            case MEDIA -> "atencao-media";
            case INFO -> "atencao-info";
        });
        Label titulo = new Label(p.titulo());
        titulo.getStyleClass().add("atencao-titulo");
        titulo.setWrapText(true);
        Label detalhe = new Label(p.detalhe());
        detalhe.getStyleClass().add("atencao-detalhe");
        detalhe.setWrapText(true);
        detalhe.setMinHeight(Region.USE_PREF_SIZE);
        VBox textos = new VBox(2, titulo, detalhe);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button abrir = new Button("Abrir", new FontIcon("fth-chevron-right"));
        abrir.setContentDisplay(ContentDisplay.RIGHT);
        abrir.getStyleClass().addAll("botao-secundario", "botao-pequeno");
        abrir.setMinWidth(Region.USE_PREF_SIZE);
        abrir.setOnAction(e -> Navegador.irPara(p.destino(), filtroDoDestino(p.tipo())));
        mostrar(abrir, Sessao.podeAbrir(p.destino()));

        HBox item = new HBox(16, numero, textos, abrir);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("atencao-item");
        return item;
    }

    /** O "Abrir" leva à tela já filtrada só no que pede atenção (ex.: Aluguéis na aba "Em atraso"). */
    private static Object filtroDoDestino(Tipo tipo) {
        return switch (tipo) {
            case ALUGUEIS_EM_ATRASO -> SituacaoAluguel.EM_ATRASO;
            case INQUILINOS_PARA_COBRANCA -> AlugueisController.Recorte.INQUILINOS_COM_MAIS_DE_UM_MES_EM_ATRASO;
            case ALUGUEIS_EM_TOLERANCIA -> SituacaoAluguel.EM_TOLERANCIA;
            case CONTRATOS_VENCIDOS -> ContratosController.Recorte.VENCIDOS;
            case CONTRATOS_VENCENDO -> ContratosController.Recorte.VENCENDO;
            case IMOVEIS_VAGOS -> SituacaoImovel.VAGO;
        };
    }

    // ---------- Aluguéis em atraso (BOARD-61) ----------

    private void montarTabela() {
        colunaInquilino.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaInquilino.setCellFactory(c -> celula(a -> duasLinhas(a.getContrato().getInquilino().getNome(),
                a.getContrato().getImovel().getCodigo() + " · ref. " + a.getCompetencia().format(MES_ANO))));
        colunaVencimento.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaVencimento.setCellFactory(c -> celula(a -> duasLinhas(Formatos.data(a.getVencimento()),
                "há " + quantos(a.diasAtraso(painel.hoje()), "dia", "dias"))));
        colunaValor.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaValor.setCellFactory(c -> {
            TableCell<Aluguel, Aluguel> celula = celula(a -> {
                VBox valores = duasLinhas(Formatos.moeda(a.base().add(a.encargosEm(painel.hoje()).total())),
                        "de " + Formatos.moeda(a.base()));
                valores.setAlignment(Pos.CENTER_RIGHT);
                return valores;
            });
            celula.getStyleClass().add("celula-numero");
            return celula;
        });

        Label titulo = new Label("Nenhum aluguel em atraso");
        titulo.getStyleClass().add("tabela-vazia-titulo");
        Label dica = new Label("Quem passar dos 5 dias de tolerância aparece aqui, do atraso mais antigo para o mais novo.");
        dica.getStyleClass().add("texto");
        dica.setWrapText(true);
        VBox vazio = new VBox(8, new FontIcon("fth-smile"), titulo, dica);
        vazio.setAlignment(Pos.CENTER);
        vazio.getStyleClass().add("tabela-vazia");
        tabelaAtrasos.setPlaceholder(vazio);
    }

    private static TableCell<Aluguel, Aluguel> celula(Function<Aluguel, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Aluguel a, boolean vazio) {
                super.updateItem(a, vazio);
                setGraphic(vazio || a == null ? null : desenho.apply(a));
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

    private static String quantos(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    private static String primeiraMaiuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }
}
