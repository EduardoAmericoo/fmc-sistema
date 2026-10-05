package br.org.fmc;

import br.org.fmc.db.Database;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ponto de entrada do sistema: prepara o banco e abre a janela principal.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Database.inicializar();

        Parent raiz = FXMLLoader.load(getClass().getResource("ui/principal.fxml"));
        Scene cena = new Scene(raiz, 1100, 700);
        cena.getStylesheets().add(getClass().getResource("css/tema.css").toExternalForm());

        stage.setTitle("Fundação Manuel Cruz");
        stage.setScene(cena);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
