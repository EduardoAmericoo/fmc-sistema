package br.org.fmc;

import br.org.fmc.db.Database;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.ServicoDadosTeste;
import br.org.fmc.ui.Navegador;
import br.org.fmc.ui.Tema;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Ponto de entrada do sistema: prepara o banco, garante que existe um
 * Administrador e abre a tela de login.
 *
 * Com a variável de ambiente FMC_DADOS_TESTE=recarregar, o sistema apaga os dados
 * e carrega os dados de teste antes de abrir (BOARD-52). No VS Code é a
 * configuração "Rodar com dados de teste"; ver README, seção "Dados de teste".
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

        if ("recarregar".equalsIgnoreCase(System.getenv("FMC_DADOS_TESTE"))) {
            recarregarDadosDeTeste(stage);
        }
    }

    /** Apaga os dados e carrega os de teste, avisando o resultado numa janelinha. */
    private void recarregarDadosDeTeste(Stage stage) {
        Alert aviso;
        try {
            ServicoDadosTeste.Resumo resumo = new ServicoDadosTeste().recarregarDoZero();
            aviso = new Alert(Alert.AlertType.INFORMATION, "Carregados: " + resumo
                    + "\n\nUsuários de teste: secretaria@exemplo.org, financeiro@exemplo.org e aprendiz@exemplo.org"
                    + ", senha " + ServicoDadosTeste.SENHA_TESTE + ".");
            aviso.setHeaderText("Dados de teste recarregados do zero");
        } catch (Exception e) {
            aviso = new Alert(Alert.AlertType.ERROR, e.getMessage());
            aviso.setHeaderText("Não foi possível carregar os dados de teste");
        }
        aviso.initOwner(stage);
        aviso.setTitle("Dados de teste");
        aviso.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
