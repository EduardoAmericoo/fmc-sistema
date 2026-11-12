package br.org.fmc.util;

import br.org.fmc.db.Database;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Envio de e-mail do sistema (usado pelo "Esqueci a senha").
 *
 * Funciona como os correios: o sistema entrega a carta e o Correio leva. Há dois tipos:
 * - SMTP: envia de verdade, pela conta de e-mail da Fundação. Configurado por variáveis de
 *   ambiente (FMC_SMTP_HOST, FMC_SMTP_PORTA, FMC_SMTP_USUARIO, FMC_SMTP_SENHA, FMC_SMTP_REMETENTE),
 *   para a senha da conta nunca ficar no código nem no GitHub.
 * - Simulado: não envia nada; salva cada e-mail como um arquivo .txt na pasta
 *   data/caixa-de-saida. Serve para testar e apresentar sem internet nem conta de e-mail.
 *   Só vale enquanto o sistema está com os dados de teste ligados.
 */
public interface Correio {

    void enviar(String para, String assunto, String texto) throws IOException;

    /** true se o e-mail não sai de verdade (fica na caixa de saída simulada). */
    default boolean simulado() {
        return false;
    }

    /**
     * O correio configurado neste computador: SMTP se FMC_SMTP_HOST estiver definido; senão,
     * o simulado enquanto os dados de teste estão ligados (FMC_DADOS_TESTE diferente de "nao");
     * senão, nenhum (null): a recuperação por e-mail fica desligada.
     */
    static Correio doSistema() {
        String host = configuracao("FMC_SMTP_HOST");
        if (host != null && !host.isBlank()) {
            return new Smtp(host, configuracao("FMC_SMTP_PORTA"), configuracao("FMC_SMTP_USUARIO"),
                    configuracao("FMC_SMTP_SENHA"), configuracao("FMC_SMTP_REMETENTE"));
        }
        if (!"nao".equalsIgnoreCase(System.getenv("FMC_DADOS_TESTE"))) {
            return new Simulado(Database.pasta().resolve("caixa-de-saida"));
        }
        return null;
    }

    /**
     * Lê uma configuração do ambiente. No Windows, se o programa foi aberto por outro que já estava
     * aberto antes da configuração ser gravada (ex.: VS Code aberto pelo GitHub Desktop), ela não
     * chega pelo ambiente; então lê direto das variáveis do usuário no Windows (HKCU\Environment).
     */
    static String configuracao(String nome) {
        String valor = System.getenv(nome);
        if ((valor == null || valor.isBlank()) && System.getProperty("os.name", "").startsWith("Windows")) {
            valor = variavelDoUsuarioNoWindows(nome);
        }
        return valor;
    }

    private static String variavelDoUsuarioNoWindows(String nome) {
        try {
            Process reg = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", nome)
                    .redirectErrorStream(true).start();
            String saida = new String(reg.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            reg.waitFor();
            for (String linha : saida.split("\\R")) {
                String[] partes = linha.trim().split("\\s{2,}|\\t", 3);
                if (partes.length == 3 && partes[0].equalsIgnoreCase(nome)) {
                    return partes[2].trim();
                }
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /** Salva cada e-mail como um arquivo de texto na pasta da caixa de saída. */
    final class Simulado implements Correio {

        private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
        private final Path pasta;

        public Simulado(Path pasta) {
            this.pasta = pasta;
        }

        public Path pasta() {
            return pasta;
        }

        @Override
        public boolean simulado() {
            return true;
        }

        @Override
        public void enviar(String para, String assunto, String texto) throws IOException {
            Files.createDirectories(pasta);
            String conteudo = "Para: " + para + "\nAssunto: " + assunto + "\nData: "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n\n" + texto + "\n";
            Files.writeString(pasta.resolve(LocalDateTime.now().format(NOME) + ".txt"), conteudo, StandardCharsets.UTF_8);
        }
    }

    /** Envia de verdade por SMTP (ex.: smtp.gmail.com, porta 587, com senha de app). */
    final class Smtp implements Correio {

        private final String host;
        private final String porta;
        private final String usuario;
        private final String senha;
        private final String remetente;

        public Smtp(String host, String porta, String usuario, String senha, String remetente) {
            this.host = host;
            this.porta = porta == null || porta.isBlank() ? "587" : porta;
            this.usuario = usuario;
            this.senha = senha;
            this.remetente = remetente == null || remetente.isBlank() ? usuario : remetente;
        }

        @Override
        public void enviar(String para, String assunto, String texto) throws IOException {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", porta);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            jakarta.mail.Session sessao = jakarta.mail.Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(usuario, senha);
                }
            });
            try {
                jakarta.mail.internet.MimeMessage mensagem = new jakarta.mail.internet.MimeMessage(sessao);
                mensagem.setFrom(new jakarta.mail.internet.InternetAddress(remetente, "Sistema FMC", "UTF-8"));
                mensagem.setRecipients(jakarta.mail.Message.RecipientType.TO,
                        jakarta.mail.internet.InternetAddress.parse(para));
                mensagem.setSubject(assunto, "UTF-8");
                mensagem.setText(texto, "UTF-8");
                jakarta.mail.Transport.send(mensagem);
            } catch (jakarta.mail.MessagingException e) {
                throw new IOException("Não foi possível enviar o e-mail: " + e.getMessage(), e);
            }
        }
    }
}
