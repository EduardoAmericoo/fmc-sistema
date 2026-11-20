package br.org.fmc.ui;

import static br.org.fmc.ui.Detalhe.coluna;
import static br.org.fmc.ui.Detalhe.linha;
import static br.org.fmc.ui.Detalhe.texto;
import static br.org.fmc.ui.Detalhe.titulo;
import static br.org.fmc.ui.Detalhe.tresColunas;
import static br.org.fmc.ui.Detalhe.veredito;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.DocumentoContrato;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FotoVistoria;
import br.org.fmc.model.Encerramento;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Prorrogacao;
import br.org.fmc.model.Reajuste;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.SituacaoContrato;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.model.TipoDocumento;
import br.org.fmc.service.RegraNegocioException;
import br.org.fmc.service.ServicoDocumentos;
import br.org.fmc.service.ServicoVistorias;
import br.org.fmc.service.Sessao;
import br.org.fmc.util.Formatos;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controla a janela "Detalhes do contrato" (ui/detalhes-contrato.fxml), BOARD-59.
 * Mostra o que já está no contrato carregado; o banco só é usado para os documentos digitalizados.
 *
 * O desenho segue a janela "Contrato" do protótipo: três colunas (Imóvel, Locatário e
 * Garantia), com o rótulo à esquerda e o valor à direita; embaixo, prazo, vigência e
 * vencimento; e a situação do contrato em caixas coloridas (verde, amarela, vermelha, cinza).
 *
 * No fim, os documentos digitalizados do contrato ("Documentos · N"), com o botão "Abrir"
 * (abre no programa padrão do computador) e "Arquivar" (não existe excluir). Os documentos
 * arquivados ficam escondidos atrás de "Mostrar arquivados", com o botão "Reativar".
 */
public class DetalhesContratoController {

    @FXML
    private StackPane veu;
    @FXML
    private Label titulo;
    @FXML
    private Label seloSituacao;
    @FXML
    private VBox corpo;
    @FXML
    private Button botaoReajustar;
    @FXML
    private Button botaoEnviarDocumento;

    private final ServicoDocumentos servicoDocumentos = new ServicoDocumentos();
    /** A seção de documentos é refeita depois de enviar, arquivar ou reativar. */
    private final VBox secaoDocumentos = new VBox(8);
    /** Resumo das vistorias com fotos (refeito quando a janela de vistorias muda algo). */
    private final VBox secaoVistorias = new VBox(8);
    private final ServicoVistorias servicoVistorias = new ServicoVistorias();
    private boolean mostrarArquivados;

    /** O contrato aberto e o que fazer quando ele muda por esta janela. */
    private Contrato contrato;
    private Runnable depoisDeMudar;

    /** Abre a janela com os detalhes do contrato escolhido. */
    public static void abrir(Contrato contrato) {
        abrir(contrato, null);
    }

    /**
     * Abre a janela com os detalhes do contrato escolhido.
     *
     * @param depoisDeMudar o que a tela faz quando algo muda pela janela (ex.: reajuste aplicado)
     */
    public static void abrir(Contrato contrato, Runnable depoisDeMudar) {
        FXMLLoader carregador = new FXMLLoader(
                DetalhesContratoController.class.getResource("/br/org/fmc/ui/detalhes-contrato.fxml"));
        try {
            Node janela = carregador.load();
            DetalhesContratoController controle = carregador.getController();
            controle.depoisDeMudar = depoisDeMudar;
            controle.preencher(contrato);
            Navegador.mostrarJanela(janela);
            janela.requestFocus();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a janela detalhes-contrato.fxml", e);
        }
    }

    @FXML
    private void initialize() {
        Permissoes.mostrarSe(botaoEnviarDocumento, Acao.ANEXAR_DOCUMENTOS);
        // Esc fecha a janela.
        veu.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                fechar();
            }
        });
    }

    private void preencher(Contrato c) {
        this.contrato = c;
        LocalDate hoje = LocalDate.now();
        SituacaoContrato situacao = SituacaoContrato.de(c, hoje);
        titulo.setText("Contrato " + c.getNumero());
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
                tresColunas(colunaImovel(c), colunaLocatario(c.getInquilino()), colunaGarantia(c)),
                tresColunas(linha("Prazo", c.getPrazo().toString()),
                        linha("Vigência", Formatos.data(c.getDataEntregaChaves()) + " a "
                                + Formatos.data(c.getDataTermino())),
                        linha("Vencimento", "Todo dia " + c.getDiaVencimento())),
                secaoSituacao(c, situacao, hoje));
        if (c.getStatus() == StatusContrato.ATIVO || !c.getReajustes().isEmpty()) {
            corpo.getChildren().add(secaoReajuste(c, hoje));
        }
        corpo.getChildren().addAll(secaoVistorias, secaoDocumentos);
        carregarVistorias();
        carregarDocumentos();
        boolean podeReajustar = c.reajustePendente(hoje) && Sessao.pode(Acao.REAJUSTAR_CONTRATO);
        botaoReajustar.setVisible(podeReajustar);
        botaoReajustar.setManaged(podeReajustar);
    }

    /**
     * Reajuste anual ([D9] BOARD-46), como no protótipo: em vermelho quando venceu e ainda não
     * foi aplicado; em cinza, a data do próximo. Embaixo, os reajustes já feitos.
     */
    private static VBox secaoReajuste(Contrato c, LocalDate hoje) {
        VBox secao = new VBox(8, titulo("Reajuste anual"));
        if (c.reajustePendente(hoje)) {
            secao.getChildren().add(veredito("veredito-erro", "Reajuste vencido desde "
                    + Formatos.data(c.proximoReajuste()) + " e ainda não aplicado. O índice é a média do INPC "
                    + "e do IGP-DI dos últimos 12 meses; o aluguel novo vale para os aluguéis ainda não pagos."));
        } else if (c.getStatus() == StatusContrato.ATIVO) {
            secao.getChildren().add(veredito("veredito-neutro",
                    "Próximo reajuste em " + Formatos.data(c.proximoReajuste()) + "."));
        }
        List<Reajuste> feitos = c.getReajustes();
        for (int i = feitos.size() - 1; i >= 0; i--) {
            Reajuste r = feitos.get(i);
            secao.getChildren().add(veredito("veredito-neutro", "✓ Reajustado em " + Formatos.data(r.getAplicadoEm())
                    + " em " + r.getPercentual().stripTrailingZeros().toPlainString().replace('.', ',') + "%: de "
                    + Formatos.moeda(r.getValorAnterior()) + " para " + Formatos.moeda(r.getValorNovo())
                    + " (aniversário de " + Formatos.data(r.getAniversario()) + ", por " + r.getFeitoPor() + ")"));
        }
        return secao;
    }

    /** Botão "Aplicar reajuste": abre a janela do percentual; depois de aplicar, fecha e atualiza a lista. */
    @FXML
    private void reajustar() {
        ReajustarContratoController.abrir(contrato, r -> {
            fechar();
            if (depoisDeMudar != null) {
                depoisDeMudar.run();
            }
        });
    }

    // ---------- as três colunas ----------

    private static VBox colunaImovel(Contrato c) {
        Imovel i = c.getImovel();
        String endereco = i.getEndereco() + (i.getBairro() == null || i.getBairro().isBlank() ? "" : " · " + i.getBairro());
        return coluna("Imóvel",
                linha("Código", i.getCodigo()),
                linha("Unidade", texto(i.getUnidade())),
                linha("Endereço", endereco),
                linha("Aluguel", Formatos.moeda(c.getValorAluguel())),
                linha("Taxa de luz", Formatos.moeda(c.getTaxaLuz())),
                linha("IPTU", Formatos.moeda(c.getValorIptu())));
    }

    private static VBox colunaLocatario(Inquilino i) {
        List<Node> linhas = new ArrayList<>(List.of(
                linha("Nome", i.getNome()),
                linha("CPF", i.getCpf().formatado()),
                linha("Telefone", texto(i.getTelefone()))));
        if (i.getConjuge() != null) {
            linhas.add(linha("Cônjuge", i.getConjuge().getNome()));
        }
        linhas.add(linha("Renda familiar", Formatos.moeda(i.rendaConsiderada())));
        return coluna("Locatário", linhas.toArray(Node[]::new));
    }

    /** A coluna da garantia (fiador ou caução); a ficha do inquilino usa a mesma. */
    static VBox colunaGarantia(Contrato c) {
        if (c.getGarantia() instanceof Caucao caucao) {
            return coluna("Garantia · caução",
                    linha("Valor", Formatos.moeda(caucao.getValor())),
                    linha("Recebida em", Formatos.data(caucao.getDataRecebimento())),
                    linha("Forma", caucao.getFormaPagamento().toString()),
                    linha("Recibo", texto(caucao.getNumeroRecibo())));
        }
        Fiador fiador = ((Fianca) c.getGarantia()).getFiador();
        List<Node> linhas = new ArrayList<>(List.of(
                linha("Nome", fiador.getNome()),
                linha("CPF", fiador.getCpf().formatado()),
                linha("Telefone", texto(fiador.getTelefone()))));
        Conjuge conjuge = fiador.getConjuge();
        if (conjuge != null) {
            linhas.add(linha("Cônjuge", conjuge.getNome()));
        }
        linhas.add(linha("Renda", Formatos.moeda(fiador.rendaConsiderada())));
        linhas.add(linha("Endereço", texto(fiador.getEndereco())));
        return coluna("Garantia · fiador", linhas.toArray(Node[]::new));
    }

    // ---------- situação do contrato ----------

    /**
     * A situação em caixas: a primeira diz o que está acontecendo com o contrato (mesma cor do
     * selo do topo); depois a regra de renda e o histórico (prorrogações e encerramento).
     */
    private static VBox secaoSituacao(Contrato c, SituacaoContrato situacao, LocalDate hoje) {
        long dias = ChronoUnit.DAYS.between(hoje, c.getDataTermino());
        VBox secao = new VBox(8, titulo("Situação do contrato"), switch (situacao) {
            case ATIVO -> veredito("veredito-ok", "Ativo até " + Formatos.data(c.getDataTermino())
                    + ". Gera aluguel todo mês, com vencimento no dia " + c.getDiaVencimento() + ".");
            case VENCENDO -> veredito("veredito-alerta", "Vence em " + Formatos.data(c.getDataTermino())
                    + (dias == 0 ? ", hoje." : ", daqui a " + dias + (dias == 1 ? " dia." : " dias."))
                    + " Combine com o inquilino se vai prorrogar ou sair.");
            case VENCIDO -> veredito("veredito-erro", "Venceu em " + Formatos.data(c.getDataTermino())
                    + " e não gera aluguel até ser prorrogado ou encerrado.");
            case ENCERRADO -> veredito("veredito-neutro", encerrado(c.getEncerramento()));
        });

        BigDecimal minimo = c.getValorAluguel().multiply(BigDecimal.valueOf(RegrasNegocio.MULTIPLO_RENDA));
        boolean rendaOk = c.atendeRegraRenda();
        secao.getChildren().add(veredito(rendaOk ? "veredito-ok" : "veredito-erro",
                "Renda familiar " + Formatos.moeda(c.getInquilino().rendaConsiderada()) + (rendaOk ? " ≥ " : " < ")
                        + RegrasNegocio.MULTIPLO_RENDA + "× o aluguel (" + Formatos.moeda(minimo) + ")"));

        if (c.getProrrogacoes().isEmpty()) {
            secao.getChildren().add(veredito("veredito-neutro", "✓ Contrato original, sem prorrogações"));
        }
        for (Prorrogacao p : c.getProrrogacoes()) {
            secao.getChildren().add(veredito("veredito-neutro", "✓ Prorrogado em " + Formatos.data(p.getData())
                    + " por " + p.getPrazo() + ": o término passou de " + Formatos.data(p.getTerminoAnterior())
                    + " para " + Formatos.data(p.getNovoTermino())));
        }
        Encerramento e = c.getEncerramento();
        if (e != null && e.getObservacao() != null && !e.getObservacao().isBlank()) {
            secao.getChildren().add(veredito("veredito-neutro", "Observação do encerramento: " + e.getObservacao()));
        }
        return secao;
    }

    private static String encerrado(Encerramento e) {
        if (e == null) {
            return "Contrato encerrado.";
        }
        return "Encerrado: chaves devolvidas em " + Formatos.data(e.getDataSaida()) + " · " + e.getMotivo()
                + ". Não gera mais aluguel; os aluguéis em aberto continuam sendo cobrados.";
    }

    // ---------- documentos digitalizados ----------

    private void carregarDocumentos() {
        secaoDocumentos.getChildren().clear();
        List<DocumentoContrato> todos;
        try {
            todos = servicoDocumentos.listar(contrato.getId());
        } catch (SQLException | RuntimeException e) {
            secaoDocumentos.getChildren().addAll(titulo("Documentos"),
                    veredito("veredito-erro", "Não foi possível carregar os documentos: " + e.getMessage()));
            return;
        }
        List<DocumentoContrato> ativos = todos.stream().filter(d -> !d.arquivado()).toList();
        List<DocumentoContrato> arquivados = todos.stream().filter(DocumentoContrato::arquivado).toList();

        secaoDocumentos.getChildren().add(titulo("Documentos · " + ativos.size()));

        // Como no protótipo: diz se a documentação obrigatória já está toda digitalizada.
        List<TipoDocumento> faltam = ServicoDocumentos.faltando(contrato, todos);
        secaoDocumentos.getChildren().add(faltam.isEmpty()
                ? veredito("veredito-ok", "Documentação obrigatória completa e digitalizada.")
                : veredito("veredito-alerta", (faltam.size() == 1 ? "1 documento obrigatório ainda está"
                        : faltam.size() + " documentos obrigatórios ainda estão") + " só no papel: "
                        + String.join(", ", faltam.stream().map(TipoDocumento::getDescricao).toList()) + "."));

        // Agrupados pela etapa da locação (Locação, Entrada, Durante a locação, Saída, Outros).
        for (TipoDocumento.Etapa etapa : TipoDocumento.Etapa.values()) {
            List<DocumentoContrato> daEtapa = ativos.stream().filter(d -> d.tipo().getEtapa() == etapa)
                    .sorted(java.util.Comparator.comparing(DocumentoContrato::data)).toList();
            if (daEtapa.isEmpty()) {
                continue;
            }
            Label grupo = new Label(etapa.getDescricao().toUpperCase(java.util.Locale.forLanguageTag("pt-BR")));
            grupo.getStyleClass().add("documentos-etapa");
            secaoDocumentos.getChildren().add(grupo);
            for (DocumentoContrato d : daEtapa) {
                secaoDocumentos.getChildren().add(linhaDocumento(d));
            }
        }
        if (!arquivados.isEmpty()) {
            Button alternar = new Button((mostrarArquivados ? "Esconder arquivados" : "Mostrar arquivados")
                    + " (" + arquivados.size() + ")");
            alternar.getStyleClass().addAll("botao-secundario", "botao-pequeno");
            alternar.setOnAction(e -> {
                mostrarArquivados = !mostrarArquivados;
                carregarDocumentos();
            });
            secaoDocumentos.getChildren().add(new HBox(alternar));
            if (mostrarArquivados) {
                for (DocumentoContrato d : arquivados) {
                    secaoDocumentos.getChildren().add(linhaDocumento(d));
                }
            }
        }
    }

    /** Uma linha: ícone, tipo e data, nome do arquivo e quem enviou, e os botões. */
    private HBox linhaDocumento(DocumentoContrato d) {
        boolean pdf = d.nomeOriginal().toLowerCase().endsWith(".pdf");
        FontIcon icone = new FontIcon(pdf ? "fth-file-text" : "fth-image");

        Label tipo = new Label(d.tipo() + " · " + Formatos.data(d.data())
                + (d.arquivado() ? " · arquivado em " + Formatos.data(d.arquivadoEm().toLocalDate()) : ""));
        tipo.getStyleClass().add("detalhe-valor");
        tipo.setWrapText(true);
        tipo.setMinHeight(Region.USE_PREF_SIZE);
        Label arquivo = new Label(d.nomeOriginal() + " · " + ServicoDocumentos.megas(d.tamanho())
                + " · enviado por " + d.enviadoPor() + " em " + Formatos.data(d.enviadoEm().toLocalDate()));
        arquivo.getStyleClass().add("detalhe-rotulo");
        arquivo.setWrapText(true);
        arquivo.setMinHeight(Region.USE_PREF_SIZE);
        VBox textos = new VBox(2, tipo, arquivo);
        if (d.observacao() != null) {
            Label obs = new Label(d.observacao());
            obs.getStyleClass().add("detalhe-rotulo");
            obs.setWrapText(true);
            obs.setMinHeight(Region.USE_PREF_SIZE);
            textos.getChildren().add(obs);
        }
        textos.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Button abrir = botaoPequeno("Abrir", "fth-external-link");
        abrir.setOnAction(e -> abrirDocumento(d));
        HBox linha = new HBox(12, icone, textos, abrir);
        if (Permissoes.pode(Acao.ANEXAR_DOCUMENTOS)) {
            Button outro = d.arquivado() ? botaoPequeno("Reativar", "fth-rotate-ccw")
                    : botaoPequeno("Arquivar", "fth-archive");
            outro.setOnAction(e -> {
                if (d.arquivado()) {
                    reativarDocumento(d);
                } else {
                    arquivarDocumento(d);
                }
            });
            linha.getChildren().add(outro);
        }
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("detalhe-linha");
        if (d.arquivado()) {
            linha.setOpacity(0.6);
        }
        return linha;
    }

    private static Button botaoPequeno(String texto, String icone) {
        Button botao = new Button(texto, new FontIcon(icone));
        botao.getStyleClass().addAll("botao-secundario", "botao-pequeno");
        botao.setMinWidth(Region.USE_PREF_SIZE);
        return botao;
    }

    /** Botão "Enviar documento" do rodapé. */
    @FXML
    private void enviarDocumento() {
        EnviarDocumentoController.abrir(contrato, servicoDocumentos, doc -> carregarDocumentos());
    }

    /** Botão "Vistorias (fotos)": contrato encerrado abre na saída; os outros, na entrada. */
    @FXML
    private void abrirVistorias() {
        EtapaVistoria etapa = contrato.getStatus() == StatusContrato.ENCERRADO ? EtapaVistoria.SAIDA
                : EtapaVistoria.ENTRADA;
        VistoriasController.abrir(contrato, etapa, this::carregarVistorias);
    }

    // ---------- vistorias com fotos ----------

    /** Resumo das vistorias: quantas fotos na entrada e na saída. */
    private void carregarVistorias() {
        secaoVistorias.getChildren().clear();
        secaoVistorias.getChildren().add(titulo("Vistorias com fotos"));
        try {
            List<FotoVistoria> fotos = servicoVistorias.listar(contrato.getId());
            long entrada = fotos.stream().filter(f -> f.etapa() == EtapaVistoria.ENTRADA && !f.arquivada()).count();
            long saida = fotos.stream().filter(f -> f.etapa() == EtapaVistoria.SAIDA && !f.arquivada()).count();
            secaoVistorias.getChildren().add(veredito(entrada > 0 ? "veredito-ok" : "veredito-neutro",
                    "Entrada: " + quantasFotos(entrada) + " · Saída: " + quantasFotos(saida)
                            + ". Veja, compare e gere o laudo em \"Vistorias (fotos)\"."));
        } catch (SQLException | RegraNegocioException e) {
            secaoVistorias.getChildren().add(veredito("veredito-erro", "Não foi possível carregar as vistorias: "
                    + e.getMessage()));
        }
    }

    private static String quantasFotos(long n) {
        return n == 0 ? "nenhuma foto" : n + (n == 1 ? " foto" : " fotos");
    }

    /**
     * Abre a cópia guardada no sistema no programa padrão (leitor de PDF, visualizador de fotos).
     * Roda fora da tela para ela não travar enquanto o programa abre.
     */
    private void abrirDocumento(DocumentoContrato d) {
        Path caminho;
        try {
            caminho = servicoDocumentos.arquivoParaAbrir(d.id());
        } catch (RegraNegocioException e) {
            avisar(String.join(" ", e.getErros()));
            return;
        } catch (SQLException e) {
            avisar("Erro no banco de dados: " + e.getMessage());
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            avisar("Este computador não permite abrir arquivos pelo sistema. O arquivo está em: " + caminho);
            return;
        }
        Thread abrir = new Thread(() -> {
            try {
                Desktop.getDesktop().open(caminho.toFile());
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> avisar("Não foi possível abrir o arquivo (" + e.getMessage()
                        + "). Confira se há um programa para abrir PDF ou fotos. O arquivo está em: " + caminho));
            }
        }, "abrir-documento");
        abrir.setDaemon(true);
        abrir.start();
    }

    private void arquivarDocumento(DocumentoContrato d) {
        if (!confirmar("Arquivar documento?", "\"" + d.tipo() + "\" (" + d.nomeOriginal() + ") sai da lista de "
                + "documentos do contrato. O arquivo continua guardado e pode ser reativado depois "
                + "em \"Mostrar arquivados\".", "Arquivar")) {
            return;
        }
        try {
            servicoDocumentos.arquivar(d.id());
            carregarDocumentos();
        } catch (RegraNegocioException e) {
            avisar(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            avisar("Erro no banco de dados: " + e.getMessage());
        }
    }

    private void reativarDocumento(DocumentoContrato d) {
        try {
            servicoDocumentos.reativar(d.id());
            carregarDocumentos();
        } catch (RegraNegocioException e) {
            avisar(String.join(" ", e.getErros()));
        } catch (SQLException e) {
            avisar("Erro no banco de dados: " + e.getMessage());
        }
    }

    private boolean confirmar(String titulo, String texto, String botao) {
        ButtonType sim = new ButtonType(botao, ButtonBar.ButtonData.OK_DONE);
        ButtonType nao = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, texto, sim, nao);
        alerta.initOwner(veu.getScene().getWindow());
        alerta.setTitle("Documentos do contrato");
        alerta.setHeaderText(titulo);
        return alerta.showAndWait().orElse(nao) == sim;
    }

    private void avisar(String texto) {
        Alert alerta = new Alert(Alert.AlertType.WARNING, texto);
        alerta.initOwner(veu.getScene().getWindow());
        alerta.setTitle("Documentos do contrato");
        alerta.setHeaderText("Não foi possível concluir");
        alerta.show();
    }

    /** Botões "Fechar" e X (e a tecla Esc). */
    @FXML
    private void fechar() {
        Navegador.fecharJanela(veu);
    }
}
