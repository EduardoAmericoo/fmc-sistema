package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.Correio;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-189 · Mensagem de cobrança pronta por e-mail e WhatsApp, registrada no histórico. */
class MensagemCobrancaTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 10);

    /** Correio de mentira: guarda os e-mails em vez de enviar. */
    private final List<String[]> enviados = new ArrayList<>();
    private final Correio correio = (para, assunto, texto) -> enviados.add(new String[] {para, assunto, texto});

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoCobranca cobranca = new ServicoCobranca();
    private ServicoMensagemCobranca mensagens;

    @BeforeEach
    void mariaDeveSetembroEJoaoDeveAgostoESetembro() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        Clock c = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(c);
        recebimento.usarRelogio(c);
        cobranca.usarRelogio(c);
        mensagens = new ServicoMensagemCobranca(correio, cobranca);

        inquilino("IM-001", "Maria Silva Teste", "529.982.247-25", "(79) 99999-0000", "maria@exemplo.com");
        inquilino("IM-002", "João Teste", "111.444.777-35", "3333", "");
        alugueis.gerarDoMes(YearMonth.of(2026, 8));
        alugueis.gerarDoMes(YearMonth.of(2026, 9));
        Aluguel agostoMaria = alugueis.listarDoMes(YearMonth.of(2026, 8)).stream()
                .filter(a -> a.getContrato().getInquilino().getNome().startsWith("Maria")).findFirst().orElseThrow();
        recebimento.receber(agostoMaria, agostoMaria.getVencimento(), FormaPagamento.PIX, "");
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void inquilino(String imovel, String nome, String cpf, String telefone, String email) throws Exception {
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, imovel, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long idImovel = new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(imovel))
                .findFirst().orElseThrow().getId();
        br.org.fmc.model.Inquilino cadastrado = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf,
                "(79) 99999-0000", "teste@exemplo.org", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", ""));
        // Hoje telefone e e-mail são obrigatórios. Cadastros antigos (de antes da regra) podem não ter:
        // grava direto no banco para simular um desses.
        cadastrado.setTelefone(telefone);
        cadastrado.setEmail(email);
        new br.org.fmc.repository.InquilinoRepository().salvar(cadastrado);
        long idInquilino = cadastrado.getId();
        new ServicoContratos().cadastrar(new DadosContrato(idImovel, idInquilino, PrazoContrato.MESES_12, chaves,
                null, new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
    }

    private ServicoCobranca.Linha linha(String inicioDoNome) throws Exception {
        return cobranca.listarParaCobranca().stream()
                .filter(l -> l.contrato().getInquilino().getNome().startsWith(inicioDoNome)).findFirst().orElseThrow();
    }

    @Test
    void umAluguelAtrasadoRecebeLembreteGentil() throws Exception {
        ServicoMensagemCobranca.Mensagem m = mensagens.mensagem(linha("Maria"));

        assertEquals("Lembrete: aluguel de setembro/2026 em aberto · IM-001", m.assunto());
        assertTrue(m.texto().startsWith("Olá, Maria! Aqui é da Fundação Manuel Cruz."), m.texto());
        assertTrue(m.texto().contains("Se você já pagou, por favor desconsidere"), m.texto());
        assertTrue(m.texto().endsWith(ServicoMensagemCobranca.ASSINATURA));
    }

    @Test
    void doisOuMaisRecebeCobrancaMaisFirme() throws Exception {
        ServicoCobranca.Linha joao = linha("João");
        ServicoMensagemCobranca.Mensagem m = mensagens.mensagem(joao);

        assertEquals("Aluguéis em atraso · IM-002", m.assunto());
        assertTrue(m.texto().contains("Constam em aberto 2 aluguéis do imóvel IM-002 (agosto/2026 e setembro/2026)"),
                m.texto());
        assertTrue(m.texto().contains("regularizar o pagamento ou combinar um acordo"), m.texto());
        assertTrue(m.texto().contains(br.org.fmc.util.Formatos.moeda(joao.valorAtualizado())), m.texto());
    }

    @Test
    void emailSaiParaOInquilinoEEntraNoHistorico() throws Exception {
        ContatoCobranca contato = mensagens.enviarEmail(linha("Maria"), "");

        assertEquals(1, enviados.size());
        assertEquals("maria@exemplo.com", enviados.get(0)[0]);
        assertTrue(enviados.get(0)[2].startsWith("Olá, Maria!"));
        assertEquals(ResultadoContato.EMAIL_ENVIADO, contato.tipo());
        assertEquals("E-mail enviado · Para maria@exemplo.com", contato.descricao());
        // a lista de cobrança passa a mostrar o e-mail como último contato
        ServicoCobranca.Linha depois = linha("Maria");
        assertEquals("E-mail enviado", depois.acompanhamento().texto());
    }

    @Test
    void textoAjustadoNaJanelaEOQueSai() throws Exception {
        mensagens.enviarEmail(linha("Maria"), "Texto revisado pela Secretaria");
        assertEquals("Texto revisado pela Secretaria", enviados.get(0)[2]);
    }

    @Test
    void semEmailOuSemCorreioNaoEnvia() throws Exception {
        ServicoCobranca.Linha joao = linha("João");
        assertEquals("O inquilino não tem e-mail no cadastro.", mensagens.problemaEmail(joao));
        assertThrows(RegraNegocioException.class, () -> mensagens.enviarEmail(joao, ""));

        ServicoMensagemCobranca semCorreio = new ServicoMensagemCobranca(null, cobranca);
        assertEquals("O envio de e-mail não está configurado neste computador.",
                semCorreio.problemaEmail(linha("Maria")));
        assertTrue(enviados.isEmpty());
        assertTrue(cobranca.historico(joao.contrato().getId()).isEmpty()); // nada registrado
    }

    @Test
    void whatsAppAbreComNumeroETextoProntosEEntraNoHistorico() throws Exception {
        String link = mensagens.abrirWhatsApp(linha("Maria"), "");

        assertTrue(link.startsWith("https://wa.me/5579999990000?text="), link);
        String texto = URLDecoder.decode(link.substring(link.indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertTrue(texto.startsWith("Olá, Maria! Aqui é da Fundação Manuel Cruz."), texto);
        assertFalse(link.contains(" "));
        assertEquals("WhatsApp enviado", linha("Maria").acompanhamento().texto());
    }

    @Test
    void whatsAppPrecisaDeCelularComDdd() throws Exception {
        assertEquals("5579999990000", ServicoMensagemCobranca.numeroWhatsApp("(79) 99999-0000"));
        assertEquals("557932110000", ServicoMensagemCobranca.numeroWhatsApp("79 3211-0000"));
        assertEquals("5579999990000", ServicoMensagemCobranca.numeroWhatsApp("+55 79 99999-0000"));
        assertNull(ServicoMensagemCobranca.numeroWhatsApp("3333"));
        assertNull(ServicoMensagemCobranca.numeroWhatsApp(null));
        assertThrows(RegraNegocioException.class, () -> mensagens.abrirWhatsApp(linha("João"), ""));
    }

    @Test
    void quemNaoRegistraContatoNaoMandaMensagem() throws Exception {
        ServicoCobranca.Linha maria = linha("Maria");
        ServicoAcesso acesso = new ServicoAcesso();
        var consulta = acesso.criarUsuario("Teste Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO, "teste123");
        acesso.alterarPermissoes(consulta.getId(), java.util.Set.of(br.org.fmc.model.Acao.VER_ALUGUEIS));
        Sessao.encerrar();
        acesso.login("consulta@exemplo.org", "teste123");

        assertThrows(AcessoNegadoException.class, () -> mensagens.enviarEmail(maria, ""));
        assertThrows(AcessoNegadoException.class, () -> mensagens.abrirWhatsApp(maria, ""));
        assertTrue(enviados.isEmpty());
    }

    @Test
    void mensagensNaoAparecemComoOpcaoDaLigacao() {
        assertEquals(List.of(ResultadoContato.NAO_ATENDEU, ResultadoContato.PROMETEU_PAGAR,
                        ResultadoContato.SEM_ACORDO, ResultadoContato.TELEFONE_ERRADO),
                java.util.Arrays.stream(ResultadoContato.values()).filter(ResultadoContato::isDaLigacao).toList());
    }
}
