package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Perfil;
import br.org.fmc.util.Correio;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Esqueci a senha · código por e-mail, com as travas de segurança. */
class RecuperacaoSenhaTest {

    private static final String EMAIL = "ana@exemplo.org";
    private static final String SENHA_NOVA = "NovaSenha1";
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 11, 10, 0);

    @TempDir
    Path pasta;

    /** Correio de mentira: guarda os e-mails em vez de enviar. */
    private final List<String[]> enviados = new ArrayList<>();
    private final Correio correio = (para, assunto, texto) -> enviados.add(new String[] {para, assunto, texto});
    private final ServicoRecuperacaoSenha servico = new ServicoRecuperacaoSenha(correio);
    private long anaId;

    @BeforeEach
    void anaCadastrada() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        anaId = acesso.criarUsuario("Ana Souza", EMAIL, Perfil.SECRETARIA).usuario().getId();
        Sessao.encerrar(); // quem esqueceu a senha não está logado
        agoraE(AGORA);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void agoraE(LocalDateTime momento) {
        servico.usarRelogio(Clock.fixed(momento.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    /** O código de 6 números do último e-mail enviado. */
    private String codigoDoEmail() {
        Matcher m = Pattern.compile("Seu código: (\\d{6})").matcher(enviados.get(enviados.size() - 1)[2]);
        assertTrue(m.find(), "o e-mail deveria trazer o código");
        return m.group(1);
    }

    @Test
    void comOCodigoDoEmailAPessoaCriaASenhaNovaEEntra() throws Exception {
        assertEquals(ServicoRecuperacaoSenha.MSG_ENVIADO, servico.pedirCodigo(" Ana@Exemplo.org "));
        assertEquals(EMAIL, enviados.get(0)[0]);

        servico.redefinir(EMAIL, codigoDoEmail(), SENHA_NOVA, SENHA_NOVA);

        assertDoesNotThrow(() -> new ServicoAcesso().login(EMAIL, SENHA_NOVA));
        assertTrue(enviados.get(1)[1].contains("Sua senha foi trocada"));
    }

    @Test
    void codigoSoServeUmaVez() throws Exception {
        servico.pedirCodigo(EMAIL);
        String codigo = codigoDoEmail();
        servico.redefinir(EMAIL, codigo, SENHA_NOVA, SENHA_NOVA);

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.redefinir(EMAIL, codigo, "OutraSenha2", "OutraSenha2"));
        assertEquals(ServicoRecuperacaoSenha.MSG_INVALIDO, e.getErros().get(0));
    }

    @Test
    void emailQueNaoExisteRecebeAMesmaRespostaENenhumEmailSai() throws Exception {
        assertEquals(ServicoRecuperacaoSenha.MSG_ENVIADO, servico.pedirCodigo("ninguem@exemplo.org"));
        assertTrue(enviados.isEmpty());
    }

    @Test
    void codigoVenceEm15Minutos() throws Exception {
        servico.pedirCodigo(EMAIL);
        String codigo = codigoDoEmail();

        agoraE(AGORA.plusMinutes(16));
        assertThrows(RegraNegocioException.class, () -> servico.redefinir(EMAIL, codigo, SENHA_NOVA, SENHA_NOVA));
    }

    @Test
    void cincoCodigosErradosCancelamOPedido() throws Exception {
        servico.pedirCodigo(EMAIL);
        String certo = codigoDoEmail();
        String errado = certo.equals("000000") ? "111111" : "000000";

        RegraNegocioException primeiro = assertThrows(RegraNegocioException.class,
                () -> servico.redefinir(EMAIL, errado, SENHA_NOVA, SENHA_NOVA));
        assertEquals("Código incorreto. Restam 4 tentativas.", primeiro.getErros().get(0));
        for (int i = 0; i < 4; i++) {
            assertThrows(RegraNegocioException.class, () -> servico.redefinir(EMAIL, errado, SENHA_NOVA, SENHA_NOVA));
        }
        // nem o código certo vale mais: é preciso pedir outro
        assertThrows(RegraNegocioException.class, () -> servico.redefinir(EMAIL, certo, SENHA_NOVA, SENHA_NOVA));
    }

    @Test
    void naoMandaOutroEmailAntesDe1Minuto() throws Exception {
        servico.pedirCodigo(EMAIL);
        servico.pedirCodigo(EMAIL);
        assertEquals(1, enviados.size());

        agoraE(AGORA.plusSeconds(61));
        servico.pedirCodigo(EMAIL);
        assertEquals(2, enviados.size());
    }

    @Test
    void usuarioBloqueadoNaoRecuperaASenha() throws Exception {
        new ServicoAcesso().login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        new ServicoAcesso().bloquear(anaId);
        Sessao.encerrar();

        assertEquals(ServicoRecuperacaoSenha.MSG_ENVIADO, servico.pedirCodigo(EMAIL));
        assertTrue(enviados.isEmpty());
    }

    @Test
    void senhaNovaPrecisaSerForteEIgualNaConfirmacao() throws Exception {
        servico.pedirCodigo(EMAIL);
        String codigo = codigoDoEmail();

        assertThrows(RegraNegocioException.class, () -> servico.redefinir(EMAIL, codigo, "fraca", "fraca"));
        assertThrows(RegraNegocioException.class, () -> servico.redefinir(EMAIL, codigo, SENHA_NOVA, "Outra123"));
        // os erros de digitação não gastam tentativas: o código continua valendo
        assertDoesNotThrow(() -> servico.redefinir(EMAIL, codigo, SENHA_NOVA, SENHA_NOVA));
    }

    @Test
    void semEmailConfiguradoARecuperacaoFicaDesligada() {
        ServicoRecuperacaoSenha semCorreio = new ServicoRecuperacaoSenha(null);
        assertFalse(semCorreio.disponivel());
        assertThrows(RegraNegocioException.class, () -> semCorreio.pedirCodigo(EMAIL));
    }

    @Test
    void caixaDeSaidaSimuladaGuardaOEmailEmArquivo() throws Exception {
        Correio.Simulado simulado = new Correio.Simulado(pasta.resolve("caixa"));
        new ServicoRecuperacaoSenha(simulado).pedirCodigo(EMAIL);

        try (var arquivos = java.nio.file.Files.list(simulado.pasta())) {
            String texto = java.nio.file.Files.readString(arquivos.findFirst().orElseThrow());
            assertTrue(texto.contains("Para: " + EMAIL));
            assertTrue(texto.matches("(?s).*Seu código: \\d{6}.*"));
        }
    }
}
