package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.DispensaEncargos;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.ReciboImpresso;
import br.org.fmc.util.ReciboPdf;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** [D11] BOARD-66 · Dispensar multa, juros e correção de um recebimento, com motivo e quem autorizou. */
class DispensaEncargosTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime AGORA = HOJE.atTime(14, 30);
    private static final String MOTIVO = "Acordo com a inquilina aprovado pela diretoria";

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void mariaComAgostoEmAtraso() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "Apto 101", TipoImovel.CASA, "Rua Exemplo, 1",
                "Centro", "", "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Araújo", "529.982.247-25",
                "(79) 99999-1111", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        Clock relogio = Clock.fixed(AGORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        recebimento.usarRelogio(relogio);
        alugueis.gerarDoMes(YearMonth.of(2026, 8));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Aluguel agosto() throws Exception {
        return alugueis.listarDoMes(YearMonth.of(2026, 8)).get(0);
    }

    private Usuario entrarComo(Perfil perfil) throws Exception {
        String email = perfil.name().toLowerCase() + "@exemplo.org";
        acesso.criarUsuario("Teste " + perfil.getDescricao(), email, perfil, "teste123");
        Sessao.encerrar();
        acesso.login(email, "teste123");
        return Sessao.usuario();
    }

    // ---------- total com e sem dispensa ----------

    @Test
    void semDispensaOTotalIncluiMultaJurosECorrecao() throws Exception {
        Aluguel a = agosto();
        Encargos e = recebimento.calcularEncargos(a, HOJE);

        assertTrue(e.temEncargos()); // agosto pago em novembro: com atraso
        assertEquals(0, a.base().add(e.total()).compareTo(recebimento.totalAReceber(a, HOJE, false)));
    }

    @Test
    void comDispensaOTotalESoOValorDevido() throws Exception {
        Aluguel a = agosto();

        assertEquals(0, a.base().compareTo(recebimento.totalAReceber(a, HOJE, true)));
    }

    @Test
    void reciboComDispensaTemTotalIgualAoValorDevidoEEncargosCobradosZerados() throws Exception {
        Aluguel a = agosto();
        Encargos calculados = recebimento.calcularEncargos(a, HOJE);

        Recibo recibo = recebimento.receber(a, HOJE, FormaPagamento.PIX, "TX-1", true, MOTIVO);

        assertEquals(0, a.base().compareTo(recibo.getValorTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(recibo.getEncargos().total()));
        assertEquals(calculados.getDiasAtraso(), recibo.getEncargos().getDiasAtraso()); // o atraso continua no histórico
    }

    @Test
    void reciboSemDispensaContinuaComoAntes() throws Exception {
        Aluguel a = agosto();
        Encargos calculados = recebimento.calcularEncargos(a, HOJE);

        Recibo recibo = recebimento.receber(a, HOJE, FormaPagamento.PIX, "TX-1");

        assertFalse(recibo.teveDispensa());
        assertEquals(0, a.base().add(calculados.total()).compareTo(recibo.getValorTotal()));
        assertNull(recebimento.reciboDoAluguel(agosto()).orElseThrow().getDispensa());
    }

    // ---------- motivo obrigatório ----------

    @Test
    void naoDispensaSemMotivo() throws Exception {
        for (String vazio : new String[] {null, "", "   "}) {
            RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                    () -> recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "", true, vazio));
            assertTrue(erro.getMessage().contains("motivo"));
        }
        assertFalse(agosto().estaPago()); // nada foi gravado
        assertTrue(recebimento.reciboDoAluguel(agosto()).isEmpty());
    }

    @Test
    void naoDispensaQuandoNaoHaEncargos() throws Exception {
        alugueis.gerarDoMes(YearMonth.of(2026, 11));
        Aluguel novembro = alugueis.listarDoMes(YearMonth.of(2026, 11)).get(0);
        LocalDate emDia = novembro.getVencimento().isAfter(HOJE) ? HOJE : novembro.getVencimento();
        assertFalse(recebimento.calcularEncargos(novembro, emDia).temEncargos());

        assertThrows(RegraNegocioException.class,
                () -> recebimento.receber(novembro, emDia, FormaPagamento.PIX, "", true, MOTIVO));
    }

    // ---------- o que fica gravado ----------

    @Test
    void gravaMotivoUsuarioDataEValoresDispensados() throws Exception {
        Aluguel a = agosto();
        Encargos calculados = recebimento.calcularEncargos(a, HOJE);
        Usuario admin = Sessao.usuario();

        recebimento.receber(a, HOJE, FormaPagamento.PIX, "TX-1", true, "  " + MOTIVO + "  ");

        // relido do banco, como na auditoria (janela do recibo)
        Recibo relido = recebimento.reciboDoAluguel(agosto()).orElseThrow();
        DispensaEncargos d = relido.getDispensa();
        assertTrue(relido.teveDispensa());
        assertEquals(MOTIVO, d.getMotivo());
        assertEquals(admin.getId(), d.getAutorizadoPor().getId());
        assertEquals(admin.getNome(), d.getAutorizadoPor().getNome());
        assertEquals(AGORA, d.getAutorizadoEm());
        assertEquals(0, calculados.getMulta().compareTo(d.getMulta()));
        assertEquals(0, calculados.getJuros().compareTo(d.getJuros()));
        assertEquals(0, calculados.getCorrecao().compareTo(d.getCorrecao()));
        assertTrue(d.getMulta().signum() > 0 && d.getJuros().signum() > 0 && d.getCorrecao().signum() > 0);
        assertEquals(0, a.base().compareTo(relido.getValorTotal()));
        assertTrue(agosto().estaPago());
    }

    @Test
    void reciboImpressoMostraEncargosOriginaisEALinhaDaDispensa() throws Exception {
        Aluguel a = agosto();
        Encargos calculados = recebimento.calcularEncargos(a, HOJE);
        Recibo recibo = recebimento.receber(a, HOJE, FormaPagamento.DINHEIRO, "", true, MOTIVO);
        ReciboImpresso r = new ReciboImpresso(recibo);

        BigDecimal soma = r.composicao().stream().map(ReciboImpresso.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, soma.compareTo(r.total())); // as linhas fecham com o TOTAL
        assertEquals(0, a.base().compareTo(r.total()));
        if (ReciboImpresso.MOSTRAR_ENCARGOS_DISPENSADOS) {
            assertTrue(r.composicao().contains(new ReciboImpresso.Item("Multa", calculados.getMulta())));
            assertTrue(r.composicao().contains(
                    new ReciboImpresso.Item(ReciboImpresso.ROTULO_DISPENSA, calculados.total().negate())));
        }
    }

    @Test
    void pdfDoReciboTrazALinhaDosEncargosDispensados() throws Exception {
        Recibo recibo = recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "", true, MOTIVO);
        Path arquivo = pasta.resolve("recibo.pdf");

        ReciboPdf.gerar(recibo, arquivo);

        try (PDDocument pdf = Loader.loadPDF(arquivo.toFile())) {
            String texto = new PDFTextStripper().getText(pdf);
            assertEquals(ReciboImpresso.MOSTRAR_ENCARGOS_DISPENSADOS, texto.contains(ReciboImpresso.ROTULO_DISPENSA));
        }
    }

    // ---------- permissão ----------

    @Test
    void regraProvisoriaSoOAdministradorPodeDispensar() {
        assertTrue(ServicoRecebimento.podeDispensarEncargos(new Usuario("A", "a@exemplo.org", Perfil.ADMINISTRADOR)));
        assertFalse(ServicoRecebimento.podeDispensarEncargos(new Usuario("S", "s@exemplo.org", Perfil.SECRETARIA)));
        assertFalse(ServicoRecebimento.podeDispensarEncargos(new Usuario("F", "f@exemplo.org", Perfil.FINANCEIRO)));
        assertFalse(ServicoRecebimento.podeDispensarEncargos(null));
    }

    @Test
    void secretariaRecebeMasNaoPodeDispensar() throws Exception {
        entrarComo(Perfil.SECRETARIA);
        assertFalse(recebimento.usuarioPodeDispensarEncargos());

        AcessoNegadoException erro = assertThrows(AcessoNegadoException.class,
                () -> recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "", true, MOTIVO));
        assertEquals(ServicoRecebimento.SEM_PERMISSAO_PARA_DISPENSAR, erro.getMessage());
        assertFalse(agosto().estaPago()); // nada foi gravado

        // sem dispensa, a Secretaria recebe normalmente, com os encargos
        Recibo recibo = recebimento.receber(agosto(), HOJE, FormaPagamento.PIX, "");
        assertFalse(recibo.teveDispensa());
        assertTrue(recibo.getEncargos().temEncargos());
    }

    @Test
    void administradorPodeDispensar() {
        assertTrue(recebimento.usuarioPodeDispensarEncargos());
    }
}
