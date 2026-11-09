package br.org.fmc.ui;

import static br.org.fmc.ui.Detalhe.coluna;
import static br.org.fmc.ui.Detalhe.linha;
import static br.org.fmc.ui.Detalhe.texto;
import static br.org.fmc.ui.Detalhe.titulo;
import static br.org.fmc.ui.Detalhe.tresColunas;
import static br.org.fmc.ui.Detalhe.veredito;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.SituacaoContrato;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoInquilinos;
import br.org.fmc.service.ServicoInquilinos.Ficha;
import br.org.fmc.service.ServicoInquilinos.Pagamento;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Controla a janela "Ficha do inquilino" (ui/ficha-inquilino.fxml), [D8] BOARD-45.
 * Tudo de um inquilino num lugar só: cadastro, contrato, garantia e o histórico de pagamentos.
 * Os dados vêm do ServicoInquilinos.ficha(); aqui só se desenha. Não grava nada.
 */
public class FichaInquilinoController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter COMPETENCIA = DateTimeFormatter.ofPattern("MM/yyyy");

    /** Quantos aluguéis a lista mostra (os mais recentes); a faixa colorida mostra os mesmos. */
    private static final int ALUGUEIS_NA_FICHA = 24;

    @FXML
    private StackPane veu;
    @FXML
    private Label subtitulo;
    @FXML
    private VBox corpo;
    @FXML
    private Button botaoEditar;

    /** O que fazer ao clicar em "Editar cadastro" (a tela de Inquilinos abre o formulário). */
    private Runnable aoEditar;

    /**
     * Abre a ficha do inquilino. Se der erro ao carregar (ex.: banco), devolve a mensagem para
     * a tela mostrar; se abrir, devolve null.
     */
    public static String abrir(Inquilino inquilino, Runnable aoEditar) {
        Ficha ficha;
        try {
            ficha = new ServicoInquilinos().ficha(inquilino.getId());
        } catch (RegraNegocioException e) {
            return String.join("\n", e.getErros());
        } catch (SQLException e) {
            return "Não foi possível carregar a ficha do banco de dados. Tente de novo.";
        }
        FXMLLoader carregador = new FXMLLoader(
                FichaInquilinoController.class.getResource("/br/org/fmc/ui/ficha-inquilino.fxml"));
        try {
            Node janela = carregador.load();
            FichaInquilinoController controle = carregador.getController();
            controle.aoEditar = aoEditar;
            controle.preencher(ficha);
            Navegador.mostrarJanela(janela);
            janela.requestFocus();
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela ficha-inquilino.fxml", e);
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

    private void preencher(Ficha f) {
        Inquilino i = f.inquilino();
        Contrato atual = f.contratoAtual();
        subtitulo.setText(i.getNome() + (atual == null ? "" : " · " + imovel(atual)));
        boolean podeEditar = Sessao.pode(Acao.EDITAR_INQUILINO) && !i.estaArquivado();
        botaoEditar.setVisible(podeEditar);
        botaoEditar.setManaged(podeEditar);

        if (i.estaArquivado()) {
            corpo.getChildren().add(veredito("veredito-neutro", "Arquivado em " + Formatos.data(i.getArquivadoEm())
                    + (i.getMotivoArquivamento() == null ? "" : " · " + i.getMotivoArquivamento())
                    + ". Para voltar a alugar, reative o cadastro em \"Ver arquivados\"."));
        }
        corpo.getChildren().add(tresColunas(colunaLocatario(i), colunaContrato(f, atual),
                atual == null ? coluna("Garantia", linha("Garantia", Detalhe.SEM_DADO))
                        : DetalhesContratoController.colunaGarantia(atual)));
        if (Sessao.pode(Acao.VER_ALUGUEIS)) {
            corpo.getChildren().add(financeiro(f));
        }
        if (!f.contatos().isEmpty()) {
            corpo.getChildren().add(contatosDeCobranca(f));
        }
        if (f.contratos().size() > 1) {
            corpo.getChildren().add(contratosAnteriores(f, atual));
        }
    }

    // ---------- as três colunas ----------

    private static VBox colunaLocatario(Inquilino i) {
        List<Node> linhas = new ArrayList<>(List.of(
                linha("CPF", i.getCpf().formatado()),
                linha("Telefone", texto(i.getTelefone())),
                linha("E-mail", texto(i.getEmail())),
                linha("Profissão", texto(i.getProfissao())),
                linha("Estado civil", i.getEstadoCivil().toString())));
        if (i.getConjuge() != null) {
            linhas.add(linha("Cônjuge", i.getConjuge().getNome()));
        }
        linhas.add(linha("Renda familiar", Formatos.moeda(i.rendaConsiderada())));
        return coluna("Locatário", linhas.toArray(Node[]::new));
    }

    private static VBox colunaContrato(Ficha f, Contrato c) {
        if (c == null) {
            return coluna("Contrato", linha("Contrato", Sessao.pode(Acao.VER_CONTRATOS) ? "Nenhum ainda" : Detalhe.SEM_DADO));
        }
        return coluna("Contrato",
                linha("Imóvel", imovel(c)),
                linha("Contrato", c.getNumero() + " · " + c.getPrazo()),
                linha("Vigência", Formatos.data(c.getDataEntregaChaves()) + " a " + Formatos.data(c.getDataTermino())),
                linha("Aluguel", Formatos.moeda(c.getValorAluguel())),
                linha("Vence todo dia", String.valueOf(c.getDiaVencimento())),
                linha("Situação", situacao(c, f)));
    }

    /** A situação no singular, como no selo da tela de Contratos. */
    private static String situacao(Contrato c, Ficha f) {
        return switch (SituacaoContrato.de(c, f.hoje())) {
            case ATIVO -> "Ativo";
            case VENCENDO -> "Vencendo";
            case VENCIDO -> "Vencido";
            case ENCERRADO -> "Encerrado";
        };
    }

    private static String imovel(Contrato c) {
        String unidade = c.getImovel().getUnidade();
        return c.getImovel().getCodigo() + (unidade == null || unidade.isBlank() ? "" : " · " + unidade);
    }

    // ---------- financeiro ----------

    /** Números de pagamento, a faixa colorida mês a mês e a lista de aluguéis (os mais recentes). */
    private static VBox financeiro(Ficha f) {
        VBox bloco = new VBox(12, titulo("Financeiro"));
        bloco.getChildren().add(tresColunas(
                kpi("Pago, sem encargos", Formatos.moeda(f.totalPago()),
                        f.pagos() + (f.pagos() == 1 ? " aluguel pago" : " aluguéis pagos"), false),
                kpi("Em atraso, valor atualizado", Formatos.moeda(f.emAtrasoHoje()), "com multa, juros e correção",
                        f.emAtrasoHoje().signum() > 0),
                kpi("Pagamentos com atraso", f.pagosComAtraso() + " de " + f.pagos(),
                        "pagos depois da tolerância", false)));

        List<Aluguel> recentes = f.alugueis().stream().limit(ALUGUEIS_NA_FICHA).toList();
        if (recentes.isEmpty()) {
            bloco.getChildren().add(veredito("veredito-neutro", "Nenhum aluguel gerado ainda para este inquilino."));
            return bloco;
        }
        // A faixa vai do mais antigo para o mais recente, como uma linha do tempo.
        FlowPane faixa = new FlowPane(6, 6);
        recentes.stream().sorted(Comparator.comparing(Aluguel::getVencimento)).forEach(a -> {
            Pagamento p = Pagamento.de(a, f.hoje());
            Label mes = new Label(a.getCompetencia().getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", ""));
            mes.getStyleClass().addAll("mes", estilo(p));
            mes.setTooltip(new Tooltip(a.getCompetencia().format(COMPETENCIA) + " · " + p.getDescricao()));
            faixa.getChildren().add(mes);
        });
        FlowPane legenda = new FlowPane(16, 6);
        for (Pagamento p : Pagamento.values()) {
            Region cor = new Region();
            cor.getStyleClass().addAll("mes-legenda", estilo(p));
            Label nome = new Label(p.getDescricao());
            nome.getStyleClass().add("detalhe-rotulo");
            HBox item = new HBox(6, cor, nome);
            item.setAlignment(Pos.CENTER_LEFT);
            legenda.getChildren().add(item);
        }
        bloco.getChildren().addAll(faixa, legenda, tabelaAlugueis(f, recentes));
        return bloco;
    }

    private static VBox kpi(String rotulo, String valor, String detalhe, boolean erro) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("ficha-kpi-rotulo");
        Label v = new Label(valor);
        v.getStyleClass().add("ficha-kpi-valor");
        if (erro) {
            v.getStyleClass().add("erro");
        }
        Label d = new Label(detalhe);
        d.getStyleClass().add("detalhe-rotulo");
        VBox caixa = new VBox(6, r, v, d);
        caixa.getStyleClass().add("ficha-kpi");
        return caixa;
    }

    /** A lista de aluguéis em linhas, como a tabela do protótipo. */
    private static GridPane tabelaAlugueis(Ficha f, List<Aluguel> alugueis) {
        GridPane grade = new GridPane();
        double[] larguras = {14, 14, 16, 18, 22, 16};
        for (double l : larguras) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(l);
            c.setHgrow(Priority.ALWAYS);
            grade.getColumnConstraints().add(c);
        }
        String[] cabecalho = {"Competência", "Contrato", "Vencimento", "Valor", "Situação", "Pago em"};
        for (int col = 0; col < cabecalho.length; col++) {
            grade.add(celula(rotulo(cabecalho[col]), col == 3), col, 0);
        }
        int linha = 1;
        for (Aluguel a : alugueis) {
            Pagamento p = Pagamento.de(a, f.hoje());
            Label selo = new Label(p.getDescricao());
            selo.getStyleClass().addAll("selo", switch (p) {
                case PAGO_EM_DIA -> "selo-sucesso";
                case PAGO_COM_ATRASO, EM_TOLERANCIA -> "selo-alerta";
                case EM_ATRASO -> "selo-erro";
                case A_VENCER -> "selo-info";
            });
            grade.add(celula(valor(a.getCompetencia().format(COMPETENCIA)), false), 0, linha);
            grade.add(celula(valor(a.getContrato().getNumero()), false), 1, linha);
            grade.add(celula(valor(Formatos.data(a.getVencimento())), false), 2, linha);
            grade.add(celula(valor(Formatos.moeda(a.base())), true), 3, linha);
            grade.add(celula(selo, false), 4, linha);
            grade.add(celula(valor(a.estaPago() ? Formatos.data(a.getDataPagamento()) : Detalhe.SEM_DADO), false), 5, linha);
            linha++;
        }
        return grade;
    }

    private static Label rotulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("detalhe-rotulo");
        return l;
    }

    private static Label valor(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("detalhe-valor");
        return l;
    }

    /** Uma célula da lista: o conteúdo com uma linha fina embaixo, à direita quando é valor. */
    private static HBox celula(Node conteudo, boolean aDireita) {
        HBox celula = new HBox(conteudo);
        celula.setAlignment(aDireita ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        celula.getStyleClass().add("detalhe-linha");
        celula.setMaxWidth(Double.MAX_VALUE);
        if (aDireita) {
            celula.setStyle("-fx-padding: 10 16 10 0;");
        }
        return celula;
    }

    private static String estilo(Pagamento p) {
        return "mes-" + p.name().toLowerCase().replace('_', '-');
    }

    // ---------- contatos de cobrança, [D7] BOARD-64 ----------

    /** Todas as ligações de cobrança: quando, quem fez e o que deu (ex.: prometeu pagar até 15/11). */
    private static VBox contatosDeCobranca(Ficha f) {
        VBox bloco = new VBox(8, titulo("Contatos de cobrança · " + f.contatos().size()));
        for (var contato : f.contatos()) {
            bloco.getChildren().add(RegistrarContatoController.linhaDoHistorico(contato));
        }
        return bloco;
    }

    // ---------- contratos anteriores ----------

    private static VBox contratosAnteriores(Ficha f, Contrato atual) {
        VBox bloco = new VBox(titulo("Outros contratos"));
        for (Contrato c : f.contratos()) {
            if (c == atual) {
                continue;
            }
            bloco.getChildren().add(linha(c.getNumero() + " · " + imovel(c),
                    Formatos.data(c.getDataEntregaChaves()) + " a " + Formatos.data(c.getDataTermino()) + " · "
                            + situacao(c, f)));
        }
        return bloco;
    }

    /** Botão "Editar cadastro": fecha a ficha e abre o formulário do inquilino. */
    @FXML
    private void editar() {
        fechar();
        if (aoEditar != null) {
            aoEditar.run();
        }
    }

    /** Botões "Fechar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }
}
