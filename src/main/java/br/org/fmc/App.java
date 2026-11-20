package br.org.fmc;

import br.org.fmc.db.Database;
import br.org.fmc.service.ServicoAcesso;
import br.org.fmc.service.ServicoCopiaSeguranca;
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
        // Se o sistema não foi fechado direito ontem (queda de energia, computador desligado),
        // a cópia do dia ainda não existe: faz agora, antes de qualquer mudança.
        try {
            new ServicoCopiaSeguranca().copiaAutomaticaSeFaltar(java.time.LocalDate.now());
        } catch (Exception e) {
            System.err.println("Não foi possível fazer a cópia de segurança ao abrir: " + e.getMessage());
        }
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
     * Ao fechar o sistema, faz a cópia de segurança do dia (data/copias), sem perguntar nada.
     * Se der errado, o sistema fecha do mesmo jeito: a cópia é uma proteção extra.
     */
    @Override
    public void stop() {
        try {
            new ServicoCopiaSeguranca().copiaAutomatica(java.time.LocalDate.now());
        } catch (Exception e) {
            System.err.println("Não foi possível fazer a cópia de segurança automática: " + e.getMessage());
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
