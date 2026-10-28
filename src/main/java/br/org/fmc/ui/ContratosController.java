package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.SituacaoContrato;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.service.DadosContrato;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoContratos;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a tela "Contratos" (ui/contratos.fxml), BOARD-36.
 * Só cuida da tela: as regras (imóvel vago, renda 3x, garantia) ficam no ServicoContratos.
 * As prévias que aparecem enquanto a pessoa preenche (renda, fim do contrato) só ajudam:
 * quem decide de verdade, ao cadastrar, é o service.
 */
public class ContratosController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ServicoContratos servico = new ServicoContratos();

    @FXML
    private Label contagem;
    /** Busca e filtro por situação (BOARD-59). */
    @FXML
    private TextField campoBusca;
    @FXML
    private ComboBox<SituacaoContrato> filtroSituacao;

    /** Todos os contratos carregados do banco; a tabela mostra só os que passam na busca e no filtro. */
    private List<Contrato> todos = List.of();

    /** O que a tabela mostra quando não há contrato nenhum, e quando a busca ou o filtro não achou nada. */
    private VBox vazioSemCadastro;
    private VBox vazioBusca;
    private Label tituloVazioBusca;
    private Label dicaVazioBusca;
    @FXML
    private Button botaoNovo;
    @FXML
    private TableView<Contrato> tabela;
    @FXML
    private TableColumn<Contrato, Contrato> colunaNumero;
    @FXML
    private TableColumn<Contrato, Contrato> colunaImovel;
    @FXML
    private TableColumn<Contrato, Contrato> colunaInquilino;
    @FXML
    private TableColumn<Contrato, Contrato> colunaAluguel;
    @FXML
    private TableColumn<Contrato, Contrato> colunaVigencia;
    @FXML
    private TableColumn<Contrato, Contrato> colunaGarantia;
    @FXML
    private TableColumn<Contrato, Contrato> colunaSituacao;

    @FXML
    private HBox toast;
    @FXML
    private Label textoToast;

    @FXML
    private StackPane veu;
    @FXML
    private HBox avisoErros;
    @FXML
    private Label textoErros;
    @FXML
    private ComboBox<Imovel> campoImovel;
    @FXML
    private Label dicaImovel;
    @FXML
    private ComboBox<Inquilino> campoInquilino;
    @FXML
    private Label dicaRenda;
    @FXML
    private DatePicker campoEntrega;
    @FXML
    private ComboBox<PrazoContrato> campoPrazo;
    @FXML
    private HBox resumoPrazo;
    @FXML
    private Label textoResumoPrazo;
    @FXML
    private ToggleGroup grupoGarantia;
    @FXML
    private ToggleButton opcaoFiador;
    @FXML
    private ToggleButton opcaoCaucao;
    @FXML
    private VBox blocoFiador;
    @FXML
    private TextField campoFiadorNome;
    @FXML
    private TextField campoFiadorCpf;
    @FXML
    private Label dicaFiadorCpf;
    @FXML
    private ComboBox<EstadoCivil> campoFiadorEstadoCivil;
    @FXML
    private TextField campoFiadorTelefone;
    @FXML
    private TextField campoFiadorRenda;
    @FXML
    private TextField campoFiadorEndereco;
    @FXML
    private VBox blocoConjugeFiador;
    @FXML
    private TextField campoConjugeNome;
    @FXML
    private TextField campoConjugeCpf;
    @FXML
    private Label dicaConjugeCpf;
    @FXML
    private VBox blocoCaucao;
    @FXML
    private TextField campoCaucaoValor;
    @FXML
    private Label dicaCaucao;
    @FXML
    private DatePicker campoCaucaoData;
    @FXML
    private ComboBox<FormaPagamento> campoCaucaoForma;
    @FXML
    private TextField campoCaucaoRecibo;

    @FXML
    private void initialize() {
        montarTabela();

        // Máscaras: os campos se formatam sozinhos enquanto a pessoa digita (BOARD-165).
        Mascara.cpf(campoFiadorCpf, dicaFiadorCpf);
        Mascara.cpf(campoConjugeCpf, dicaConjugeCpf);
        Mascara.telefone(campoFiadorTelefone);
        Mascara.dinheiro(campoFiadorRenda);
        Mascara.dinheiro(campoCaucaoValor);
        formatarData(campoEntrega);
        formatarData(campoCaucaoData);

        campoImovel.setConverter(texto(ContratosController::descreverImovel));
        campoPrazo.getItems().setAll(PrazoContrato.values());
        campoFiadorEstadoCivil.getItems().setAll(EstadoCivil.values());
        campoCaucaoForma.getItems().setAll(FormaPagamento.values());

        // Prévias que se atualizam enquanto a pessoa preenche.
        campoImovel.valueProperty().addListener((obs, antes, agora) -> imovelEscolhido(antes, agora));
        campoInquilino.valueProperty().addListener((obs, antes, agora) -> atualizarRenda());
        campoEntrega.valueProperty().addListener((obs, antes, agora) -> atualizarResumoPrazo());
        campoPrazo.valueProperty().addListener((obs, antes, agora) -> atualizarResumoPrazo());
        campoFiadorEstadoCivil.valueProperty().addListener((obs, antes, agora) -> mostrarBlocos());

        // Fiador ou caução: sempre um dos dois marcado (clicar no marcado não desmarca).
        grupoGarantia.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                grupoGarantia.selectToggle(antes);
            }
            mostrarBlocos();
        });

        Permissoes.mostrarSe(botaoNovo, Acao.CRIAR_CONTRATO);

        // Esc fecha a janela do formulário.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });

        esconder(veu);
        esconder(toast);

        // Busca e filtro: atualizam enquanto a pessoa digita ou escolhe; Esc limpa a busca;
        // Ctrl+F leva o cursor para a busca (igual às telas de Imóveis e Inquilinos).
        filtroSituacao.getItems().setAll(SituacaoContrato.values());
        filtroSituacao.setButtonCell(celulaSituacao());
        filtroSituacao.setCellFactory(l -> celulaSituacao());
        filtroSituacao.valueProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        campoBusca.textProperty().addListener((obs, antes, agora) -> aplicarFiltros());
        campoBusca.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                limparFiltros();
            }
        });
        campoBusca.sceneProperty().addListener((obs, antes, cena) -> {
            if (cena != null) {
                cena.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                        () -> campoBusca.requestFocus());
            }
        });

        // Clicar na linha (ou Enter na linha escolhida) abre os detalhes do contrato.
        tabela.setRowFactory(t -> linhaClicavel());
        tabela.setOnKeyPressed(e -> {
            Contrato escolhido = tabela.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && escolhido != null) {
                DetalhesContratoController.abrir(escolhido);
            }
        });

        // Vindo do Painel (ex.: "Contratos vencidos"): já abre filtrado por essa situação.
        if (Navegador.filtroRecebido() instanceof SituacaoContrato pedida) {
            filtroSituacao.setValue(pedida);
        }
        carregarLista();
    }

    private void montarTabela() {
        colunaNumero.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaNumero.setCellFactory(c -> celula(ct -> rotulo(ct.getNumero(), "celula-nome")));
        // Imóvel: código e, embaixo, a unidade (ou o endereço, se não tiver unidade).
        colunaImovel.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaImovel.setCellFactory(c -> celula(ct -> duasLinhas(
                ct.getImovel().getCodigo(),
                ct.getImovel().getUnidade() != null ? ct.getImovel().getUnidade() : ct.getImovel().getEndereco())));
        // Inquilino: nome e, embaixo, o CPF.
        colunaInquilino.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaInquilino.setCellFactory(c -> celula(ct -> duasLinhas(ct.getInquilino().getNome(),
                ct.getInquilino().getCpf().formatado())));
        colunaAluguel.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAluguel.setCellFactory(c -> {
            TableCell<Contrato, Contrato> celula = celula(ct -> new Label(Formatos.moeda(ct.getValorAluguel())));
            celula.getStyleClass().add("celula-numero"); // valores alinhados à direita
            return celula;
        });
        colunaVigencia.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaVigencia.setCellFactory(c -> celula(ct -> duasLinhas(
                Formatos.data(ct.getDataEntregaChaves()) + " a " + Formatos.data(ct.getDataTermino()),
                ct.getPrazo() + " · vence dia " + ct.getDiaVencimento())));
        colunaGarantia.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaGarantia.setCellFactory(c -> celula(ct -> {
            Label selo = selo(ct.getGarantia() instanceof Caucao ? "Caução" : "Fiador", "selo-neutro");
            selo.setTooltip(new Tooltip(ct.getGarantia().getDescricao()));
            return selo;
        }));
        colunaSituacao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        // Os botões ficam ao lado do selo, só para quem tem a permissão:
        // Prorrogar (BOARD-169) nos vencidos ou vencendo, e Encerrar (BOARD-174) em qualquer contrato ativo.
        colunaSituacao.setCellFactory(c -> celula(ct -> {
            HBox caixa = new HBox(4, seloSituacao(ct));
            caixa.setAlignment(Pos.CENTER_LEFT);
            if (Sessao.pode(Acao.PRORROGAR_CONTRATO) && servico.podeProrrogar(ct)) {
                caixa.getChildren().add(botaoProrrogar(ct));
            }
            if (Sessao.pode(Acao.ENCERRAR_CONTRATO) && ct.getStatus() == StatusContrato.ATIVO) {
                caixa.getChildren().add(botaoEncerrar(ct));
            }
            return caixa;
        }));

        FontIcon icone = new FontIcon("fth-file-text");
        Label titulo = new Label("Nenhum contrato cadastrado ainda");
        titulo.getStyleClass().add("tabela-vazia-titulo");
        Label dica = new Label(Sessao.pode(Acao.CRIAR_CONTRATO)
                ? "Clique em \"Novo contrato\" para começar." : "Os contratos aparecem aqui.");
        dica.getStyleClass().add("texto");
        VBox vazio = new VBox(8, icone, titulo, dica);
        vazio.setAlignment(Pos.CENTER);
        vazio.getStyleClass().add("tabela-vazia");
        vazioSemCadastro = vazio;

        // Busca ou filtro sem resultado: diz o que foi pedido e oferece limpar.
        tituloVazioBusca = new Label();
        tituloVazioBusca.getStyleClass().add("tabela-vazia-titulo");
        dicaVazioBusca = new Label();
        dicaVazioBusca.getStyleClass().add("texto");
        Button limpar = new Button("Limpar filtros");
        limpar.getStyleClass().add("botao-secundario");
        limpar.setOnAction(e -> limparFiltros());
        vazioBusca = new VBox(8, new FontIcon("fth-search"), tituloVazioBusca, dicaVazioBusca, limpar);
        vazioBusca.setAlignment(Pos.CENTER);
        vazioBusca.getStyleClass().add("tabela-vazia");
        tabela.setPlaceholder(vazioSemCadastro);
    }

    /** Linha da tabela que abre os detalhes ao clicar, menos quando o clique foi num botão da linha. */
    private TableRow<Contrato> linhaClicavel() {
        TableRow<Contrato> linha = new TableRow<>();
        linha.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && !linha.isEmpty() && !clicouEmBotao(e.getTarget())) {
                DetalhesContratoController.abrir(linha.getItem());
            }
        });
        return linha;
    }

    private static boolean clicouEmBotao(Object alvo) {
        for (Node no = alvo instanceof Node n ? n : null; no != null; no = no.getParent()) {
            if (no instanceof Button) {
                return true;
            }
        }
        return false;
    }

    /** Mostra "Todas as situações" quando nada está escolhido no filtro. */
    private ListCell<SituacaoContrato> celulaSituacao() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SituacaoContrato item, boolean vazia) {
                super.updateItem(item, vazia);
                setText(vazia || item == null ? "Todas as situações" : item.toString());
            }
        };
    }

    /**
     * Ativo; vencendo (nos próximos 60 dias); vencido (não gera aluguel até ser prorrogado); ou encerrado.
     * Decisão do PO (27/10): contrato vencido não continua gerando aluguel sozinho.
     */
    private static Label seloSituacao(Contrato c) {
        if (c.getStatus() == StatusContrato.ENCERRADO) {
            Label selo = selo("Encerrado", "selo-neutro");
            if (c.getEncerramento() != null) {
                selo.setTooltip(new Tooltip("Chaves devolvidas em " + Formatos.data(c.getEncerramento().getDataSaida())
                        + ". " + c.getEncerramento().getMotivo() + "."));
            }
            return selo;
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), c.getDataTermino());
        if (dias < 0) {
            Label selo = selo("Vencido", "selo-erro");
            selo.setTooltip(new Tooltip("O prazo venceu em " + Formatos.data(c.getDataTermino())
                    + ". Não gera aluguel até ser prorrogado."));
            return selo;
        }
        if (dias <= RegrasNegocio.DIAS_AVISO_VENCIMENTO) {
            Label selo = selo("Vencendo", "selo-alerta");
            selo.setTooltip(new Tooltip((dias == 0 ? "Termina hoje" : "Termina em " + Formatos.data(c.getDataTermino())
                    + (dias == 1 ? " (amanhã)" : " (daqui a " + dias + " dias)")) + ". Já pode ser prorrogado."));
            return selo;
        }
        return selo("Ativo", "selo-sucesso");
    }

    private Button botaoProrrogar(Contrato c) {
        Button botao = new Button(null, new FontIcon("fth-refresh-cw"));
        botao.getStyleClass().addAll("botao-icone", "botao-tabela", "botao-prorrogar");
        botao.setAccessibleText("Prorrogar");
        botao.setTooltip(new Tooltip("Prorrogar o contrato " + c.getNumero() + " por 12, 24 ou 36 meses"));
        botao.setOnAction(e -> ProrrogarContratoController.abrir(c, servico, prorrogado -> {
            carregarLista();
            mostrarToast("Contrato " + prorrogado.getNumero() + " prorrogado até "
                    + Formatos.data(prorrogado.getDataTermino()) + ".");
        }));
        return botao;
    }

    private Button botaoEncerrar(Contrato c) {
        Button botao = new Button(null, new FontIcon("fth-log-out"));
        botao.getStyleClass().addAll("botao-icone", "botao-tabela");
        botao.setAccessibleText("Encerrar");
        botao.setTooltip(new Tooltip("Encerrar o contrato " + c.getNumero() + " (o inquilino devolveu as chaves)"));
        botao.setOnAction(e -> EncerrarContratoController.abrir(c, servico, encerrado -> {
            carregarLista();
            mostrarToast("Contrato " + encerrado.getNumero() + " encerrado. O imóvel "
                    + encerrado.getImovel().getCodigo() + " agora está vago.");
        }));
        return botao;
    }

    /** Botão "Novo contrato": carrega as opções e abre a janela vazia. */
    @FXML
    private void novo() {
        try {
            campoImovel.getItems().setAll(servico.imoveisVagos());
            campoInquilino.getItems().setAll(servico.inquilinosSemContrato());
        } catch (RegraNegocioException e) {
            mostrarToast(String.join(" ", e.getErros()));
            return;
        } catch (SQLException e) {
            mostrarToast("Erro no banco de dados: " + e.getMessage());
            return;
        }
        campoImovel.setValue(null);
        campoInquilino.setValue(null);
        campoEntrega.setValue(LocalDate.now());
        campoPrazo.setValue(PrazoContrato.MESES_12);
        for (TextField campo : new TextField[] {campoFiadorNome, campoFiadorCpf, campoFiadorTelefone, campoFiadorRenda,
            campoFiadorEndereco, campoConjugeNome, campoConjugeCpf, campoCaucaoValor, campoCaucaoRecibo}) {
            campo.clear();
        }
        campoFiadorEstadoCivil.setValue(EstadoCivil.SOLTEIRO);
        campoCaucaoData.setValue(LocalDate.now());
        campoCaucaoForma.setValue(FormaPagamento.PIX);
        grupoGarantia.selectToggle(opcaoFiador);

        esconder(avisoErros);
        imovelEscolhido(null, null);
        atualizarResumoPrazo();
        mostrarBlocos();
        Navegador.mostrarJanela(veu); // por cima da tela inteira, inclusive do menu
        campoImovel.requestFocus();
    }

    /** Botões "Cancelar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }

    /** Botão "Cadastrar contrato". */
    @FXML
    private void salvar() {
        try {
            Contrato salvo = servico.cadastrar(lerFormulario());
            fechar();
            carregarLista();
            mostrarToast("Contrato " + salvo.getNumero() + " cadastrado. O imóvel "
                    + salvo.getImovel().getCodigo() + " agora está ocupado.");
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Erro no banco de dados: " + e.getMessage());
        }
    }

    private DadosContrato lerFormulario() {
        Imovel imovel = campoImovel.getValue();
        Inquilino inquilino = campoInquilino.getValue();
        DadosContrato.Fiador fiador = null;
        DadosContrato.Caucao caucao = null;
        if (opcaoCaucao.isSelected()) {
            caucao = new DadosContrato.Caucao(campoCaucaoValor.getText(), lerData(campoCaucaoData),
                    campoCaucaoForma.getValue(), campoCaucaoRecibo.getText());
        } else {
            fiador = new DadosContrato.Fiador(campoFiadorNome.getText(), campoFiadorCpf.getText(),
                    campoFiadorTelefone.getText(), campoFiadorEstadoCivil.getValue(), campoFiadorRenda.getText(),
                    campoFiadorEndereco.getText(), campoConjugeNome.getText(), campoConjugeCpf.getText());
        }
        return new DadosContrato(imovel == null ? null : imovel.getId(), inquilino == null ? null : inquilino.getId(),
                campoPrazo.getValue(), lerData(campoEntrega), fiador, caucao);
    }

    /** Ao escolher o imóvel: mostra os valores e sugere a caução de 3 aluguéis. */
    private void imovelEscolhido(Imovel antes, Imovel agora) {
        if (agora == null) {
            dicaImovel.setText("Os valores do contrato são copiados do imóvel.");
            dicaCaucao.setText("Pelo menos " + RegrasNegocio.MESES_CAUCAO + " aluguéis.");
        } else {
            dicaImovel.setText("Aluguel " + Formatos.moeda(agora.getValorAluguel())
                    + " · Taxa de luz " + Formatos.moeda(agora.getTaxaLuz())
                    + " · IPTU " + Formatos.moeda(agora.getValorIptu()));
            BigDecimal exigido = Caucao.valorExigido(agora.getValorAluguel());
            dicaCaucao.setText("Pelo menos " + RegrasNegocio.MESES_CAUCAO + " aluguéis: " + Formatos.moeda(exigido) + ".");
            // Sugere o valor da caução, sem apagar um valor que a pessoa já tenha digitado.
            String sugestaoAnterior = antes == null ? "" : Formatos.numero(Caucao.valorExigido(antes.getValorAluguel()));
            if (campoCaucaoValor.getText().isBlank() || campoCaucaoValor.getText().equals(sugestaoAnterior)) {
                campoCaucaoValor.setText(Formatos.numero(exigido));
            }
        }
        atualizarRenda();
    }

    /** Prévia da regra de renda: renda familiar do inquilino x 3 aluguéis. */
    private void atualizarRenda() {
        Inquilino inquilino = campoInquilino.getValue();
        Imovel imovel = campoImovel.getValue();
        dicaRenda.getStyleClass().removeAll("dica", "sucesso", "erro");
        if (inquilino == null) {
            dicaRenda.setText(campoInquilino.getItems().isEmpty()
                    ? "Nenhum inquilino disponível: cadastre o inquilino antes, na tela Inquilinos."
                    : "A renda familiar precisa ser pelo menos " + RegrasNegocio.MULTIPLO_RENDA + " vezes o aluguel.");
            dicaRenda.getStyleClass().add("dica");
            return;
        }
        String renda = "Renda familiar " + Formatos.moeda(inquilino.rendaConsiderada());
        if (imovel == null) {
            dicaRenda.setText(renda + ".");
            dicaRenda.getStyleClass().add("dica");
            return;
        }
        BigDecimal minimo = imovel.getValorAluguel().multiply(BigDecimal.valueOf(RegrasNegocio.MULTIPLO_RENDA));
        boolean atende = inquilino.rendaConsiderada().compareTo(minimo) >= 0;
        dicaRenda.setText(renda + (atende
                ? ": atende à regra de " + RegrasNegocio.MULTIPLO_RENDA + " aluguéis (" + Formatos.moeda(minimo) + ")."
                : ": abaixo de " + RegrasNegocio.MULTIPLO_RENDA + " aluguéis (" + Formatos.moeda(minimo)
                        + "). O contrato não pode ser cadastrado."));
        dicaRenda.getStyleClass().add(atende ? "sucesso" : "erro");
    }

    /** Prévia: quando o contrato termina e quando vencem os aluguéis. */
    private void atualizarResumoPrazo() {
        LocalDate entrega = campoEntrega.getValue();
        PrazoContrato prazo = campoPrazo.getValue();
        if (entrega == null || prazo == null) {
            textoResumoPrazo.setText("Escolha a data e o prazo para ver o fim do contrato e o vencimento dos aluguéis.");
            return;
        }
        LocalDate primeiro = entrega.plusDays(RegrasNegocio.DIAS_1O_VENCIMENTO);
        textoResumoPrazo.setText("Termina em " + Formatos.data(entrega.plusMonths(prazo.getMeses()))
                + ". O 1º aluguel vence em " + Formatos.data(primeiro)
                + " e os próximos, todo dia " + primeiro.getDayOfMonth() + ".");
    }

    /** Mostra só os campos da garantia escolhida, e os do cônjuge só para fiador casado. */
    private void mostrarBlocos() {
        boolean caucao = opcaoCaucao.isSelected();
        mostrar(blocoFiador, !caucao);
        mostrar(blocoCaucao, caucao);
        EstadoCivil estado = campoFiadorEstadoCivil.getValue();
        mostrar(blocoConjugeFiador, estado != null && estado.temConjuge());
    }

    private void carregarLista() {
        try {
            todos = servico.listar();
            aplicarFiltros();
        } catch (RegraNegocioException e) {
            contagem.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            contagem.setText("Erro no banco de dados: " + e.getMessage());
        }
    }

    /** Mostra na tabela só os contratos que passam na busca e no filtro e atualiza a contagem. */
    private void aplicarFiltros() {
        SituacaoContrato situacao = filtroSituacao.getValue();
        String busca = campoBusca.getText();
        boolean filtrando = situacao != null || !busca.isBlank();
        List<Contrato> filtrados = ServicoContratos.filtrar(todos, situacao, busca, LocalDate.now());
        tabela.getItems().setAll(filtrados);

        int total = todos.size();
        if (filtrando) {
            contagem.setText(filtrados.size() + " de " + total + (total == 1 ? " contrato" : " contratos"));
            tituloVazioBusca.setText(busca.isBlank() ? "Nenhum contrato " + situacao.getDescricao().toLowerCase()
                    : "Nenhum contrato encontrado para \"" + busca.trim() + "\"");
            dicaVazioBusca.setText("Dá para buscar por número do contrato, imóvel, nome do inquilino ou CPF.");
            tabela.setPlaceholder(vazioBusca);
        } else {
            long ativos = todos.stream().filter(c -> c.getStatus() == StatusContrato.ATIVO).count();
            long vencidos = todos.stream()
                    .filter(c -> SituacaoContrato.de(c, LocalDate.now()) == SituacaoContrato.VENCIDO).count();
            contagem.setText((total == 1 ? "1 contrato cadastrado" : total + " contratos cadastrados, " + ativos + " ativos")
                    + (vencidos == 0 ? "" : " · " + vencidos + (vencidos == 1 ? " vencido" : " vencidos")
                            + ", sem gerar aluguel até serem prorrogados"));
            tabela.setPlaceholder(vazioSemCadastro);
        }
    }

    /** Botão "Limpar filtros" (ao lado da busca e na lista vazia) e a tecla Esc na busca. */
    @FXML
    private void limparFiltros() {
        filtroSituacao.setValue(null);
        campoBusca.clear();
        campoBusca.requestFocus();
    }

    private void mostrarErros(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    /** Aviso rápido no canto da tela, que some sozinho depois de 4 segundos. */
    private void mostrarToast(String texto) {
        textoToast.setText(texto);
        mostrar(toast, true);
        PauseTransition espera = new PauseTransition(Duration.seconds(4));
        espera.setOnFinished(e -> esconder(toast));
        espera.play();
    }

    /** Datas no formato brasileiro (dd/mm/aaaa), digitando ou escolhendo no calendário. */
    private static void formatarData(DatePicker campo) {
        campo.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate data) {
                return data == null ? "" : data.format(DATA);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.trim(), DATA);
                } catch (DateTimeParseException e) {
                    return null; // data inválida: o service avisa que falta a data
                }
            }
        });
    }

    /** Lê a data mesmo que a pessoa tenha digitado e não apertado Enter. */
    private static LocalDate lerData(DatePicker campo) {
        return campo.getConverter().fromString(campo.getEditor().getText());
    }

    private static String descreverImovel(Imovel i) {
        return i.getCodigo() + (i.getUnidade() == null ? "" : " · " + i.getUnidade()) + " · " + i.getEndereco()
                + " · " + Formatos.moeda(i.getValorAluguel());
    }

    private static <T> StringConverter<T> texto(Function<T, String> descricao) {
        return new StringConverter<>() {
            @Override
            public String toString(T objeto) {
                return objeto == null ? "" : descricao.apply(objeto);
            }

            @Override
            public T fromString(String texto) {
                return null; // a lista não é digitável
            }
        };
    }

    /** Célula de tabela que desenha um componente a partir do contrato da linha. */
    private static TableCell<Contrato, Contrato> celula(Function<Contrato, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Contrato c, boolean vazio) {
                super.updateItem(c, vazio);
                setGraphic(vazio || c == null ? null : desenho.apply(c));
            }
        };
    }

    private static VBox duasLinhas(String principal, String detalhe) {
        return new VBox(2, rotulo(principal, "celula-nome"), rotulo(detalhe, "celula-detalhe"));
    }

    private static Label rotulo(String texto, String estilo) {
        Label rotulo = new Label(texto);
        rotulo.getStyleClass().add(estilo);
        return rotulo;
    }

    private static Label selo(String texto, String tipo) {
        Label selo = new Label(texto);
        selo.getStyleClass().addAll("selo", tipo);
        return selo;
    }

    private static void mostrar(Node componente, boolean visivel) {
        componente.setVisible(visivel);
        componente.setManaged(visivel);
    }

    private static void esconder(Node componente) {
        mostrar(componente, false);
    }
}
