package br.org.fmc.ui;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAlugueis;
import br.org.fmc.service.ServicoCobranca;
import br.org.fmc.service.ServicoCobranca.Linha;
import br.org.fmc.service.ServicoRecebimento;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela "Aluguéis" (ui/alugueis.fxml), BOARD-38.
 * Só cuida da tela: a situação de cada aluguel e os filtros ficam no ServicoAlugueis.
 */
public class AlugueisController {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
            Locale.forLanguageTag("pt-BR"));

    /**
     * Opção do filtro de mês que mostra os aluguéis em aberto de todos os meses (é como a tela
     * abre quando vem do Painel, porque um atraso pode ser de meses atrás). Não é um mês de verdade.
     */
    private static final YearMonth TODOS_EM_ABERTO = YearMonth.of(1, 1);

    /** Recortes que o Painel pede ao abrir esta tela, além das abas por situação. */
    public enum Recorte {
        /** Lista de cobrança: uma linha por inquilino com 2 ou mais aluguéis atrasados. */
        INQUILINOS_PARA_COBRANCA
    }

    private final ServicoAlugueis servico = new ServicoAlugueis();
    private final ServicoCobranca servicoCobranca = new ServicoCobranca();

    private boolean modoCobranca;

    /** Todos os aluguéis do mês escolhido; a tabela mostra só os que passam nos filtros. */
    private List<Aluguel> doMes = List.of();

    /** A data de hoje, que define a situação. Fixada ao abrir a tela. */
    private LocalDate hoje;
    private Map<SituacaoAluguel, ServicoAlugueis.Total> resumoAtual = Map.of();

    @FXML
    private Label contagem;
    @FXML
    private HBox abasVisualizacao;
    @FXML
    private ToggleGroup grupoVisualizacao;
    @FXML
    private ToggleButton abaAlugueis;
    @FXML
    private ToggleButton abaCobranca;
    @FXML
    private HBox barraFiltrosMes;
    @FXML
    private VBox resumoCobranca;
    @FXML
    private Label contagemDoisMeses;
    @FXML
    private Label contagemTresMeses;
    @FXML
    private Label contagemQuatroMeses;
    @FXML
    private Label valorDoisMeses;
    @FXML
    private Label valorTresMeses;
    @FXML
    private Label valorQuatroMeses;
    @FXML
    private VBox resumoMes;
    @FXML
    private Label tituloResumoMes;
    @FXML
    private Label textoResumoMes;
    @FXML
    private HBox barraSituacoes;
    @FXML
    private Region barraPagos;
    @FXML
    private Region barraAVencer;
    @FXML
    private Region barraTolerancia;
    @FXML
    private Region barraAtraso;
    @FXML
    private Label quantidadePagos;
    @FXML
    private Label quantidadeAVencer;
    @FXML
    private Label quantidadeTolerancia;
    @FXML
    private Label quantidadeAtraso;
    @FXML
    private Label valorPagos;
    @FXML
    private Label valorAVencer;
    @FXML
    private Label valorTolerancia;
    @FXML
    private Label valorAtraso;
    @FXML
    private ComboBox<YearMonth> campoMes;
    @FXML
    private Button mesAnterior;
    @FXML
    private Button mesSeguinte;
    @FXML
    private TextField campoBusca;
    @FXML
    private ToggleGroup grupoSituacao;
    @FXML
    private ToggleButton abaAtraso;
    @FXML
    private ToggleButton abaTolerancia;
    @FXML
    private ToggleButton abaAVencer;
    @FXML
    private ToggleButton abaPagos;
    @FXML
    private ToggleButton abaTodos;
    @FXML
    private TableView<Aluguel> tabela;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaInquilino;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaContrato;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaVencimento;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaValor;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaSituacao;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaDemonstrativo;
    @FXML
    private HBox abas;
    @FXML
    private TableView<Linha> tabelaCobranca;
    @FXML
    private TableColumn<Linha, Linha> cobrancaInquilino;
    @FXML
    private TableColumn<Linha, Linha> cobrancaMeses;
    @FXML
    private TableColumn<Linha, Linha> cobrancaOriginal;
    @FXML
    private TableColumn<Linha, Linha> cobrancaAtualizado;
    @FXML
    private TableColumn<Linha, Linha> cobrancaFiador;
    @FXML
    private TableColumn<Linha, Linha> cobrancaUltimoContato;
    @FXML
    private TableColumn<Linha, Linha> cobrancaAcao;

    @FXML
    private void initialize() {
        hoje = servico.hoje();
        montarTabela();
        montarTabelaCobranca();
        barraSituacoes.widthProperty().addListener((obs, antes, agora) -> ajustarBarraSituacoes());

        // Abas por situação: cada uma guarda a situação que filtra; "Todos" guarda null.
        abaAtraso.setUserData(SituacaoAluguel.EM_ATRASO);
        abaTolerancia.setUserData(SituacaoAluguel.EM_TOLERANCIA);
        abaAVencer.setUserData(SituacaoAluguel.A_VENCER);
        abaPagos.setUserData(SituacaoAluguel.PAGO);
        abaTodos.setUserData(null);
        grupoSituacao.selectToggle(abaTodos);
        // Sempre uma aba marcada: clicar na que já está marcada não desmarca.
        grupoSituacao.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                grupoSituacao.selectToggle(antes);
            } else {
                aplicarFiltros();
            }
        });

        abaAlugueis.setUserData(false);
        abaCobranca.setUserData(true);
        grupoVisualizacao.selectToggle(abaAlugueis);
        grupoVisualizacao.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                grupoVisualizacao.selectToggle(antes);
                return;
            }
            modoCobranca = Boolean.TRUE.equals(agora.getUserData());
            if (modoCobranca && !TODOS_EM_ABERTO.equals(campoMes.getValue())) {
                campoMes.setValue(TODOS_EM_ABERTO);
            } else if (!modoCobranca && TODOS_EM_ABERTO.equals(campoMes.getValue())) {
                campoMes.setValue(YearMonth.from(hoje));
            } else {
                aplicarFiltros();
            }
        });

        campoMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth mes) {
                return mes == null ? "" : nomeDoMes(mes);
            }

            @Override
            public YearMonth fromString(String texto) {
                return null; // a lista não é digitável
            }
        });
        campoMes.valueProperty().addListener((obs, antes, agora) -> {
            atualizarSetasDoMes();
            carregarMes();
        });
        campoBusca.textProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        campoBusca.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                limparFiltros();
            }
        });

        try {
            List<YearMonth> meses = servico.mesesDisponiveis();
            campoMes.getItems().setAll(meses);
            campoMes.getItems().add(0, TODOS_EM_ABERTO);
            // Vindo do Painel (ex.: "Aluguéis em atraso"): abre na aba pedida, com todos os meses em aberto.
            Object filtro = Navegador.filtroRecebido();
            if (filtro instanceof SituacaoAluguel pedida) {
                grupoSituacao.getToggles().stream().filter(aba -> aba.getUserData() == pedida)
                        .findFirst().ifPresent(grupoSituacao::selectToggle);
                campoMes.setValue(TODOS_EM_ABERTO);
            } else if (filtro instanceof Recorte) {
                grupoVisualizacao.selectToggle(abaCobranca);
            } else {
                campoMes.setValue(YearMonth.from(hoje)); // abre no mês atual
            }
        } catch (RegraNegocioException e) {
            contagem.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            contagem.setText("Não foi possível carregar os aluguéis do banco de dados.");
        }
    }

    private void montarTabela() {
        colunaInquilino.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaInquilino.setCellFactory(c -> celula(a -> {
            String unidade = a.getContrato().getImovel().getUnidade();
            return duasLinhas(a.getContrato().getInquilino().getNome(),
                    a.getContrato().getImovel().getCodigo() + (unidade == null ? "" : " · " + unidade), null);
        }));
        colunaContrato.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaContrato.setCellFactory(c -> celula(a -> new Label(a.getContrato().getNumero())));
        // Vencimento: a data e, embaixo, o que ela significa hoje (dias de atraso em vermelho).
        colunaVencimento.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaVencimento.setCellFactory(c -> celula(a -> {
            SituacaoAluguel s = a.getSituacao(hoje);
            long dias = a.diasAtraso(hoje);
            String detalhe = switch (s) {
                case PAGO -> "Pago em " + Formatos.data(a.getDataPagamento());
                case A_VENCER -> a.getVencimento().equals(hoje) ? "Vence hoje" : "Vence em " + diasAte(a) + " dias";
                case EM_TOLERANCIA, EM_ATRASO -> dias + (dias == 1 ? " dia de atraso" : " dias de atraso");
            };
            return duasLinhas(Formatos.data(a.getVencimento()), detalhe,
                    s == SituacaoAluguel.EM_ATRASO ? "celula-detalhe-erro" : null);
        }));
        colunaValor.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaValor.setCellFactory(c -> {
            TableCell<Aluguel, Aluguel> celula = celula(a -> new Label(Formatos.moeda(a.base())));
            celula.getStyleClass().add("celula-numero"); // valores alinhados à direita
            return celula;
        });
        colunaSituacao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaSituacao.setCellFactory(c -> celula(a -> selo(a.getSituacao(hoje))));
        colunaDemonstrativo.setVisible(Sessao.pode(Acao.RECEBER_ALUGUEL) || Sessao.pode(Acao.GERAR_ALUGUEIS));
        colunaDemonstrativo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaDemonstrativo.setCellFactory(c -> celula(a -> {
            if (a.estaPago()) {
                Label recibo = new Label(a.getNumeroRecibo() == null ? "Pago" : a.getNumeroRecibo());
                recibo.getStyleClass().add("selo");
                return recibo;
            }
            HBox acoes = new HBox(8);
            acoes.setAlignment(Pos.CENTER_LEFT);
            if (Sessao.pode(Acao.RECEBER_ALUGUEL)) {
                Button receber = new Button("Receber");
                receber.getStyleClass().addAll("botao-primario", "botao-pequeno");
                receber.setOnAction(e -> abrirRecebimento(a));
                acoes.getChildren().add(receber);
            }
            if (Sessao.pode(Acao.GERAR_ALUGUEIS)) {
                // Demonstrativo em PDF para mandar ao inquilino (simulação, sem valor de boleto).
                Button salvar = new Button("PDF", new FontIcon("fth-download"));
                salvar.getStyleClass().addAll("botao-secundario", "botao-pequeno");
                salvar.setTooltip(new Tooltip("Baixar o demonstrativo da cobrança em PDF (simulação, sem valor para pagamento)"));
                salvar.setOnAction(e -> salvarDemonstrativo(a));
                acoes.getChildren().add(salvar);
            }
            return acoes;
        }));
    }

    private void abrirRecebimento(Aluguel aluguel) {
        ReceberAluguelController.abrir(aluguel, new ServicoRecebimento(), recibo -> carregarMes());
    }

    private void salvarDemonstrativo(Aluguel aluguel) {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar demonstrativo de cobrança (simulação)");
        seletor.setInitialFileName("demonstrativo-" + aluguel.getContrato().getNumero() + "-"
                + aluguel.getCompetencia() + ".pdf");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = seletor.showSaveDialog(tabela.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            Path caminho = destino.toPath();
            if (!caminho.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                caminho = caminho.resolveSibling(caminho.getFileName() + ".pdf");
            }
            servico.gerarDemonstrativo(aluguel, hoje, caminho);
            Alert aviso = new Alert(Alert.AlertType.INFORMATION,
                    "O PDF foi salvo. Ele está marcado como simulação e não pode ser usado para pagamento.");
            aviso.setTitle("Demonstrativo salvo");
            aviso.setHeaderText("Documento fictício gerado");
            aviso.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Não foi possível salvar o PDF escolhido.").showAndWait();
        } catch (RegraNegocioException e) {
            new Alert(Alert.AlertType.WARNING, String.join(" ", e.getErros())).showAndWait();
        }
    }

    /** Lista de cobrança: quem é, quantos aluguéis deve, há quanto tempo e quanto deve hoje. */
    private void montarTabelaCobranca() {
        cobrancaInquilino.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaInquilino.setCellFactory(c -> celula(linha -> {
            String telefone = linha.contrato().getInquilino().getTelefone();
            String imovel = linha.contrato().getImovel().getCodigo() + " · "
                    + (telefone == null ? "sem telefone" : telefone);
            VBox caixa = duasLinhas(linha.contrato().getInquilino().getNome(), imovel, null);
            String unidade = linha.contrato().getImovel().getUnidade();
            Tooltip.install(caixa, new Tooltip(linha.contrato().getNumero() + " · "
                    + linha.contrato().getImovel().getEndereco()
                    + (unidade == null ? "" : " · " + unidade)));
            return caixa;
        }));
        cobrancaMeses.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaMeses.setCellFactory(c -> celula(linha -> duasLinhas(meses(linha.alugueis()),
                linha.mesesEmAberto() + (linha.mesesEmAberto() == 1 ? " mês" : " meses"), null)));
        cobrancaOriginal.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaOriginal.setCellFactory(c -> {
            TableCell<Linha, Linha> celula = celula(linha -> new Label(Formatos.moeda(linha.valorOriginal())));
            celula.getStyleClass().add("celula-numero");
            return celula;
        });
        cobrancaAtualizado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaAtualizado.setCellFactory(c -> {
            TableCell<Linha, Linha> celula = celula(linha -> new Label(Formatos.moeda(linha.valorAtualizado())));
            celula.getStyleClass().add("celula-numero");
            return celula;
        });
        cobrancaFiador.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaFiador.setCellFactory(c -> celula(linha -> {
            Fiador fiador = linha.fiador();
            if (fiador == null) {
                return duasLinhas("Caução", Formatos.moeda(linha.contrato().getGarantia() instanceof br.org.fmc.model.Caucao caucao
                        ? caucao.getValor() : BigDecimal.ZERO), null);
            }
            return duasLinhas(fiador.getNome(), fiador.getTelefone() == null ? "sem telefone" : fiador.getTelefone(), null);
        }));
        cobrancaUltimoContato.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        // [D7] BOARD-64: em que pé está a cobrança (nunca contatado, prometeu para..., promessa não cumprida).
        cobrancaUltimoContato.setCellFactory(c -> celula(linha -> {
            ServicoCobranca.Acompanhamento a = linha.acompanhamento();
            String estilo = switch (a.situacao()) {
                case PROMESSA_NAO_CUMPRIDA -> "celula-detalhe-erro";
                case PROMESSA_VENCENDO -> "celula-detalhe-alerta";
                case PROMESSA_NO_PRAZO -> "celula-detalhe-ok";
                case SEM_RETORNO, NUNCA_CONTATADO -> null;
            };
            VBox celula = duasLinhas(a.texto(), a.detalhe(), estilo);
            if (linha.ultimoContato() != null) {
                Tooltip.install(celula, new Tooltip(linha.ultimoContato().descricao()));
            }
            return celula;
        }));
        cobrancaAcao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaAcao.setCellFactory(c -> celula(linha -> {
            Button contato = new Button("Contato", new FontIcon("fth-phone"));
            contato.setTooltip(new Tooltip("Registrar contato de cobrança"));
            contato.getStyleClass().addAll("botao-secundario", "botao-pequeno");
            contato.setVisible(Sessao.pode(Acao.REGISTRAR_CONTATO_COBRANCA));
            contato.setManaged(Sessao.pode(Acao.REGISTRAR_CONTATO_COBRANCA));
            contato.setOnAction(e -> abrirContato(linha));
            return contato;
        }));
        tabelaCobranca.setRowFactory(t -> {
            TableRow<Linha> linha = new TableRow<>();
            linha.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !linha.isEmpty()) {
                    abrirContato(linha.getItem());
                }
            });
            return linha;
        });
    }

    private void abrirContato(Linha linha) {
        RegistrarContatoController.abrir(linha, servicoCobranca, contato -> carregarMes());
    }

    /** "ago, set e out" (com o ano quando não é o ano de hoje: "dez/25, jan e fev"). */
    private String meses(List<Aluguel> alugueis) {
        List<String> nomes = alugueis.stream().map(a -> {
            YearMonth m = a.getCompetencia();
            String nome = m.getMonth().getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-BR")).replace(".", "");
            return m.getYear() == hoje.getYear() ? nome : nome + "/" + String.valueOf(m.getYear()).substring(2);
        }).toList();
        if (nomes.size() == 1) {
            return nomes.get(0);
        }
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }

    private long diasAte(Aluguel a) {
        return java.time.temporal.ChronoUnit.DAYS.between(hoje, a.getVencimento());
    }

    /** Ao trocar de mês: busca os aluguéis do mês no banco. */
    private void carregarMes() {
        YearMonth mes = campoMes.getValue();
        if (mes == null) {
            return;
        }
        try {
            doMes = modoCobranca || mes.equals(TODOS_EM_ABERTO)
                    ? servico.listarEmAberto() : servico.listarDoMes(mes);
            aplicarFiltros();
        } catch (RegraNegocioException e) {
            contagem.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            contagem.setText("Não foi possível carregar os aluguéis do banco de dados.");
        }
    }

    /** Mostra só os aluguéis da aba e da busca, e atualiza as contagens. */
    private void aplicarFiltros() {
        YearMonth mes = campoMes.getValue();
        if (mes == null) {
            return;
        }
        barraFiltrosMes.setVisible(!modoCobranca);
        barraFiltrosMes.setManaged(!modoCobranca);
        mostrar(resumoCobranca, modoCobranca);
        mostrar(resumoMes, !modoCobranca);
        mostrar(abas, !modoCobranca);
        mostrar(tabela, !modoCobranca);
        mostrar(tabelaCobranca, modoCobranca);

        if (modoCobranca) {
            try {
                List<Linha> linhas = servicoCobranca.listarParaCobranca();
                atualizarResumoCobranca(linhas);
                abaCobranca.setText("Para cobrança (" + linhas.size() + ")");
                tabelaCobranca.getItems().setAll(linhas);
                contagem.setText(linhas.size() + (linhas.size() == 1 ? " inquilino para cobrança"
                        : " inquilinos para cobrança") + " · valores atualizados em " + Formatos.data(hoje));
                tabelaCobranca.setPlaceholder(vazio("fth-check-circle", "Ninguém para cobrança",
                        "Não há contratos com dois ou mais aluguéis vencidos em aberto."));
            } catch (RegraNegocioException e) {
                contagem.setText(String.join(" ", e.getErros()));
            } catch (SQLException e) {
                contagem.setText("Não foi possível carregar a lista de cobrança.");
            }
            return;
        }

        abaAlugueis.setText("Aluguéis");
        String busca = campoBusca.getText();
        List<Aluguel> base = doMes;
        // As contagens das abas respeitam a busca, para a pessoa ver quantos há em cada situação.
        List<Aluguel> daBusca = ServicoAlugueis.filtrar(base, null, busca, hoje);
        Map<SituacaoAluguel, ServicoAlugueis.Total> totais = ServicoAlugueis.totais(daBusca, hoje);
        resumoAtual = totais;
        atualizarResumoMensal(mes, daBusca, totais);
        abaAtraso.setText("Em atraso · " + totais.get(SituacaoAluguel.EM_ATRASO).quantidade());
        abaTolerancia.setText("Em tolerância · " + totais.get(SituacaoAluguel.EM_TOLERANCIA).quantidade());
        abaAVencer.setText("A vencer · " + totais.get(SituacaoAluguel.A_VENCER).quantidade());
        abaPagos.setText("Pagos · " + totais.get(SituacaoAluguel.PAGO).quantidade());
        abaTodos.setText("Todos · " + daBusca.size());

        SituacaoAluguel situacao = (SituacaoAluguel) grupoSituacao.getSelectedToggle().getUserData();
        tabela.getItems().setAll(ServicoAlugueis.filtrar(base, situacao, busca, hoje));

        // Resumo do mês embaixo do título: quantos aluguéis, quanto já entrou e quanto falta.
        Map<SituacaoAluguel, ServicoAlugueis.Total> doMesTodo = ServicoAlugueis.totais(doMes, hoje);
        BigDecimal pago = doMesTodo.get(SituacaoAluguel.PAGO).valor();
        BigDecimal emAberto = doMes.stream().map(Aluguel::base).reduce(BigDecimal.ZERO, BigDecimal::add).subtract(pago);
        String nome = nomeDoMes(mes);
        boolean todosEmAberto = mes.equals(TODOS_EM_ABERTO);
        if (todosEmAberto) {
            contagem.setText(nome + " · " + doMes.size() + (doMes.size() == 1 ? " aluguel" : " aluguéis") + " · "
                    + Formatos.moeda(emAberto) + " em aberto");
        } else {
            contagem.setText(doMes.isEmpty() ? nome + " · nenhum aluguel gerado ainda"
                    : nome + " · " + doMes.size() + (doMes.size() == 1 ? " aluguel" : " aluguéis") + " · "
                            + Formatos.moeda(pago) + " recebidos · " + Formatos.moeda(emAberto) + " em aberto");
        }

        boolean filtrando = situacao != null || !busca.isBlank();
        tabela.setPlaceholder(todosEmAberto && doMes.isEmpty()
                ? vazio("fth-check-circle", "Nenhum aluguel em aberto", "Todos os aluguéis gerados já foram pagos.")
                : doMes.isEmpty()
                ? vazio("fth-calendar", "Nenhum aluguel gerado para " + mes.format(MES),
                        "Os aluguéis do mês aparecem aqui depois de gerados.")
                : vazio(filtrando ? "fth-search" : "fth-check-circle",
                        filtrando ? "Nenhum aluguel com esses filtros" : "Nenhum aluguel",
                        filtrando ? "Escolha outra aba ou clique em \"Limpar filtros\"." : ""));
    }

    private void atualizarResumoCobranca(List<Linha> linhas) {
        List<Linha> dois = linhas.stream().filter(l -> l.mesesEmAberto() == 2).toList();
        List<Linha> tres = linhas.stream().filter(l -> l.mesesEmAberto() == 3).toList();
        List<Linha> quatro = linhas.stream().filter(l -> l.mesesEmAberto() >= 4).toList();
        contagemDoisMeses.setText("2 meses · " + dois.size());
        valorDoisMeses.setText(Formatos.moeda(somarAtualizados(dois)));
        contagemTresMeses.setText("3 meses · " + tres.size());
        valorTresMeses.setText(Formatos.moeda(somarAtualizados(tres)));
        contagemQuatroMeses.setText("4 ou mais · " + quatro.size());
        valorQuatroMeses.setText(Formatos.moeda(somarAtualizados(quatro)));
    }

    private static BigDecimal somarAtualizados(List<Linha> linhas) {
        return linhas.stream().map(Linha::valorAtualizado).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void atualizarResumoMensal(YearMonth mes, List<Aluguel> lista,
            Map<SituacaoAluguel, ServicoAlugueis.Total> totais) {
        tituloResumoMes.setText("Situação de " + nomeDoMes(mes));
        BigDecimal previsto = lista.stream().map(Aluguel::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal recebido = totais.get(SituacaoAluguel.PAGO).valor();
        textoResumoMes.setText(lista.size() + (lista.size() == 1 ? " aluguel · previsto " : " aluguéis · previsto ")
                + Formatos.moeda(previsto) + " · recebido " + Formatos.moeda(recebido));
        preencherResumoStatus(SituacaoAluguel.PAGO, quantidadePagos, valorPagos, totais);
        preencherResumoStatus(SituacaoAluguel.A_VENCER, quantidadeAVencer, valorAVencer, totais);
        preencherResumoStatus(SituacaoAluguel.EM_TOLERANCIA, quantidadeTolerancia, valorTolerancia, totais);
        preencherResumoStatus(SituacaoAluguel.EM_ATRASO, quantidadeAtraso, valorAtraso, totais);
        ajustarBarraSituacoes();
    }

    private static void preencherResumoStatus(SituacaoAluguel situacao, Label quantidade, Label valor,
            Map<SituacaoAluguel, ServicoAlugueis.Total> totais) {
        ServicoAlugueis.Total total = totais.get(situacao);
        quantidade.setText(Integer.toString(total.quantidade()));
        valor.setText(Formatos.moeda(total.valor()));
    }

    private void ajustarBarraSituacoes() {
        Region[] segmentos = {barraPagos, barraAVencer, barraTolerancia, barraAtraso};
        SituacaoAluguel[] situacoes = {SituacaoAluguel.PAGO, SituacaoAluguel.A_VENCER,
            SituacaoAluguel.EM_TOLERANCIA, SituacaoAluguel.EM_ATRASO};
        int[] quantidades = new int[segmentos.length];
        int total = 0;
        int comAlgum = 0;
        for (int i = 0; i < segmentos.length; i++) {
            quantidades[i] = resumoAtual.getOrDefault(situacoes[i],
                    new ServicoAlugueis.Total(0, BigDecimal.ZERO)).quantidade();
            total += quantidades[i];
            comAlgum += quantidades[i] > 0 ? 1 : 0;
        }
        // A barra nunca pede mais espaço que o cartão: as partes vazias saem (sem deixar o espaço
        // entre elas) e as outras dividem a largura que sobra, na proporção de cada situação.
        barraSituacoes.setMinWidth(0);
        barraSituacoes.setPrefWidth(0);
        double largura = Math.max(0, barraSituacoes.getWidth() - barraSituacoes.getSpacing() * Math.max(0, comAlgum - 1) - 1);
        for (int i = 0; i < segmentos.length; i++) {
            double parte = total == 0 ? 0 : Math.floor(largura * quantidades[i] / total);
            mostrar(segmentos[i], quantidades[i] > 0);
            segmentos[i].setMinWidth(0);
            segmentos[i].setPrefWidth(parte);
            segmentos[i].setMaxWidth(parte);
        }
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }

    /**
     * Botão "Lista de cobrança" ([D6] BOARD-44): abre a mesma lista que o Painel abre,
     * com os aluguéis em aberto de todos os meses.
     */
    @FXML
    private void abrirCobranca() {
        grupoVisualizacao.selectToggle(abaCobranca);
    }

    /*
     * Seletor de mês "‹ Novembro de 2026 ›": as setas andam um mês, o nome abre a lista de meses
     * e "Mês atual" volta para hoje. A lista vem do mais novo para o mais antigo, com
     * "Em aberto, todos os meses" na posição 0.
     */
    @FXML
    private void irMesAnterior() {
        andarMes(+1);
    }

    @FXML
    private void irMesSeguinte() {
        andarMes(-1);
    }

    @FXML
    private void irMesAtual() {
        campoMes.setValue(YearMonth.from(hoje));
    }

    private void andarMes(int passo) {
        int i = campoMes.getItems().indexOf(campoMes.getValue()) + passo;
        if (i >= 1 && i < campoMes.getItems().size()) {
            campoMes.setValue(campoMes.getItems().get(i));
        }
    }

    private void atualizarSetasDoMes() {
        int i = campoMes.getItems().indexOf(campoMes.getValue());
        mesAnterior.setDisable(i < 1 || i >= campoMes.getItems().size() - 1);
        mesSeguinte.setDisable(i <= 1);
    }

    @FXML
    private void limparFiltros() {
        campoBusca.clear();
        grupoSituacao.selectToggle(abaTodos);
        campoBusca.requestFocus();
    }

    /** "outubro de 2026" vira "Outubro de 2026". */
    private static String nomeDoMes(YearMonth mes) {
        if (mes.equals(TODOS_EM_ABERTO)) {
            return "Em aberto, todos os meses";
        }
        String texto = mes.format(MES);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** A vencer (azul), em tolerância (amarelo), em atraso (vermelho) ou pago (verde). */
    private static Label selo(SituacaoAluguel s) {
        String estilo = switch (s) {
            case A_VENCER -> "selo-info";
            case EM_TOLERANCIA -> "selo-alerta";
            case EM_ATRASO -> "selo-erro";
            case PAGO -> "selo-sucesso";
        };
        Label selo = new Label(s.getDescricao());
        selo.getStyleClass().addAll("selo", estilo);
        return selo;
    }

    /** Célula de tabela que desenha um componente a partir do item da linha. */
    private static <T> TableCell<T, T> celula(Function<T, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T a, boolean vazio) {
                super.updateItem(a, vazio);
                setGraphic(vazio || a == null ? null : desenho.apply(a));
            }
        };
    }

    private static VBox duasLinhas(String principal, String detalhe, String estiloDetalhe) {
        Label linha1 = new Label(principal);
        linha1.getStyleClass().add("celula-nome");
        Label linha2 = new Label(detalhe);
        linha2.getStyleClass().add(estiloDetalhe != null ? estiloDetalhe : "celula-detalhe");
        return new VBox(2, linha1, linha2);
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
}
