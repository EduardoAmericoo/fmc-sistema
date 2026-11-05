package br.org.fmc.ui;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Recibo;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoRecebimento;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

/** Janela para receber um aluguel e emitir o recibo correspondente. */
public class ReceberAluguelController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private Aluguel aluguel;
    private ServicoRecebimento servico;
    private Consumer<Recibo> depoisDeReceber;

    @FXML private StackPane veu;
    @FXML private Label subtitulo;
    @FXML private Label competencia;
    @FXML private Label vencimento;
    @FXML private Label tolerancia;
    @FXML private Label valorAluguel;
    @FXML private Label taxaLuz;
    @FXML private Label iptu;
    @FXML private Label outros;
    @FXML private Label valorDevido;
    @FXML private Label atraso;
    @FXML private Label multa;
    @FXML private Label juros;
    @FXML private Label correcao;
    @FXML private Label total;
    @FXML private DatePicker campoData;
    @FXML private ComboBox<FormaPagamento> campoForma;
    @FXML private TextField campoDocumento;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;

    public static void abrir(Aluguel aluguel, ServicoRecebimento servico, Consumer<Recibo> depoisDeReceber) {
        FXMLLoader carregador = new FXMLLoader(
                ReceberAluguelController.class.getResource("/br/org/fmc/ui/receber-aluguel.fxml"));
        try {
            Node janela = carregador.load();
            ReceberAluguelController controle = carregador.getController();
            controle.preencher(aluguel, servico, depoisDeReceber);
            Navegador.mostrarJanela(janela);
            controle.campoData.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel abrir receber-aluguel.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        campoForma.getItems().setAll(FormaPagamento.values());
        campoForma.setValue(FormaPagamento.PIX);
        campoData.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate data) {
                return data == null ? "" : data.format(DATA);
            }

            @Override
            public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.trim(), DATA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        campoData.valueProperty().addListener((obs, antes, agora) -> atualizarEncargos());
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        mostrar(avisoErros, false);
    }

    private void preencher(Aluguel aluguel, ServicoRecebimento servico, Consumer<Recibo> depoisDeReceber) {
        this.aluguel = aluguel;
        this.servico = servico;
        this.depoisDeReceber = depoisDeReceber;
        var contrato = aluguel.getContrato();
        var imovel = contrato.getImovel();
        subtitulo.setText(contrato.getInquilino().getNome() + " · " + imovel.getCodigo() + " · " + imovel.getEndereco());
        competencia.setText(aluguel.getCompetencia().getMonthValue() + "/" + aluguel.getCompetencia().getYear());
        vencimento.setText(Formatos.data(aluguel.getVencimento()));
        tolerancia.setText(Formatos.data(aluguel.getVencimento().plusDays(br.org.fmc.model.RegrasNegocio.TOLERANCIA_DIAS)));
        valorAluguel.setText(Formatos.moeda(aluguel.getValorAluguel()));
        taxaLuz.setText(Formatos.moeda(aluguel.getTaxaLuz()));
        iptu.setText(Formatos.moeda(aluguel.getValorIptu()));
        outros.setText(Formatos.moeda(aluguel.getOutros()));
        campoData.setValue(servico.hoje());
        atualizarEncargos();
    }

    private void atualizarEncargos() {
        if (aluguel == null || servico == null || campoData.getValue() == null) {
            return;
        }
        try {
            Encargos encargos = servico.calcularEncargos(aluguel, campoData.getValue());
            valorDevido.setText(Formatos.moeda(aluguel.base()));
            atraso.setText(encargos.getDiasAtraso() + " dias · " + encargos.getMeses() + " meses (ou fração)");
            multa.setText(Formatos.moeda(encargos.getMulta()));
            juros.setText(Formatos.moeda(encargos.getJuros()));
            correcao.setText(Formatos.moeda(encargos.getCorrecao()));
            total.setText(Formatos.moeda(aluguel.base().add(encargos.total())));
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        }
    }

    @FXML
    private void confirmar() {
        LocalDate data = campoData.getConverter().fromString(campoData.getEditor().getText());
        if (data == null) {
            mostrarErros("Informe uma data de pagamento válida.");
            return;
        }
        try {
            Recibo recibo = servico.receber(aluguel, data, campoForma.getValue(), campoDocumento.getText());
            fechar();
            depoisDeReceber.accept(recibo);
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Nao foi possivel registrar o recebimento: " + e.getMessage());
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
