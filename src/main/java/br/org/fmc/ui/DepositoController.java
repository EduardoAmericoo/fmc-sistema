package br.org.fmc.ui;

import br.org.fmc.model.Conta;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCaixaBanco;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

/** Janela "Depositar ou sacar" (revisão da SR2): dinheiro do Caixa levado ao banco, ou sacado do banco para o Caixa. */
public class DepositoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ServicoCaixaBanco servico;
    private Runnable depoisDeSalvar;

    @FXML private StackPane veu;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private DatePicker campoData;
    @FXML private TextField campoValor;
    @FXML private Label dicaDisponivel;
    @FXML private TextField campoComprovante;
    @FXML private ToggleGroup grupoSentido;
    @FXML private RadioButton opcaoDeposito;
    @FXML private Label rotuloData;
    @FXML private Label rotuloValor;
    @FXML private Label explicacao;
    @FXML private Button botaoSalvar;

    /** De onde sai o dinheiro: Caixa (depósito) ou Banco (saque). */
    private Conta origem() {
        return opcaoDeposito.isSelected() ? Conta.CAIXA : Conta.BANCO;
    }

    public static void abrir(ServicoCaixaBanco servico, Runnable depoisDeSalvar) {
        FXMLLoader carregador = new FXMLLoader(DepositoController.class.getResource("/br/org/fmc/ui/deposito.fxml"));
        try {
            Node janela = carregador.load();
            DepositoController controle = carregador.getController();
            controle.preencher(servico, depoisDeSalvar);
            Navegador.mostrarJanela(janela);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir deposito.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        Mascara.dinheiro(campoValor);
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
        campoData.valueProperty().addListener((obs, antes, agora) -> mostrarDisponivel());
        grupoSentido.selectedToggleProperty().addListener((obs, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true); // sempre um dos dois marcado
                return;
            }
            mostrarSentido();
        });
        mostrarSentido();
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
        mostrarDisponivel();
    }

    /** Troca os textos da janela conforme a escolha: depósito ou saque. */
    private void mostrarSentido() {
        boolean deposito = origem() == Conta.CAIXA;
        rotuloData.setText(deposito ? "Data do depósito *" : "Data do saque *");
        rotuloValor.setText(deposito ? "Valor depositado *" : "Valor sacado *");
        botaoSalvar.setText(deposito ? "Registrar depósito" : "Registrar saque");
        explicacao.setText(deposito
                ? "O valor sai do Caixa e entra no Banco na mesma data. O total da Fundação não muda: só troca de lugar."
                : "O valor sai do Banco e entra no Caixa na mesma data. Use para o troco do caixa ou para um "
                        + "pagamento pequeno em dinheiro.");
        mostrar(avisoErros, false);
        mostrarDisponivel();
    }

    /** Embaixo do valor: quanto a conta de origem tem para depositar ou sacar na data escolhida. */
    private void mostrarDisponivel() {
        LocalDate data = campoData.getValue();
        if (servico == null || data == null) {
            dicaDisponivel.setText("");
            return;
        }
        try {
            Conta origem = origem();
            BigDecimal disponivel = servico.disponivelEm(origem, data);
            dicaDisponivel.setText("O " + origem + " tem " + Formatos.moeda(disponivel) + " para "
                    + (origem == Conta.CAIXA ? "depositar" : "sacar") + " em " + Formatos.data(data) + ".");
        } catch (SQLException | RegraNegocioException e) {
            dicaDisponivel.setText("");
        }
    }

    @FXML
    private void salvar() {
        try {
            LocalDate data = campoData.getConverter().fromString(campoData.getEditor().getText());
            BigDecimal valor = campoValor.getText().isBlank() ? null : Formatos.lerValor(campoValor.getText());
            servico.transferir(origem(), data, valor, campoComprovante.getText());
            fechar();
            depoisDeSalvar.run();
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (IllegalArgumentException e) {
            mostrarErros("Valor inválido. Use o formato 1.234,56.");
        } catch (SQLException e) {
            mostrarErros("Não foi possível salvar no banco de dados. Nada foi gravado.");
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
