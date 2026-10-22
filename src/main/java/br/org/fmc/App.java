package br.org.fmc;

import br.org.fmc.db.Database;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.ui.Navegador;
import br.org.fmc.ui.Tema;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Ponto de entrada do sistema: prepara o banco, garante que existe um
 * Administrador e abre a tela de login.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        Database.inicializar();
        new ServicoAcesso().garantirAdministradorInicial();

        Tema.aplicar();
        stage.setTitle("Fundação Manuel Cruz");
        Navegador.iniciar(stage);
        Navegador.mostrarLogin();
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
