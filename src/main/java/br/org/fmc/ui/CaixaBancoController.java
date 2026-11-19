package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Lancamento;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCaixaBanco;
import br.org.fmc.service.ServicoCaixaBanco.Disponibilidades;
import br.org.fmc.service.ServicoCaixaBanco.Linha;
import br.org.fmc.service.ServicoCaixaBanco.Movimento;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * [D5] BOARD-63 · Tela "Caixa e banco": disponibilidades de hoje e o movimento do mês do Caixa
 * e do Banco, a partir dos lançamentos que os recibos geram sozinhos (D4).
 */
public class CaixaBancoController {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy",
            Locale.forLanguageTag("pt-BR"));

    private final ServicoCaixaBanco servico = new ServicoCaixaBanco();

    @FXML private Button botaoSaldoInicial;
    @FXML private Button botaoDepositar;
    @FXML private HBox avisoSaldoInicial;
    @FXML private Label textoAvisoSaldo;
    @FXML private Label dataDisponibilidades;
    @FXML private HBox barra;
    @FXML private Region barraCaixa;
    @FXML private Region barraBanco;
    @FXML private Label percentualCaixa;
    @FXML private Label percentualBanco;
    @FXML private Label saldoCaixa;
    @FXML private Label saldoBanco;
    @FXML private Label saldoTotal;
    @FXML private Label textoSaldoInicial;
    @FXML private ToggleGroup grupoConta;
    @FXML private ToggleButton abaCaixa;
    @FXML private ToggleButton abaBanco;
    @FXML private Button mesAnterior;
    @FXML private Button mesSeguinte;
    @FXML private ComboBox<YearMonth> campoMes;
    @FXML private Label descricaoConta;
    @FXML private HBox cartoesMes;
    @FXML private TableView<Linha> tabela;
    @FXML private TableColumn<Linha, Linha> colunaData;
    @FXML private TableColumn<Linha, Linha> colunaDescricao;
    @FXML private TableColumn<Linha, Linha> colunaEntrada;
    @FXML private TableColumn<Linha, Linha> colunaSaida;
    @FXML private TableColumn<Linha, Linha> colunaSaldo;

    private Disponibilidades disponiveis;

    @FXML
    private void initialize() {
        abaCaixa.setUserData(Conta.CAIXA);
        abaBanco.setUserData(Conta.BANCO);
        grupoConta.selectToggle(abaCaixa);
        grupoConta.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                grupoConta.selectToggle(antes); // sempre uma aba marcada
            } else {
                carregarMovimento();
            }
        });
        campoMes.setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth mes) {
                if (mes == null) {
                    return "";
                }
                String texto = mes.format(MES);
                return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
            }

            @Override
            public YearMonth fromString(String texto) {
                return null;
            }
        });
        campoMes.valueProperty().addListener((obs, antes, agora) -> {
            atualizarSetas();
            carregarMovimento();
        });
        montarTabela();
        Permissoes.mostrarSe(botaoDepositar, Acao.DEPOSITAR_NO_BANCO);
        barra.widthProperty().addListener((obs, antes, agora) -> ajustarBarra());
        carregarTudo();
    }

    private void carregarTudo() {
        try {
            disponiveis = servico.disponibilidades();
            dataDisponibilidades.setText(Formatos.data(disponiveis.dia()));
            saldoCaixa.setText(Formatos.moeda(disponiveis.caixa()));
            saldoBanco.setText(Formatos.moeda(disponiveis.banco()));
            saldoTotal.setText(Formatos.moeda(disponiveis.total()));
            percentualCaixa.setText(percentual(disponiveis.caixa()) + "%");
            percentualBanco.setText(percentual(disponiveis.banco()) + "%");
            ajustarBarra();
            mostrarSaldoInicial();
            List<YearMonth> meses = servico.meses();
            campoMes.getItems().setAll(meses);
            campoMes.setValue(meses.get(0)); // o mês atual
        } catch (RegraNegocioException e) {
            textoSaldoInicial.setText(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            textoSaldoInicial.setText("Não foi possível ler o Caixa e o Banco no banco de dados.");
        }
    }

    /** Aviso amarelo e botão enquanto falta o saldo inicial de alguma conta. */
    private void mostrarSaldoInicial() throws SQLException {
        List<Conta> faltam = servico.semSaldoInicial();
        boolean podeInformar = Sessao.pode(Acao.INFORMAR_SALDO_INICIAL);
        mostrar(botaoSaldoInicial, !faltam.isEmpty() && podeInformar);
        mostrar(avisoSaldoInicial, !faltam.isEmpty());
        if (!faltam.isEmpty()) {
            String quais = faltam.size() == 2 ? "do Caixa e do Banco" : "do " + faltam.get(0);
            textoAvisoSaldo.setText("Falta informar o saldo inicial " + quais + ". Até lá, o saldo mostra só o que "
                    + "entrou pelo sistema." + (podeInformar ? " Use \"Informar saldo inicial\"."
                            : " Peça ao Administrador para informar."));
        }
        StringBuilder texto = new StringBuilder();
        for (Conta conta : Conta.values()) {
            servico.saldoInicial(conta).ifPresent(l -> texto.append("Saldo inicial do ").append(conta).append(": ")
                    .append(Formatos.moeda(l.valor())).append(" em ").append(Formatos.data(l.data())).append(". "));
        }
        textoSaldoInicial.setText(texto.toString().trim());
        mostrar(textoSaldoInicial, texto.length() > 0);
    }

    private void carregarMovimento() {
        YearMonth mes = campoMes.getValue();
        Conta conta = (Conta) grupoConta.getSelectedToggle().getUserData();
        if (mes == null) {
            return;
        }
        descricaoConta.setText(conta == Conta.CAIXA ? "Dinheiro e cheque recebidos na Fundação"
                : "Total das contas bancárias: PIX, transferência e boleto");
        try {
            Movimento m = servico.movimento(conta, mes);
            cartoesMes.getChildren().setAll(cartao("Saldo anterior", m.saldoAnterior(), null),
                    cartao("Entradas", m.entradas(), "kpi-valor-entrada"),
                    cartao("Saídas", m.saidas(), m.saidas().signum() > 0 ? "kpi-valor-saida" : null),
                    cartao("Saldo final", m.saldoFinal(), null));
            tabela.getItems().setAll(m.linhas());
            tabela.setPlaceholder(new Label("Nenhum lançamento no " + conta + " em "
                    + campoMes.getConverter().toString(mes).toLowerCase(Locale.forLanguageTag("pt-BR")) + "."));
        } catch (RegraNegocioException e) {
            tabela.setPlaceholder(new Label(String.join(" ", e.getErros())));
        } catch (SQLException e) {
            tabela.setPlaceholder(new Label("Não foi possível ler o movimento no banco de dados."));
        }
    }

    private static VBox cartao(String rotulo, BigDecimal valor, String estiloValor) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("kpi-rotulo");
        Label v = new Label(Formatos.moeda(valor));
        v.getStyleClass().add("kpi-valor");
        if (estiloValor != null) {
            v.getStyleClass().add(estiloValor);
        }
        VBox cartao = new VBox(8, r, v);
        cartao.getStyleClass().add("kpi");
        HBox.setHgrow(cartao, Priority.ALWAYS);
        cartao.setMaxWidth(Double.MAX_VALUE);
        return cartao;
    }

    private void montarTabela() {
        colunaData.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaData.setCellFactory(c -> celula(l -> new Label(Formatos.data(l.lancamento().data())), false));
        colunaDescricao.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaDescricao.setCellFactory(c -> celula(l -> {
            Lancamento lanc = l.lancamento();
            Label titulo = new Label(lanc.tipo().getDescricao());
            titulo.getStyleClass().add("celula-nome");
            Label detalhe = new Label(lanc.descricao());
            detalhe.getStyleClass().add("celula-detalhe");
            return new VBox(2, titulo, detalhe);
        }, false));
        colunaEntrada.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaEntrada.setCellFactory(c -> celula(l -> new Label(l.lancamento().entrada()
                ? Formatos.moeda(l.lancamento().valor()) : ""), true));
        colunaSaida.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaSaida.setCellFactory(c -> celula(l -> new Label(l.lancamento().entrada()
                ? "" : Formatos.moeda(l.lancamento().valor().negate())), true));
        colunaSaldo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaSaldo.setCellFactory(c -> celula(l -> new Label(Formatos.moeda(l.saldoDepois())), true));
    }

    private static <T> TableCell<T, T> celula(Function<T, Node> desenho, boolean numero) {
        TableCell<T, T> celula = new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean vazio) {
                super.updateItem(item, vazio);
                setGraphic(vazio || item == null ? null : desenho.apply(item));
            }
        };
        if (numero) {
            celula.getStyleClass().add("celula-numero");
        }
        return celula;
    }

    /** A barra colorida: o tamanho de cada parte acompanha quanto há no Caixa e no Banco. */
    private void ajustarBarra() {
        if (disponiveis == null) {
            return;
        }
        BigDecimal total = disponiveis.total();
        double largura = Math.max(0, barra.getWidth() - barra.getSpacing() - 1);
        barra.setMinWidth(0);
        barra.setPrefWidth(0);
        boolean temCaixa = disponiveis.caixa().signum() > 0;
        boolean temBanco = disponiveis.banco().signum() > 0;
        mostrar(barraCaixa, temCaixa || total.signum() <= 0);
        mostrar(barraBanco, temBanco || total.signum() <= 0);
        double fracaoCaixa = total.signum() <= 0 ? 0.5
                : disponiveis.caixa().max(BigDecimal.ZERO).doubleValue() / total.doubleValue();
        double caixa = Math.floor(largura * fracaoCaixa);
        tamanho(barraCaixa, temBanco || total.signum() <= 0 ? caixa : largura);
        tamanho(barraBanco, temCaixa || total.signum() <= 0 ? largura - caixa : largura);
    }

    private static void tamanho(Region r, double largura) {
        r.setMinWidth(0);
        r.setPrefWidth(Math.max(0, largura));
        r.setMaxWidth(Math.max(0, largura));
    }

    private String percentual(BigDecimal valor) {
        BigDecimal total = disponiveis.total();
        if (total.signum() <= 0) {
            return "0";
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).toPlainString();
    }

    @FXML
    private void informarSaldoInicial() {
        SaldoInicialController.abrir(servico, this::carregarTudo);
    }

    @FXML
    private void depositarNoBanco() {
        DepositoController.abrir(servico, this::carregarTudo);
    }

    // ---------- seletor de mês (a lista vem do mais novo para o mais antigo) ----------

    @FXML
    private void irMesAnterior() {
        andarMes(+1);
    }

    @FXML
    private void irMesSeguinte() {
        andarMes(-1);
    }

    private void andarMes(int passo) {
        int i = campoMes.getItems().indexOf(campoMes.getValue()) + passo;
        if (i >= 0 && i < campoMes.getItems().size()) {
            campoMes.setValue(campoMes.getItems().get(i));
        }
    }

    private void atualizarSetas() {
        int i = campoMes.getItems().indexOf(campoMes.getValue());
        mesAnterior.setDisable(i < 0 || i >= campoMes.getItems().size() - 1);
        mesSeguinte.setDisable(i <= 0);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
