package br.org.fmc.ui;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Inquilino;
import br.org.fmc.service.DadosInquilino;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoInquilinos;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Controla a tela "Inquilinos" (ui/inquilinos.fxml).
 * Só cuida da tela: as regras (CPF válido, CPF repetido, quem pode) ficam no ServicoInquilinos.
 */
public class InquilinosController {

    private final ServicoInquilinos servico = new ServicoInquilinos();

    /** Inquilino aberto no formulário para editar; null quando é um cadastro novo. */
    private Inquilino emEdicao;

    @FXML
    private TableView<Inquilino> tabela;
    @FXML
    private TableColumn<Inquilino, String> colunaNome;
    @FXML
    private TableColumn<Inquilino, String> colunaCpf;
    @FXML
    private TableColumn<Inquilino, String> colunaTelefone;
    @FXML
    private TableColumn<Inquilino, String> colunaRenda;

    @FXML
    private ScrollPane formulario;
    @FXML
    private Label tituloFormulario;
    @FXML
    private TextField campoNome;
    @FXML
    private TextField campoCpf;
    @FXML
    private TextField campoTelefone;
    @FXML
    private TextField campoEmail;
    @FXML
    private ComboBox<EstadoCivil> campoEstadoCivil;
    @FXML
    private TextField campoProfissao;
    @FXML
    private TextField campoLocalTrabalho;
    @FXML
    private TextField campoRenda;
    @FXML
    private TextField campoOutrasRendas;
    @FXML
    private VBox blocoConjuge;
    @FXML
    private TextField campoConjugeNome;
    @FXML
    private TextField campoConjugeCpf;
    @FXML
    private TextField campoConjugeRenda;
    @FXML
    private Label mensagem;
    @FXML
    private Button botaoSalvar;

    @FXML
    private void initialize() {
        colunaNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNome()));
        colunaCpf.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCpf().formatado()));
        colunaTelefone.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTelefone() == null ? "—" : c.getValue().getTelefone()));
        colunaRenda.setCellValueFactory(c -> new SimpleStringProperty(Formatos.moeda(c.getValue().rendaConsiderada())));
        tabela.setPlaceholder(new Label("Nenhum inquilino cadastrado ainda."));

        campoEstadoCivil.getItems().setAll(EstadoCivil.values());
        campoEstadoCivil.valueProperty().addListener((obs, antes, agora) -> mostrarConjuge());

        // O Financeiro só consulta: vê a lista, mas não o formulário.
        Permissoes.mostrarSe(formulario, Acao.CADASTRAR_INQUILINO);
        if (Sessao.pode(Acao.CADASTRAR_INQUILINO)) {
            tabela.getSelectionModel().selectedItemProperty().addListener((obs, antes, agora) -> {
                if (agora != null) {
                    abrirParaEditar(agora);
                }
            });
        }

        novo();
        carregarLista();
    }

    /** Botão "Cadastrar inquilino" / "Salvar alterações". */
    @FXML
    private void salvar() {
        limparMensagem();
        try {
            boolean cadastroNovo = emEdicao == null;
            Inquilino salvo = cadastroNovo
                    ? servico.cadastrar(lerFormulario())
                    : servico.editar(emEdicao.getId(), lerFormulario());
            carregarLista();
            // Selecionar abre o inquilino no formulário (e limpa a mensagem), por isso a mensagem vem depois.
            selecionar(salvo.getId());
            mostrar((cadastroNovo ? "Inquilino cadastrado: " : "Alterações salvas: ") + salvo.getNome() + ".", false);
        } catch (RegraNegocioException e) {
            mostrar(String.join("\n", e.getErros()), true);
        } catch (SQLException e) {
            mostrar("Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    /** Botão "Limpar / novo inquilino": esvazia o formulário para um cadastro novo. */
    @FXML
    private void novo() {
        emEdicao = null;
        tabela.getSelectionModel().clearSelection();
        tituloFormulario.setText("Novo inquilino");
        botaoSalvar.setText("Cadastrar inquilino");
        for (TextField campo : new TextField[] {campoNome, campoCpf, campoTelefone, campoEmail, campoProfissao,
            campoLocalTrabalho, campoRenda, campoOutrasRendas, campoConjugeNome, campoConjugeCpf, campoConjugeRenda}) {
            campo.clear();
        }
        campoEstadoCivil.setValue(EstadoCivil.SOLTEIRO);
        limparMensagem();
        campoNome.requestFocus();
    }

    private void abrirParaEditar(Inquilino i) {
        emEdicao = i;
        tituloFormulario.setText("Editar inquilino");
        botaoSalvar.setText("Salvar alterações");
        campoNome.setText(i.getNome());
        campoCpf.setText(i.getCpf().formatado());
        campoTelefone.setText(textoOuVazio(i.getTelefone()));
        campoEmail.setText(textoOuVazio(i.getEmail()));
        campoEstadoCivil.setValue(i.getEstadoCivil());
        campoProfissao.setText(textoOuVazio(i.getProfissao()));
        campoLocalTrabalho.setText(textoOuVazio(i.getLocalTrabalho()));
        campoRenda.setText(valorOuVazio(i.getRendaMensal()));
        campoOutrasRendas.setText(valorOuVazio(i.getOutrasRendas()));
        Conjuge c = i.getConjuge();
        campoConjugeNome.setText(c == null ? "" : c.getNome());
        campoConjugeCpf.setText(c == null || c.getCpf() == null ? "" : c.getCpf().formatado());
        campoConjugeRenda.setText(c == null ? "" : valorOuVazio(c.getRenda()));
        limparMensagem();
    }

    private DadosInquilino lerFormulario() {
        return new DadosInquilino(campoNome.getText(), campoCpf.getText(), campoTelefone.getText(),
                campoEmail.getText(), campoEstadoCivil.getValue(), campoProfissao.getText(),
                campoLocalTrabalho.getText(), campoRenda.getText(), campoOutrasRendas.getText(),
                campoConjugeNome.getText(), campoConjugeCpf.getText(), campoConjugeRenda.getText());
    }

    /** Os campos do cônjuge só aparecem para casado(a) ou união estável. */
    private void mostrarConjuge() {
        EstadoCivil estado = campoEstadoCivil.getValue();
        boolean temConjuge = estado != null && estado.temConjuge();
        blocoConjuge.setVisible(temConjuge);
        blocoConjuge.setManaged(temConjuge);
    }

    private void carregarLista() {
        try {
            tabela.getItems().setAll(servico.listar());
        } catch (RegraNegocioException e) {
            mostrar(String.join("\n", e.getErros()), true);
        } catch (SQLException e) {
            mostrar("Erro no banco de dados: " + e.getMessage(), true);
        }
    }

    /** Deixa selecionado na lista o inquilino que acabou de ser salvo. */
    private void selecionar(long id) {
        tabela.getItems().stream()
                .filter(i -> i.getId() == id)
                .findFirst()
                .ifPresent(i -> tabela.getSelectionModel().select(i));
    }

    private static String textoOuVazio(String texto) {
        return texto == null ? "" : texto;
    }

    /** Zero aparece como campo vazio, para não poluir o formulário. */
    private static String valorOuVazio(BigDecimal valor) {
        return valor == null || valor.signum() == 0 ? "" : Formatos.numero(valor);
    }

    private void mostrar(String texto, boolean erro) {
        mensagem.setText(texto);
        mensagem.getStyleClass().setAll("label", erro ? "erro" : "sucesso");
        mensagem.setVisible(true);
        mensagem.setManaged(true);
    }

    private void limparMensagem() {
        mensagem.setText("");
        mensagem.setVisible(false);
        mensagem.setManaged(false);
    }
}
