package br.org.fmc.ui;

import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCobranca;
import br.org.fmc.service.ServicoCobranca.Linha;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * [D7] BOARD-64 · Janela para registrar o que deu uma ligação de cobrança: escolhe-se o
 * resultado; se o inquilino prometeu pagar, a data. Mostra também os contatos anteriores.
 */
public class RegistrarContatoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Linha linha;
    private ServicoCobranca servico;
    private Consumer<ContatoCobranca> depoisDeRegistrar;

    @FXML private StackPane veu;
    @FXML private Label subtitulo;
    @FXML private Label resumoDivida;
    @FXML private FlowPane opcoes;
    @FXML private ToggleGroup grupoResultado;
    @FXML private VBox blocoPromessa;
    @FXML private DatePicker campoData;
    @FXML private Label dicaPromessa;
    @FXML private TextArea campoObservacao;
    @FXML private Label tituloHistorico;
    @FXML private VBox historico;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;

    public static void abrir(Linha linha, ServicoCobranca servico,
            Consumer<ContatoCobranca> depoisDeRegistrar) {
        FXMLLoader carregador = new FXMLLoader(
                RegistrarContatoController.class.getResource("/br/org/fmc/ui/registrar-contato.fxml"));
        try {
            Node janela = carregador.load();
            RegistrarContatoController controle = carregador.getController();
            controle.preencher(linha, servico, depoisDeRegistrar);
            Navegador.mostrarJanela(janela);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir registrar-contato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        mostrar(avisoErros, false);
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
        // Uma opção por resultado; "Prometeu pagar" abre o campo da data.
        for (ResultadoContato resultado : ResultadoContato.values()) {
            ToggleButton opcao = new ToggleButton(resultado.getRotulo());
            opcao.setUserData(resultado);
            opcao.setToggleGroup(grupoResultado);
            opcao.getStyleClass().add("opcao-contato");
            opcoes.getChildren().add(opcao);
        }
        grupoResultado.selectedToggleProperty().addListener((obs, antes, agora) -> {
            boolean promessa = agora != null && agora.getUserData() == ResultadoContato.PROMETEU_PAGAR;
            mostrar(blocoPromessa, promessa);
            if (promessa) {
                campoData.requestFocus();
            }
        });
        mostrar(blocoPromessa, false);
        formatarData(campoData);
        campoData.valueProperty().addListener((obs, antes, agora) -> atualizarDica());
        atualizarDica();
    }

    private void preencher(Linha linha, ServicoCobranca servico,
            Consumer<ContatoCobranca> depoisDeRegistrar) {
        this.linha = linha;
        this.servico = servico;
        this.depoisDeRegistrar = depoisDeRegistrar;
        String telefone = linha.contrato().getInquilino().getTelefone();
        subtitulo.setText(linha.contrato().getInquilino().getNome()
                + (telefone == null || telefone.isBlank() ? "" : " · " + telefone));
        resumoDivida.setText("Valor atualizado para informar ao inquilino: " + Formatos.moeda(linha.valorAtualizado())
                + " (" + linha.mesesEmAberto() + " meses em aberto: " + competencias() + ").");
        mostrarHistorico();
    }

    /** Todos os contatos do contrato, do mais recente para o mais antigo: data, quem e o que deu. */
    private void mostrarHistorico() {
        List<ContatoCobranca> contatos;
        try {
            contatos = servico.historico(linha.contrato().getId());
        } catch (RegraNegocioException | SQLException e) {
            contatos = List.of();
        }
        tituloHistorico.setText(contatos.isEmpty() ? "Contatos anteriores"
                : "Contatos anteriores · " + contatos.size());
        historico.getChildren().clear();
        if (contatos.isEmpty()) {
            Label nenhum = new Label("Nenhum contato registrado ainda. Este será o primeiro.");
            nenhum.getStyleClass().add("texto");
            historico.getChildren().add(nenhum);
            return;
        }
        for (ContatoCobranca c : contatos) {
            historico.getChildren().add(linhaDoHistorico(c));
        }
    }

    /** Uma linha do histórico: "10/11/2026 · Ana (Secretaria)" e embaixo o que deu. */
    static VBox linhaDoHistorico(ContatoCobranca c) {
        Label quando = new Label(Formatos.data(c.registradoEm().toLocalDate()) + " · " + c.feitoPor().getNome());
        quando.getStyleClass().add("historico-quando");
        Label oQue = new Label(c.descricao());
        oQue.setWrapText(true);
        oQue.setMinHeight(Region.USE_PREF_SIZE);
        oQue.getStyleClass().add("historico-texto");
        VBox item = new VBox(2, quando, oQue);
        item.getStyleClass().add("historico-item");
        return item;
    }

    private String competencias() {
        return String.join(", ", linha.alugueis().stream()
                .map(a -> String.format("%02d/%d", a.getCompetencia().getMonthValue(), a.getCompetencia().getYear()))
                .toList());
    }

    @FXML
    private void em7Dias() {
        campoData.setValue(servico.hoje().plusDays(7));
    }

    @FXML
    private void em15Dias() {
        campoData.setValue(servico.hoje().plusDays(15));
    }

    @FXML
    private void em30Dias() {
        campoData.setValue(servico.hoje().plusDays(30));
    }

    private void atualizarDica() {
        LocalDate data = campoData.getValue();
        String aviso = "O Painel avisa a partir de " + RegrasNegocio.DIAS_AVISO_PROMESSA
                + " dias antes. Se a data passar sem pagamento, a promessa aparece como não cumprida.";
        if (data != null && servico != null) {
            long dias = ChronoUnit.DAYS.between(servico.hoje(), data);
            if (dias >= 0) {
                aviso = ServicoCobranca.faltam(dias).replace("Vence hoje", "É hoje") + ". " + aviso;
            }
        }
        dicaPromessa.setText(aviso);
    }

    @FXML
    private void salvar() {
        ResultadoContato tipo = grupoResultado.getSelectedToggle() == null ? null
                : (ResultadoContato) grupoResultado.getSelectedToggle().getUserData();
        LocalDate data = tipo == ResultadoContato.PROMETEU_PAGAR ? lerData(campoData) : null;
        try {
            ContatoCobranca contato = servico.registrarContato(linha.contrato().getId(), tipo, data,
                    campoObservacao.getText());
            fechar();
            depoisDeRegistrar.accept(contato);
        } catch (RegraNegocioException e) {
            mostrarErros(String.join("\n", e.getErros()));
        } catch (SQLException e) {
            mostrarErros("Não foi possível salvar o contato: " + e.getMessage());
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
}
