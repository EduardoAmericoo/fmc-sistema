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

    /** Grava o erro com data e hora no fim de data/erros.log. Se nem isso der certo, vai para o console. */
    static void registrar(Exception e) {
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
