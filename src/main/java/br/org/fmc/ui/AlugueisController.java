package br.org.fmc.ui;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoAlugueis;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
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
        /** Só os aluguéis em atraso de quem tem 2 ou mais meses em atraso (para cobrança). */
        INQUILINOS_COM_MAIS_DE_UM_MES_EM_ATRASO
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
    private void initialize() {
        hoje = servico.hoje();
        montarTabela();

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
        campoMes.valueProperty().addListener((obs, antes, agora) -> carregarMes());
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
        // Recorte pedido pelo Painel: só os aluguéis em atraso de quem tem mais de 1 mês em atraso.
        List<Aluguel> base = recorte == null ? doMes
                : ServicoAlugueis.deInquilinosComMaisDeUmMesEmAtraso(doMes, hoje);
        mostrarFaixa(base);
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

    /** A faixa azul acima da tabela, que explica o recorte vindo do Painel. */
    private void mostrarFaixa(List<Aluguel> base) {
        faixaRecorte.setVisible(recorte != null);
        faixaRecorte.setManaged(recorte != null);
        if (recorte != null) {
            long inquilinos = base.stream().map(a -> a.getContrato().getId()).distinct().count();
            textoRecorte.setText("Mostrando só os aluguéis em atraso dos " + inquilinos
                    + " inquilinos com mais de 1 mês em atraso (" + base.size() + " aluguéis).");
        }
    }

    /** Botão "Ver todos" da faixa: tira o recorte e volta a mostrar todos os aluguéis. */
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

    /** Célula de tabela que desenha um componente a partir do aluguel da linha. */
    private static TableCell<Aluguel, Aluguel> celula(Function<Aluguel, Node> desenho) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Aluguel a, boolean vazio) {
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
