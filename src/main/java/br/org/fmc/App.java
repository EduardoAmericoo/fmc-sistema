package br.org.fmc;

import br.org.fmc.db.Database;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.ServicoDadosTeste;
import br.org.fmc.ui.Navegador;
import br.org.fmc.ui.Tema;
import java.util.Optional;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Ponto de entrada do sistema: prepara o banco, garante que existe um
 * Administrador e abre a tela de login.
 *
 * Dados de teste (BOARD-52), pela variável de ambiente FMC_DADOS_TESTE:
 * - não definida: se o banco estiver vazio (primeira vez), carrega os dados de teste sozinho;
 * - "recarregar": apaga tudo e carrega os dados de teste do zero (configuração
 *   "Rodar com dados de teste" do VS Code);
 * - "nao": nunca carrega dados de teste (usar na versão entregue à Fundação).
 * Ver README, seção "Dados de teste".
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

        String opcao = System.getenv("FMC_DADOS_TESTE");
        if ("recarregar".equalsIgnoreCase(opcao)) {
            carregarDadosDeTeste(stage, true);
        } else if (!"nao".equalsIgnoreCase(opcao)) {
            carregarDadosDeTeste(stage, false);
        }
    }

    /**
     * Carrega os dados de teste e avisa o resultado numa janelinha.
     * Sem "doZero", só carrega se o banco estiver vazio (e aí não mostra nada se já tinha dados).
     */
    private void carregarDadosDeTeste(Stage stage, boolean doZero) {
        Alert aviso;
        try {
            ServicoDadosTeste dados = new ServicoDadosTeste();
            Optional<ServicoDadosTeste.Resumo> resumo = doZero
                    ? Optional.of(dados.recarregarDoZero())
                    : dados.carregarSeBancoVazio();
            if (resumo.isEmpty()) {
                return; // o banco já tinha dados: abre normalmente
            }
            aviso = new Alert(Alert.AlertType.INFORMATION, "Carregados: " + resumo.get()
                    + "\n\n" + ServicoDadosTeste.acessos());
            aviso.setHeaderText(doZero ? "Dados de teste recarregados do zero"
                    : "Banco vazio: dados de teste carregados");
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
