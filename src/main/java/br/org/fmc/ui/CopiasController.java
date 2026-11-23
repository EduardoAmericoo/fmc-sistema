package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoCopiaSeguranca;
import br.org.fmc.service.ServicoCopiaSeguranca.Copia;
import br.org.fmc.service.Sessao;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Tela "Cópias de segurança" (revisão da SR2). Só desenha e chama o ServicoCopiaSeguranca:
 * mostra a última cópia, faz uma cópia na hora, salva em pen drive e restaura.
 */
public class CopiasController {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ServicoCopiaSeguranca servico = new ServicoCopiaSeguranca();

    @FXML private HBox situacao;
    @FXML private FontIcon iconeSituacao;
    @FXML private Label textoSituacao;
    @FXML private Label textoAutomatica;
    @FXML private Button botaoFazer;
    @FXML private HBox avisoOk;
    @FXML private Label textoOk;
    @FXML private HBox avisoErros;
    @FXML private Label textoErros;
    @FXML private Label textoPasta;
    @FXML private Button botaoRestaurarArquivo;
    @FXML private TableView<Copia> tabela;
    @FXML private TableColumn<Copia, String> colunaQuando;
    @FXML private TableColumn<Copia, String> colunaTipo;
    @FXML private TableColumn<Copia, String> colunaTamanho;
    @FXML private TableColumn<Copia, Copia> colunaAcoes;

    @FXML
    private void initialize() {
        mostrar(avisoOk, false);
        mostrar(avisoErros, false);
        textoAutomatica.setText("Ao abrir e ao fechar o sistema, ele guarda sozinho a cópia do dia, com os documentos anexados aos contratos. Ficam as cópias dos últimos "
                + ServicoCopiaSeguranca.DIAS_GUARDADOS + " dias; as mais antigas são apagadas.");
        textoPasta.setText("Pasta: " + servico.pasta().toAbsolutePath() + " (os documentos ficam na subpasta documentos)");
        Permissoes.mostrarSe(botaoRestaurarArquivo, Acao.RESTAURAR_COPIA_SEGURANCA);

        colunaQuando.setCellValueFactory(c -> new SimpleStringProperty(quando(c.getValue().feitaEm())));
        colunaTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tipo().getDescricao()));
        colunaTamanho.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tamanho()));
        colunaAcoes.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colunaAcoes.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Copia copia, boolean vazio) {
                super.updateItem(copia, vazio);
                if (vazio || copia == null || !Sessao.pode(Acao.RESTAURAR_COPIA_SEGURANCA)) {
                    setGraphic(null);
                    return;
                }
                Button restaurar = new Button("Restaurar", new FontIcon("fth-rotate-ccw"));
                restaurar.getStyleClass().add("botao-secundario");
                restaurar.setOnAction(e -> restaurar(copia.arquivo(), quando(copia.feitaEm())));
                setGraphic(restaurar);
            }
        });
        tabela.setPlaceholder(new Label("Nenhuma cópia ainda. A primeira é feita ao fechar o sistema, "
                + "ou agora, no botão \"Fazer cópia agora\"."));
        atualizar();
    }

    /** Recarrega a lista e a caixa "Última cópia". */
    private void atualizar() {
        try {
            List<Copia> copias = servico.listar();
            tabela.getItems().setAll(copias);
            mostrarSituacao(copias.isEmpty() ? Optional.empty() : Optional.of(copias.get(0)));
        } catch (IOException e) {
            erro("Não foi possível ler a pasta das cópias: " + e.getMessage());
        }
    }

    private void mostrarSituacao(Optional<Copia> ultima) {
        situacao.getStyleClass().removeAll("veredito-ok", "veredito-alerta");
        if (ultima.isEmpty()) {
            situacao.getStyleClass().add("veredito-alerta");
            iconeSituacao.setIconLiteral("fth-alert-triangle");
            textoSituacao.setText("Ainda não há nenhuma cópia. Faça a primeira agora.");
            return;
        }
        Copia c = ultima.get();
        boolean recente = !c.feitaEm().toLocalDate().isBefore(LocalDate.now().minusDays(1));
        situacao.getStyleClass().add(recente ? "veredito-ok" : "veredito-alerta");
        iconeSituacao.setIconLiteral(recente ? "fth-check-circle" : "fth-alert-triangle");
        textoSituacao.setText("Última cópia: " + quando(c.feitaEm()) + " (" + c.tipo().getDescricao().toLowerCase()
                + ")." + (recente ? "" : " Faz mais de um dia: faça uma cópia agora."));
    }

    @FXML
    private void fazerAgora() {
        executar(() -> {
            Copia feita = servico.fazerAgora();
            ok("Cópia feita: " + feita.arquivo().getFileName() + " (" + feita.tamanho() + ").");
        });
    }

    @FXML
    private void salvarEm() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Salvar cópia de segurança");
        seletor.setInitialFileName("Copia Sistema FMC " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + ".zip");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cópia completa do Sistema FMC (banco e documentos)", "*.zip"));
        File destino = seletor.showSaveDialog(janela());
        if (destino == null) {
            return;
        }
        executar(() -> {
            servico.salvarEm(destino.toPath());
            ok("Cópia salva em " + destino.getAbsolutePath() + ". Guarde o pen drive longe do computador.");
        });
    }

    @FXML
    private void restaurarDeArquivo() {
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Escolher a cópia para restaurar");
        seletor.getExtensionFilters().add(new FileChooser.ExtensionFilter("Cópia do Sistema FMC", "*.zip", "*.db"));
        File escolhida = seletor.showOpenDialog(janela());
        if (escolhida != null) {
            restaurar(escolhida.toPath(), "o arquivo " + escolhida.getName());
        }
    }

    /** Pede confirmação, restaura e manda a pessoa entrar de novo (os usuários também voltam). */
    private void restaurar(Path copia, String deQuando) {
        Alert pergunta = new Alert(Alert.AlertType.CONFIRMATION, "O sistema vai voltar a ficar como estava em "
                + deQuando + ". Tudo o que foi feito depois disso deixa de aparecer.\n\n"
                + "Antes, o sistema guarda uma cópia de como está agora (\"Antes de restaurar\"), para você poder "
                + "voltar atrás se precisar. Depois, todos precisam entrar de novo.",
                ButtonType.OK, ButtonType.CANCEL);
        pergunta.setHeaderText("Restaurar cópia de segurança?");
        pergunta.initOwner(janela());
        if (pergunta.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        executar(() -> {
            servico.restaurar(copia);
            Alert pronto = new Alert(Alert.AlertType.INFORMATION, "A cópia foi restaurada. Entre de novo com o seu "
                    + "e-mail e a senha que valia na data da cópia.", ButtonType.OK);
            pronto.setHeaderText("Cópia restaurada");
            pronto.initOwner(janela());
            pronto.showAndWait();
            Sessao.encerrar();
            Navegador.mostrarLogin();
        });
    }

    private interface Operacao {
        void executar() throws IOException, SQLException;
    }

    private void executar(Operacao operacao) {
        Node raiz = botaoFazer.getScene() == null ? botaoFazer : botaoFazer.getScene().getRoot();
        raiz.setCursor(Cursor.WAIT);
        mostrar(avisoOk, false);
        mostrar(avisoErros, false);
        try {
            operacao.executar();
            atualizar();
        } catch (RegraNegocioException e) {
            erro(String.join("\n", e.getErros()));
        } catch (IOException e) {
            erro("Não foi possível gravar a cópia: " + e.getMessage());
        } catch (SQLException e) {
            erro(Falha.doBanco(e) + " Nada foi alterado.");
        } finally {
            raiz.setCursor(Cursor.DEFAULT);
        }
    }

    private static String quando(LocalDateTime dataHora) {
        LocalDate dia = dataHora.toLocalDate();
        String hora = dataHora.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (dia.equals(LocalDate.now())) {
            return "hoje às " + hora;
        }
        if (dia.equals(LocalDate.now().minusDays(1))) {
            return "ontem às " + hora;
        }
        return dataHora.format(DATA_HORA);
    }

    private javafx.stage.Window janela() {
        return botaoFazer.getScene().getWindow();
    }

    private void ok(String texto) {
        textoOk.setText(texto);
        mostrar(avisoOk, true);
    }

    private void erro(String texto) {
        textoErros.setText(texto);
        mostrar(avisoErros, true);
    }

    private static void mostrar(Node no, boolean visivel) {
        no.setVisible(visivel);
        no.setManaged(visivel);
    }
}
