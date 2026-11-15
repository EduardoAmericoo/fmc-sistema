package br.org.fmc.ui;

import br.org.fmc.model.Conta;
import br.org.fmc.model.Lancamento;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCaixaBanco;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

/** [D5] BOARD-63 · Janela para informar, uma vez, o saldo inicial do Caixa e do Banco. */
public class SaldoInicialController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ServicoCaixaBanco servico;
    private Runnable depoisDeSalvar;

    @FXML private StackPane veu;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private DatePicker campoData;
    @FXML private TextField campoCaixa;
    @FXML private Label jaCaixa;
    @FXML private TextField campoBanco;
    @FXML private Label jaBanco;

    public static void abrir(ServicoCaixaBanco servico, Runnable depoisDeSalvar) {
        FXMLLoader carregador = new FXMLLoader(SaldoInicialController.class.getResource("/br/org/fmc/ui/saldo-inicial.fxml"));
        try {
            Node janela = carregador.load();
            SaldoInicialController controle = carregador.getController();
            controle.preencher(servico, depoisDeSalvar);
            Navegador.mostrarJanela(janela);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir saldo-inicial.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        Mascara.dinheiro(campoCaixa);
        Mascara.dinheiro(campoBanco);
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
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(ServicoCaixaBanco servico, Runnable depoisDeSalvar) {
        this.servico = servico;
        this.depoisDeSalvar = depoisDeSalvar;
        campoData.setValue(servico.hoje());
        try {
            travarSeJaInformado(servico.saldoInicial(Conta.CAIXA), campoCaixa, jaCaixa);
            travarSeJaInformado(servico.saldoInicial(Conta.BANCO), campoBanco, jaBanco);
        } catch (SQLException | RegraNegocioException e) {
            mostrarErros("Não foi possível ler os saldos já informados.");
        }
    }

    /** A conta que já tem saldo inicial fica travada, mostrando o valor e a data. */
    private static void travarSeJaInformado(Optional<Lancamento> existente, TextField campo, Label dica) {
        if (existente.isPresent()) {
            campo.setText(Formatos.numero(existente.get().valor()));
            campo.setDisable(true);
            dica.setText("Já informado em " + Formatos.data(existente.get().data()) + ".");
        }
        mostrar(dica, existente.isPresent());
    }

    @FXML
    private void salvar() {
        try {
            LocalDate data = campoData.getConverter().fromString(campoData.getEditor().getText());
            servico.informarSaldoInicial(data, valor(campoCaixa), valor(campoBanco));
            fechar();
            depoisDeSalvar.run();
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (IllegalArgumentException e) {
            mostrarErros("Valor inválido. Use o formato 1.234,56.");
        } catch (SQLException e) {
            mostrarErros("Não foi possível salvar no banco de dados.");
        }
    }

    /** Campo travado ou em branco = não informar agora. */
    private static BigDecimal valor(TextField campo) {
        return campo.isDisable() || campo.getText().isBlank() ? null : Formatos.lerValor(campo.getText());
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
