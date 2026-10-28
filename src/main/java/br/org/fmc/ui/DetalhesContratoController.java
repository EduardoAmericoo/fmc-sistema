package br.org.fmc.ui;

import br.org.fmc.model.Caucao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Encerramento;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.Garantia;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.SituacaoContrato;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.time.LocalDate;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controla a janela "Detalhes do contrato" (ui/detalhes-contrato.fxml), BOARD-59.
 * Só mostra o que já está no contrato carregado: não grava nada nem chama o banco.
 */
public class DetalhesContratoController {

    private static final String SEM_DADO = "—";

    @FXML
    private StackPane veu;
    @FXML
    private Label titulo;
    @FXML
    private Label subtitulo;
    @FXML
    private Label seloSituacao;
    @FXML
    private VBox corpo;

    /** Abre a janela com os detalhes do contrato escolhido. */
    public static void abrir(Contrato contrato) {
        FXMLLoader carregador = new FXMLLoader(
                DetalhesContratoController.class.getResource("/br/org/fmc/ui/detalhes-contrato.fxml"));
        try {
            Node janela = carregador.load();
            DetalhesContratoController controle = carregador.getController();
            controle.preencher(contrato);
            Navegador.mostrarJanela(janela);
            janela.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela detalhes-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        // Esc fecha a janela.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Contrato c) {
        titulo.setText("Contrato " + c.getNumero());
        subtitulo.setText(c.getImovel().getCodigo() + " · " + c.getInquilino().getNome());

        SituacaoContrato situacao = SituacaoContrato.de(c, LocalDate.now());
        seloSituacao.setText(switch (situacao) {
            case ATIVO -> "Ativo";
            case VENCENDO -> "Vencendo";
            case VENCIDO -> "Vencido";
            case ENCERRADO -> "Encerrado";
        });
        seloSituacao.getStyleClass().setAll("label", "selo", switch (situacao) {
            case ATIVO -> "selo-sucesso";
            case VENCENDO -> "selo-alerta";
            case VENCIDO -> "selo-erro";
            case ENCERRADO -> "selo-neutro";
        });

        corpo.getChildren().addAll(
                secaoContrato(c),
                secao("Valores no dia da assinatura",
                        "Aluguel", Formatos.moeda(c.getValorAluguel()),
                        "Taxa de luz", Formatos.moeda(c.getTaxaLuz()),
                        "IPTU", Formatos.moeda(c.getValorIptu())),
                secaoImovel(c.getImovel()),
                secaoInquilino(c.getInquilino()),
                secaoGarantia(c.getGarantia()));
        if (c.getEncerramento() != null) {
            corpo.getChildren().add(secaoEncerramento(c.getEncerramento()));
        }
    }

    private static Node secaoContrato(Contrato c) {
        return secao("Contrato",
                "Número", c.getNumero(),
                "Situação", SituacaoContrato.de(c, LocalDate.now()) == SituacaoContrato.ENCERRADO
                        ? "Encerrado" : c.getProrrogacoes().isEmpty() ? "Ativo" : "Prorrogado",
                "Entrega das chaves", Formatos.data(c.getDataEntregaChaves()),
                "Término", Formatos.data(c.getDataTermino()),
                "Prazo", c.getPrazo().toString(),
                "1º vencimento", Formatos.data(c.primeiroVencimento()),
                "Dia de vencimento", "Todo dia " + c.getDiaVencimento());
    }

    private static Node secaoImovel(Imovel i) {
        return secao("Imóvel",
                "Código", i.getCodigo(),
                "Unidade", texto(i.getUnidade()),
                "Tipo", i.getTipo().toString(),
                "Endereço", i.getEndereco(),
                "Bairro", texto(i.getBairro()));
    }

    private static Node secaoInquilino(Inquilino i) {
        return secao("Inquilino",
                "Nome", i.getNome(),
                "CPF", i.getCpf().formatado(),
                "Telefone", texto(i.getTelefone()),
                "E-mail", texto(i.getEmail()));
    }

    private static Node secaoGarantia(Garantia g) {
        if (g instanceof Caucao caucao) {
            return secao("Garantia: caução",
                    "Valor", Formatos.moeda(caucao.getValor()),
                    "Data de recebimento", Formatos.data(caucao.getDataRecebimento()),
                    "Forma de pagamento", caucao.getFormaPagamento().toString(),
                    "Recibo", texto(caucao.getNumeroRecibo()));
        }
        Fiador fiador = ((Fianca) g).getFiador();
        VBox bloco = new VBox(14, secao("Garantia: fiador",
                "Nome", fiador.getNome(),
                "CPF", fiador.getCpf().formatado(),
                "Telefone", texto(fiador.getTelefone()),
                "Estado civil", fiador.getEstadoCivil().toString(),
                "Renda mensal", Formatos.moeda(fiador.getRendaMensal()),
                "Endereço", texto(fiador.getEndereco())));
        Conjuge conjuge = fiador.getConjuge();
        if (conjuge != null) {
            bloco.getChildren().add(secao("Cônjuge do fiador",
                    "Nome", conjuge.getNome(),
                    "CPF", conjuge.getCpf() == null ? SEM_DADO : conjuge.getCpf().formatado()));
        }
        return bloco;
    }

    private static Node secaoEncerramento(Encerramento e) {
        return secao("Encerramento",
                "Chaves devolvidas em", Formatos.data(e.getDataSaida()),
                "Motivo", e.getMotivo().toString(),
                "Observação", texto(e.getObservacao()));
    }

    /** Uma seção: título e, embaixo, os pares "rótulo / valor" em duas colunas. */
    private static Node secao(String titulo, String... paresRotuloValor) {
        Label cabecalho = new Label(titulo);
        cabecalho.getStyleClass().add("secao-formulario");

        GridPane grade = new GridPane();
        grade.setHgap(24);
        grade.setVgap(14);
        for (int coluna = 0; coluna < 2; coluna++) {
            ColumnConstraints largura = new ColumnConstraints();
            largura.setPercentWidth(50);
            grade.getColumnConstraints().add(largura);
        }
        for (int n = 0; n < paresRotuloValor.length / 2; n++) {
            Label rotulo = new Label(paresRotuloValor[2 * n]);
            rotulo.getStyleClass().add("rotulo");
            Label valor = new Label(paresRotuloValor[2 * n + 1]);
            valor.getStyleClass().add("celula-nome");
            valor.setWrapText(true);
            valor.setMinHeight(Label.USE_PREF_SIZE);
            VBox campo = new VBox(4, rotulo, valor);
            GridPane.setHgrow(campo, Priority.ALWAYS);
            grade.add(campo, n % 2, n / 2);
        }
        VBox secao = new VBox(10, cabecalho, grade);
        secao.setPadding(new Insets(0));
        return secao;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? SEM_DADO : valor;
    }

    /** Botões "Fechar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }
}
