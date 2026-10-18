package br.org.fmc.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Troca a tela mostrada no centro da janela principal.
 *
 * Como criar a tela de uma história: crie o arquivo
 * resources/br/org/fmc/ui/&lt;nome&gt;.fxml com o nome da tabela abaixo
 * (ex.: imoveis.fxml) e o controller dela neste pacote. Pronto: o botão do
 * menu já abre a sua tela. Enquanto o arquivo não existe, aparece "Em construção".
 */
public final class Navegador {

    /** Nome do arquivo .fxml → título da tela. */
    public static final Map<String, String> TELAS = Map.of(
            "painel", "Painel",
            "alugueis", "Aluguéis",
            "imoveis", "Imóveis",
            "inquilinos", "Inquilinos",
            "contratos", "Contratos",
            "usuarios", "Usuários");

    private static BorderPane janela;

    private Navegador() {
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

    private static Node carregar(String tela) {
        URL fxml = Navegador.class.getResource("/br/org/fmc/ui/" + tela + ".fxml");
        if (fxml == null) {
            return emConstrucao(TELAS.getOrDefault(tela, tela));
        }
        try {
            return FXMLLoader.load(fxml);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir a tela " + tela + ".fxml", e);
        }
    }

    private static Node emConstrucao(String titulo) {
        Label nome = new Label(titulo);
        nome.getStyleClass().add("titulo");
        Label aviso = new Label("Tela em construção.");
        aviso.getStyleClass().add("texto");
        VBox caixa = new VBox(12, nome, aviso);
        caixa.getStyleClass().add("conteudo");
        return caixa;
    }
}
