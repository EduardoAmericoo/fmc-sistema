package br.org.fmc.ui;

import br.org.fmc.db.Database;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Erro técnico (banco de dados, disco) na tela: a pessoa vê uma frase simples, e o detalhe
 * técnico vai para data/erros.log, para quem der suporte. Antes a tela mostrava coisas como
 * "Erro no banco de dados: [SQLITE_BUSY] ...", que não ajudam quem usa e expõem detalhes
 * do sistema (auditoria de segurança, item 11).
 */
public final class Falha {

    private Falha() {
    }

    /** Ex.: "Não foi possível acessar os dados agora. Tente de novo; ..." */
    public static String doBanco(Exception e) {
        registrar(e);
        return "Não foi possível acessar os dados agora. Tente de novo; se continuar, feche e abra o sistema. "
                + "(O detalhe ficou em data/erros.log.)";
    }

    /**
     * Rede de proteção para erro que ninguém previu (um defeito do sistema), ligada uma vez pelo App.
     * Sem ela, no programa instalado (que não tem console) o clique simplesmente "não fazia nada"
     * e o erro se perdia. Agora a pessoa vê um aviso simples e o detalhe vai para data/erros.log.
     */
    public static void ligarRedeDeProtecao() {
        Thread.UncaughtExceptionHandler rede = (thread, e) -> {
            registrar(e);
            if (Platform.isFxApplicationThread()) {
                avisarInesperado();
            } else {
                Platform.runLater(Falha::avisarInesperado);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(rede);
        if (Platform.isFxApplicationThread()) {
            Thread.currentThread().setUncaughtExceptionHandler(rede);
        }
    }

    /** Evita uma pilha de avisos iguais se o mesmo erro se repetir em seguida. */
    private static boolean avisoAberto;

    private static void avisarInesperado() {
        if (avisoAberto) {
            return;
        }
        avisoAberto = true;
        try {
            Alert aviso = new Alert(Alert.AlertType.ERROR,
                    "Algo deu errado nesta ação e ela pode não ter sido concluída. Confira se o que você fez "
                            + "ficou gravado e tente de novo. Se continuar, avise o suporte: o detalhe ficou em "
                            + "data/erros.log.");
            aviso.setTitle("Fundação Manuel Cruz");
            aviso.setHeaderText("Não foi possível concluir");
            aviso.showAndWait();
        } catch (Exception ignorado) {
            // Se nem o aviso abrir, o erro já está no erros.log.
        } finally {
            avisoAberto = false;
        }
    }

    /** Grava o erro com data e hora no fim de data/erros.log. Se nem isso der certo, vai para o console. */
    static void registrar(Throwable e) {
        StringWriter pilha = new StringWriter();
        e.printStackTrace(new PrintWriter(pilha));
        String texto = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + System.lineSeparator() + pilha
                + System.lineSeparator();
        System.err.print(texto);
        try {
            Path log = Database.pasta().resolve("erros.log");
            Files.createDirectories(log.getParent());
            Files.writeString(log, texto, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignorado) {
            // O aviso na tela continua: o registro é só uma ajuda para o suporte.
        }
    }
}
