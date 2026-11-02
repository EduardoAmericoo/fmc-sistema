package br.org.fmc.ui;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Acao;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.RecebimentoAluguel;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAlugueis;
import br.org.fmc.service.ServicoAlugueis.Cobranca;
import br.org.fmc.service.ServicoBoletoFicticio;
import br.org.fmc.service.Sessao;
import java.io.File;
import java.nio.file.Files;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
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
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DatePicker;
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
import javafx.scene.layout.VBox;
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

    /** O recorte em uso, ou null. */
    private Recorte recorte;

    /** Todos os aluguéis do mês escolhido; a tabela mostra só os que passam nos filtros. */
    private List<Aluguel> doMes = List.of();

    /** A data de hoje, que define a situação. Fixada ao abrir a tela. */
    private LocalDate hoje;

    @FXML
    private Label contagem;
    @FXML
    private HBox faixaRecorte;
    @FXML
    private Label textoRecorte;
    @FXML
    private ComboBox<YearMonth> campoMes;
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
    private HBox abas;
    @FXML
    private TableView<Cobranca> tabelaCobranca;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaInquilino;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaAtrasados;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaMaisAntigo;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaGarantia;
    @FXML
    private Button botaoCobranca;
    @FXML
    private Button botaoGerar;
    @FXML
    private Label mensagemOperacao;
    @FXML
    private TableColumn<Aluguel, Aluguel> colunaReceber;
    private boolean gerando;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaDevido;
    @FXML
    private TableColumn<Cobranca, Cobranca> cobrancaAcao;

    @FXML
    private void initialize() {
        Permissoes.mostrarSe(botaoGerar, Acao.GERAR_ALUGUEIS);
        hoje = servico.hoje();
        montarTabela();
        montarTabelaCobranca();

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
            botaoGerar.setDisable(gerando || agora == null || TODOS_EM_ABERTO.equals(agora));
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
            contarCobranca();
            // Vindo do Painel (ex.: "Aluguéis em atraso"): abre na aba pedida, com todos os meses em aberto.
            Object filtro = Navegador.filtroRecebido();
            if (filtro instanceof SituacaoAluguel pedida) {
                grupoSituacao.getToggles().stream().filter(aba -> aba.getUserData() == pedida)
                        .findFirst().ifPresent(grupoSituacao::selectToggle);
                campoMes.setValue(TODOS_EM_ABERTO);
            } else if (filtro instanceof Recorte pedido) {
                recorte = pedido;
                grupoSituacao.selectToggle(abaAtraso);
                campoMes.setValue(TODOS_EM_ABERTO);
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
        colunaReceber.setVisible(Sessao.pode(Acao.RECEBER_ALUGUEL));
        colunaReceber.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaReceber.setCellFactory(c -> celula(a -> {
            Button receber = new Button("Receber");
            receber.getStyleClass().add("botao-secundario");
            receber.setDisable(a.estaPago());
            receber.setOnAction(e -> abrirRecebimento(a));
            return receber;
        }));
    }

    /** Lista de cobrança: quem é, quantos aluguéis deve, há quanto tempo e quanto deve hoje. */
    private void montarTabelaCobranca() {
        cobrancaInquilino.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        // Inquilino: nome e, embaixo, o imóvel e o telefone para ligar (o endereço completo fica na dica).
        cobrancaInquilino.setCellFactory(c -> celula(cb -> {
            String telefone = cb.contrato().getInquilino().getTelefone();
            VBox caixa = duasLinhas(cb.contrato().getInquilino().getNome(), cb.contrato().getImovel().getCodigo()
                    + " · " + (telefone == null ? "sem telefone" : telefone), null);
            String unidade = cb.contrato().getImovel().getUnidade();
            Tooltip.install(caixa, new Tooltip(cb.contrato().getNumero() + " · " + cb.contrato().getImovel().getCodigo()
                    + (unidade == null ? "" : " · " + unidade)));
            return caixa;
        }));
        cobrancaAtrasados.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaAtrasados.setCellFactory(c -> celula(cb -> duasLinhas(cb.alugueis().size() + " aluguéis",
                "ref. " + meses(cb.alugueis()), null)));
        cobrancaMaisAntigo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaMaisAntigo.setCellFactory(c -> celula(cb -> duasLinhas(
                "Há " + cb.maisAntigo().diasAtraso(hoje) + " dias",
                "desde " + Formatos.data(cb.maisAntigo().getVencimento()), "celula-detalhe-erro")));
        // Fiador (para avisar da dívida) com o telefone; ou o valor da caução.
        cobrancaGarantia.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaGarantia.setCellFactory(c -> celula(cb -> {
            if (cb.fiador() != null) {
                String telefone = cb.fiador().getTelefone();
                return duasLinhas(cb.fiador().getNome(), telefone == null ? "sem telefone" : telefone, null);
            }
            return duasLinhas("Caução", cb.caucao() == null ? "" : Formatos.moeda(cb.caucao().getValor()), null);
        }));
        cobrancaDevido.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaDevido.setCellFactory(c -> {
            TableCell<Cobranca, Cobranca> celula = celula(cb -> {
                VBox valor = duasLinhas(Formatos.moeda(cb.devidoHoje(hoje)),
                        "eram " + Formatos.moeda(cb.base()), null);
                valor.setAlignment(Pos.CENTER_RIGHT);
                return valor;
            });
            celula.getStyleClass().add("celula-numero");
            return celula;
        });
        // "Ver aluguéis": abre os aluguéis em aberto desse inquilino, um por um (para receber).
        cobrancaAcao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cobrancaAcao.setCellFactory(c -> celula(cb -> {
            Button ver = new Button(null, new FontIcon("fth-list"));
            ver.getStyleClass().add("botao-icone");
            ver.setTooltip(new Tooltip("Ver os aluguéis de " + cb.contrato().getInquilino().getNome()));
            ver.setOnAction(e -> verAlugueisDe(cb));
            return ver;
        }));
        tabelaCobranca.setRowFactory(t -> {
            TableRow<Cobranca> linha = new TableRow<>();
            linha.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !linha.isEmpty()) {
                    verAlugueisDe(linha.getItem());
                }
            });
            return linha;
        });
    }

    /** Sai da lista de cobrança e mostra os aluguéis em aberto do contrato desse inquilino. */
    private void verAlugueisDe(Cobranca cb) {
        recorte = null;
        grupoSituacao.selectToggle(abaTodos);
        campoBusca.setText(cb.contrato().getNumero()); // a busca já chama aplicarFiltros
        aplicarFiltros();
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

    @FXML
    private void gerarDoMes() {
        YearMonth mes = campoMes.getValue();
        if (gerando || mes == null || TODOS_EM_ABERTO.equals(mes)) {
            return;
        }
        gerando = true;
        botaoGerar.setDisable(true);
        informar("Gerando cobranças de " + MES.format(mes) + "…");
        Task<Integer> tarefa = new Task<>() {
            @Override protected Integer call() throws Exception { return servico.gerarDoMes(mes); }
        };
        tarefa.setOnSucceeded(evento -> {
            finalizarGeracao();
            carregarMes();
            contarCobranca();
            int gerados = tarefa.getValue();
            informar(gerados == 0 ? "Nenhuma cobrança nova para " + MES.format(mes)
                    + ". As existentes foram mantidas. Para emitir uma demonstração, clique em Receber e depois em Gerar boleto fictício (PDF)."
                    : gerados + " cobrança(s) gerada(s) para " + MES.format(mes) + ".");
        });
        tarefa.setOnFailed(evento -> {
            finalizarGeracao();
            informar(tarefa.getException() instanceof RegraNegocioException
                    ? tarefa.getException().getMessage() : "Não foi possível gerar as cobranças. Tente novamente.");
        });
        iniciarTarefa(tarefa);
    }

    private void finalizarGeracao() {
        gerando = false;
        botaoGerar.setDisable(campoMes.getValue() == null || TODOS_EM_ABERTO.equals(campoMes.getValue()));
    }

    private void informar(String mensagem) {
        mensagemOperacao.setText(mensagem);
        mensagemOperacao.setVisible(true);
        mensagemOperacao.setManaged(true);
    }

    private void salvarBoleto(Aluguel aluguel, Button botao, javafx.stage.Window janela,
            java.util.function.Consumer<String> mensagem) {
        FileChooser escolha = new FileChooser();
        escolha.setTitle("Salvar boleto fictício — sem validade para pagamento");
        escolha.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        escolha.setInitialFileName("boleto-ficticio-" + aluguel.getContrato().getNumero()
                + "-" + aluguel.getCompetencia() + ".pdf");
        File arquivo = escolha.showSaveDialog(janela);
        if (arquivo == null) return;
        botao.setDisable(true);
        mensagem.accept("Preparando PDF fictício…");
        Task<Void> tarefa = new Task<>() {
            @Override protected Void call() throws Exception {
                byte[] pdf = new ServicoBoletoFicticio().gerar(aluguel, servico.hoje());
                Files.write(arquivo.toPath(), pdf);
                return null;
            }
        };
        tarefa.setOnSucceeded(e -> {
            botao.setDisable(aluguel.estaPago());
            mensagem.accept("PDF fictício salvo em " + arquivo.getAbsolutePath() + ". Sem validade para pagamento.");
        });
        tarefa.setOnFailed(e -> {
            botao.setDisable(aluguel.estaPago());
            mensagem.accept(tarefa.getException() instanceof RegraNegocioException ? tarefa.getException().getMessage()
                    : "Não foi possível salvar o PDF. Verifique a pasta escolhida e tente novamente.");
        });
        iniciarTarefa(tarefa);
    }

    private static void iniciarTarefa(Task<?> tarefa) {
        Thread executor = new Thread(tarefa, "fmc-cobrancas");
        executor.setDaemon(true);
        executor.start();
    }

    private void abrirRecebimento(Aluguel aluguel) {
        Dialog<Void> janela = new Dialog<>();
        janela.setTitle("Receber aluguel");
        janela.setHeaderText(aluguel.getContrato().getInquilino().getNome() + " • "
                + aluguel.getContrato().getNumero() + " • " + aluguel.getCompetencia());
        janela.initOwner(tabela.getScene().getWindow());
        DatePicker data = new DatePicker(servico.hoje());
        ComboBox<FormaPagamento> forma = new ComboBox<>();
        forma.getItems().setAll(FormaPagamento.disponiveis());
        forma.setPromptText("Escolha a forma de pagamento");
        Label resumo = new Label();
        resumo.setWrapText(true);
        Label erro = new Label();
        erro.setWrapText(true);
        erro.getStyleClass().add("erro");
        Runnable atualizar = () -> {
            try {
                LocalDate dia = data.getValue();
                if (dia == null || dia.isAfter(servico.hoje())) {
                    resumo.setText("Informe uma data de pagamento válida, até hoje.");
                    return;
                }
                var encargos = aluguel.encargosEm(dia);
                resumo.setText("Vencimento: " + Formatos.data(aluguel.getVencimento())
                        + "\nValor base: " + Formatos.moeda(aluguel.base())
                        + "\nMulta (10%): " + Formatos.moeda(encargos.getMulta())
                        + "\nJuros (1% ao mês ou fração): " + Formatos.moeda(encargos.getJuros())
                        + "\nCorreção simulada (0,35% ao mês): " + Formatos.moeda(encargos.getCorrecao())
                        + "\nTotal a receber: " + Formatos.moeda(aluguel.base().add(encargos.total()))
                        + "\nAté 5 dias após o vencimento: sem encargos.");
            } catch (RuntimeException e) {
                resumo.setText("Informe uma data válida.");
            }
        };
        data.valueProperty().addListener((obs, antes, agora) -> atualizar.run());
        data.getEditor().focusedProperty().addListener((obs, antes, agora) -> {
            if (!agora) {
                try { data.commitValue(); atualizar.run(); }
                catch (RuntimeException e) { resumo.setText("Informe uma data válida."); }
            }
        });
        atualizar.run();
        VBox campos = new VBox(10, new Label("Data do pagamento"), data, new Label("Forma de pagamento"),
                forma, resumo, erro);
        Button gerarBoleto = new Button("Gerar boleto fictício (PDF)", new FontIcon("fth-file-text"));
        gerarBoleto.getStyleClass().add("botao-secundario");
        Permissoes.mostrarSe(gerarBoleto, Acao.GERAR_ALUGUEIS);
        Label orientacaoBoleto = new Label("Para cobrar antes de receber, gere o PDF fictício. "
                + "Isso mantém o aluguel em aberto e não exige escolher a forma de pagamento. "
                + "O PDF usa a data de emissão de hoje e não tem validade para pagamento.");
        orientacaoBoleto.setWrapText(true);
        Permissoes.mostrarSe(orientacaoBoleto, Acao.GERAR_ALUGUEIS);
        Label resultadoBoleto = new Label();
        resultadoBoleto.setWrapText(true);
        gerarBoleto.setOnAction(e -> salvarBoleto(aluguel, gerarBoleto,
                janela.getDialogPane().getScene().getWindow(), resultadoBoleto::setText));
        campos.getChildren().addAll(gerarBoleto, orientacaoBoleto, resultadoBoleto);
        campos.setPrefWidth(480);
        janela.getDialogPane().setContent(campos);
        ButtonType confirmar = new ButtonType("Confirmar recebimento", ButtonBar.ButtonData.OK_DONE);
        janela.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);
        Button confirmarBotao = (Button) janela.getDialogPane().lookupButton(confirmar);
        Button cancelarBotao = (Button) janela.getDialogPane().lookupButton(ButtonType.CANCEL);
        final boolean[] salvando = {false};
        janela.setOnCloseRequest(e -> { if (salvando[0]) e.consume(); });
        confirmarBotao.addEventFilter(javafx.event.ActionEvent.ACTION, evento -> {
            evento.consume();
            try { data.commitValue(); }
            catch (RuntimeException e) { erro.setText("Informe uma data válida."); return; }
            if (data.getValue() == null || data.getValue().isAfter(servico.hoje()) || forma.getValue() == null) {
                erro.setText("Informe a data até hoje e escolha a forma de pagamento.");
                return;
            }
            LocalDate dia = data.getValue();
            FormaPagamento meio = forma.getValue();
            salvando[0] = true;
            gerarBoleto.setDisable(true);
            confirmarBotao.setDisable(true);
            cancelarBotao.setDisable(true);
            data.setDisable(true);
            forma.setDisable(true);
            erro.setText("Registrando pagamento…");
            Task<RecebimentoAluguel> tarefa = new Task<>() {
                @Override protected RecebimentoAluguel call() throws Exception {
                    return servico.receber(aluguel.getId(), dia, meio);
                }
            };
            tarefa.setOnSucceeded(e -> {
                salvando[0] = false;
                janela.close();
                carregarMes();
                contarCobranca();
                informar("Pagamento registrado: " + Formatos.moeda(tarefa.getValue().total()) + " • "
                        + meio + " • " + Formatos.data(dia) + ". Situação: Pago.");
            });
            tarefa.setOnFailed(e -> {
                salvando[0] = false;
                confirmarBotao.setDisable(false);
                gerarBoleto.setDisable(false);
                cancelarBotao.setDisable(false);
                data.setDisable(false);
                forma.setDisable(false);
                erro.setText(tarefa.getException() instanceof RegraNegocioException
                        ? tarefa.getException().getMessage() : "Não foi possível registrar. Tente novamente.");
            });
            iniciarTarefa(tarefa);
        });
        janela.show();
    }

    /** Ao trocar de mês: busca os aluguéis do mês no banco. */
    private void carregarMes() {
        YearMonth mes = campoMes.getValue();
        if (mes == null) {
            return;
        }
        try {
            if (!mes.equals(TODOS_EM_ABERTO)) {
                recorte = null; // o recorte do Painel vale para todos os meses em aberto
            }
            doMes = mes.equals(TODOS_EM_ABERTO) ? servico.listarEmAberto() : servico.listarDoMes(mes);
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
        String busca = campoBusca.getText();
        // Lista de cobrança (pelo Painel ou pelo botão): troca as abas e a tabela por uma linha por inquilino.
        boolean cobranca = recorte == Recorte.INQUILINOS_PARA_COBRANCA;
        mostrar(abas, !cobranca);
        mostrar(tabela, !cobranca);
        mostrar(tabelaCobranca, cobranca);
        mostrar(faixaRecorte, cobranca);
        mostrar(botaoCobranca, !cobranca);
        if (cobranca) {
            List<Cobranca> lista = ServicoAlugueis.listaDeCobranca(doMes, hoje);
            ServicoAlugueis.Faixas f = ServicoAlugueis.faixas(lista);
            textoRecorte.setText("Lista de cobrança: " + lista.size()
                    + (lista.size() == 1 ? " inquilino" : " inquilinos") + " com 2 ou mais aluguéis atrasados, "
                    + "que devem " + Formatos.moeda(lista.stream().map(c -> c.devidoHoje(hoje))
                            .reduce(BigDecimal.ZERO, BigDecimal::add)) + " com multa e juros."
                    + (lista.isEmpty() ? "" : "\n" + f.dois() + " com 2 aluguéis · " + f.tres() + " com 3 · "
                            + f.quatroOuMais() + " com 4 ou mais"));
            tabelaCobranca.getItems().setAll(ServicoAlugueis.listaDeCobranca(
                    ServicoAlugueis.filtrar(doMes, null, busca, hoje), hoje));
            tabelaCobranca.setPlaceholder(vazio("fth-check-circle", "Ninguém para cobrar",
                    busca.isBlank() ? "Nenhum inquilino tem 2 ou mais aluguéis atrasados."
                            : "Nenhum inquilino da lista com essa busca."));
        }
        List<Aluguel> base = doMes;
        // As contagens das abas respeitam a busca, para a pessoa ver quantos há em cada situação.
        List<Aluguel> daBusca = ServicoAlugueis.filtrar(base, null, busca, hoje);
        Map<SituacaoAluguel, ServicoAlugueis.Total> totais = ServicoAlugueis.totais(daBusca, hoje);
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
        recorte = Recorte.INQUILINOS_PARA_COBRANCA;
        grupoSituacao.selectToggle(abaAtraso);
        campoBusca.clear();
        if (TODOS_EM_ABERTO.equals(campoMes.getValue())) {
            aplicarFiltros();
        } else {
            campoMes.setValue(TODOS_EM_ABERTO); // carrega os aluguéis em aberto e mostra a lista
        }
    }

    /** O botão mostra quantos inquilinos estão na lista de cobrança agora. */
    private void contarCobranca() {
        try {
            int n = ServicoAlugueis.listaDeCobranca(servico.listarEmAberto(), hoje).size();
            botaoCobranca.setText("Lista de cobrança · " + n);
        } catch (RegraNegocioException | SQLException e) {
            botaoCobranca.setText("Lista de cobrança");
        }
    }

    /** Botão "Ver todos os aluguéis" da faixa: sai da lista de cobrança e volta à aba Em atraso. */
    @FXML
    private void verTodos() {
        recorte = null;
        aplicarFiltros();
    }

    @FXML
    private void limparFiltros() {
        recorte = null;
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
