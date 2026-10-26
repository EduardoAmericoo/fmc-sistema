package br.org.fmc.ui;

import br.org.fmc.model.Tela;
import br.org.fmc.service.Sessao;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Troca a tela mostrada no centro da janela principal.
 *
 * Como criar a tela de uma história: crie o arquivo
 * resources/br/org/fmc/ui/&lt;nome&gt;.fxml com o nome que está no enum
 * model/Tela (ex.: imoveis.fxml) e o controller dela neste pacote. Pronto: o
 * botão do menu já abre a sua tela. Enquanto o arquivo não existe, aparece
 * "Em construção". Quem não tem permissão para a tela vê um aviso no lugar dela.
 */
public final class Navegador {

    private static final String CSS = "/br/org/fmc/css/tema.css";
    /** Tamanho quando a pessoa sai do tela cheia, e o menor tamanho permitido (as telas não cabem em menos). */
    private static final double LARGURA = 1280;
    private static final double ALTURA = 800;
    private static final double LARGURA_MINIMA = 1024;
    private static final double ALTURA_MINIMA = 680;

    /** Ícone da janela e da barra de tarefas, em vários tamanhos (o Windows escolhe o melhor). */
    private static final int[] TAMANHOS_ICONE = {16, 24, 32, 48, 64, 128, 256};

    private static Stage palco;
    private static BorderPane janela;

    /**
     * Raiz da cena: a tela por baixo e, por cima, a janela aberta (se houver).
     * A cena é uma só a vida toda; trocar de tela troca só o que está dentro dela.
     * Assim a janela não "reabre" nem volta ao tamanho pequeno a cada troca de tela.
     */
    private static StackPane camadas;

    private Navegador() {
    }

    /** Chamado uma vez pelo App, com a janela do sistema: monta a cena, o ícone e abre maximizada. */
    public static void iniciar(Stage stage) {
        palco = stage;
        camadas = new StackPane();
        Scene cena = new Scene(camadas, LARGURA, ALTURA);
        cena.getStylesheets().add(Navegador.class.getResource(CSS).toExternalForm());
        stage.setScene(cena);
        stage.setMinWidth(LARGURA_MINIMA);
        stage.setMinHeight(ALTURA_MINIMA);
        for (int tamanho : TAMANHOS_ICONE) {
            URL icone = Navegador.class.getResource("/br/org/fmc/icones/fmc-" + tamanho + ".png");
            if (icone != null) {
                stage.getIcons().add(new Image(icone.toExternalForm()));
            }
        }
        stage.setMaximized(true);
    }

    /** Mostra a tela de login (ao abrir o sistema e ao sair). */
    public static void mostrarLogin() {
        trocarCena("login");
    }

    /** A troca de senha aberta agora é a obrigatória do 1º acesso? (lida pelo TrocarSenhaController) */
    private static boolean trocaObrigatoria;

    /**
     * Mostra a tela de troca de senha (BOARD-62).
     * Obrigatória: logo depois do login com senha provisória; a pessoa só sai dela trocando a senha ou saindo.
     * Não obrigatória: quando a pessoa clica em "Trocar minha senha"; dá para cancelar e voltar.
     */
    public static void mostrarTrocaDeSenha(boolean obrigatoria) {
        trocaObrigatoria = obrigatoria;
        trocarCena("trocar-senha");
    }

    static boolean trocaObrigatoria() {
        return trocaObrigatoria;
    }

    /** Mostra o sistema (menu + telas), depois do login. */
    public static void mostrarSistema() {
        trocarCena("principal");
    }

    private static void trocarCena(String fxml) {
        if (palco == null) {
            throw new IllegalStateException("Chame Navegador.iniciar(stage) antes.");
        }
        Parent raiz = (Parent) carregarFxml(Navegador.class.getResource("/br/org/fmc/ui/" + fxml + ".fxml"), fxml);
        camadas.getChildren().setAll(raiz); // troca a tela (e fecha qualquer janela que estivesse aberta)
    }

    /**
     * Mostra uma janela (formulário sobre um véu escuro) por cima da tela inteira,
     * inclusive do menu lateral. Use fecharJanela() com o mesmo componente para tirar.
     */
    public static void mostrarJanela(Node veu) {
        if (!camadas.getChildren().contains(veu)) {
            camadas.getChildren().add(veu);
        }
        veu.setVisible(true);
        veu.setManaged(true);
    }

    public static void fecharJanela(Node veu) {
        camadas.getChildren().remove(veu);
    }

    /** Chamado uma vez pela tela principal, para dizer onde as telas aparecem. */
    static void configurar(BorderPane janelaPrincipal) {
        janela = janelaPrincipal;
    }

    /** Abre a tela pelo nome (ex.: "imoveis"). */
    public static void abrir(String tela) {
        if (janela == null) {
            throw new IllegalStateException("A janela principal ainda não foi aberta.");
        }
        janela.setCenter(carregar(tela));
    }

    private static Node carregar(String arquivo) {
        Optional<Tela> tela = Tela.porArquivo(arquivo);
        String titulo = tela.map(Tela::getTitulo).orElse(arquivo);
        // Segunda barreira: mesmo que alguém chame abrir() direto, a tela proibida não abre.
        if (tela.isPresent() && !Sessao.podeAbrir(tela.get())) {
            return aviso(titulo, "O seu perfil não tem acesso a esta tela.");
        }
        URL fxml = Navegador.class.getResource("/br/org/fmc/ui/" + arquivo + ".fxml");
        if (fxml == null) {
            return aviso(titulo, "Tela em construção.");
        }
        return carregarFxml(fxml, arquivo);
    }

    private static Node carregarFxml(URL fxml, String nome) {
        if (fxml == null) {
            throw new IllegalStateException("Arquivo " + nome + ".fxml não encontrado.");
        }
        try {
            return FXMLLoader.load(fxml);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a tela " + nome + ".fxml", e);
        }
    }

    private static Node aviso(String titulo, String texto) {
        Label nome = new Label(titulo);
        nome.getStyleClass().add("titulo");
        Label aviso = new Label(texto);
        aviso.getStyleClass().add("texto");
        VBox caixa = new VBox(12, nome, aviso);
        caixa.getStyleClass().add("conteudo");
        return caixa;
    }
}
