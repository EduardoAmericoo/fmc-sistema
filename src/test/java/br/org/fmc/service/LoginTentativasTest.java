package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Login com aviso por campo e limite de tentativas (5 senhas erradas = 2 minutos de pausa). */
class LoginTentativasTest {

    private static final String EMAIL = ServicoAcesso.EMAIL_ADMIN_INICIAL;
    private static final String SENHA = ServicoAcesso.SENHA_ADMIN_INICIAL;

    /** Relógio que o teste adianta quando quer, para não esperar 2 minutos de verdade. */
    private static final class RelogioDeTeste extends Clock {
        private Instant agora = Instant.parse("2026-10-25T10:00:00Z");

        void adiantar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Maceio");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @TempDir
    Path pasta;

    private final ServicoAcesso servico = new ServicoAcesso();
    private final RelogioDeTeste relogio = new RelogioDeTeste();

    @BeforeEach
    void bancoComAdministrador() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        servico.garantirAdministradorInicial();
        servico.usarRelogio(relogio);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private FalhaLogin errar() {
        return assertThrows(FalhaLogin.class, () -> servico.login(EMAIL, "errada"));
    }

    @Test
    void avisaQuandoFaltamPoucasTentativas() {
        assertEquals("Senha incorreta.", errar().mensagem(FalhaLogin.Campo.SENHA));
        assertEquals("Senha incorreta.", errar().mensagem(FalhaLogin.Campo.SENHA));
        assertEquals("Senha incorreta. Mais 2 tentativas erradas e o acesso fica em pausa por 2 minutos.",
                errar().mensagem(FalhaLogin.Campo.SENHA));
        assertEquals("Senha incorreta. Mais 1 tentativa errada e o acesso fica em pausa por 2 minutos.",
                errar().mensagem(FalhaLogin.Campo.SENHA));
    }

    @Test
    void quintaSenhaErradaPausaOAcessoMesmoComASenhaCerta() throws Exception {
        for (int i = 0; i < 4; i++) {
            errar();
        }
        assertEquals("Senha errada 5 vezes seguidas. Por segurança, o acesso fica em pausa por 2 minutos.",
                errar().mensagem(FalhaLogin.Campo.GERAL));

        relogio.adiantar(Duration.ofSeconds(30));
        FalhaLogin pausa = assertThrows(FalhaLogin.class, () -> servico.login(EMAIL, SENHA));
        assertEquals("Acesso em pausa por segurança, depois de 5 senhas erradas. Tente de novo em 2 minutos.",
                pausa.mensagem(FalhaLogin.Campo.GERAL));

        relogio.adiantar(Duration.ofMinutes(2));
        servico.login(EMAIL, SENHA);
        assertTrue(Sessao.estaLogado());
    }

    @Test
    void loginCertoZeraAContagem() throws Exception {
        for (int i = 0; i < 4; i++) {
            errar();
        }
        servico.login(EMAIL, SENHA);
        servico.sair();

        assertEquals("Senha incorreta.", errar().mensagem(FalhaLogin.Campo.SENHA)); // voltou a ser o 1º erro
    }

    @Test
    void explicaOQueEstaErradoNoEmail() {
        assertEquals("Informe o e-mail.", ServicoAcesso.problemaEmail("  "));
        assertEquals("Falta o @ no e-mail. Exemplo: nome@exemplo.org", ServicoAcesso.problemaEmail("secretaria"));
        assertEquals("Falta o nome antes do @. Exemplo: nome@exemplo.org", ServicoAcesso.problemaEmail("@fmc.org"));
        assertEquals("E-mail incompleto: falta o domínio depois do @, como exemplo.org.",
                ServicoAcesso.problemaEmail("secretaria@exemplo"));
        assertEquals("O e-mail não pode ter espaços.", ServicoAcesso.problemaEmail("ana maria@fmc.org"));
        assertNull(ServicoAcesso.problemaEmail(" admin@fmc.org "));
    }
}
