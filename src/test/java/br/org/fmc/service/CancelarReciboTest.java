package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Conta;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** [D10] BOARD-65 · Cancelar recibo emitido por engano, com estorno no Caixa ou no Banco. */
class CancelarReciboTest {

    @TempDir
    Path pasta;

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 3);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final ServicoAlugueis alugueis = new ServicoAlugueis();
    private final ServicoRecebimento recebimento = new ServicoRecebimento();
    private final ServicoCaixaBanco caixaBanco = new ServicoCaixaBanco();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void mariaComAgostoEmAberto() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25",
                "(79) 99999-0000", "teste@exemplo.org", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        alugueis.usarRelogio(relogio);
        recebimento.usarRelogio(relogio);
        caixaBanco.usarRelogio(relogio);
        alugueis.gerarDoMes(AGOSTO);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Aluguel agosto() throws Exception {
        return alugueis.listarDoMes(AGOSTO).get(0);
    }

    private Recibo receberAgosto(FormaPagamento forma) throws Exception {
        return recebimento.receber(agosto(), LocalDate.of(2026, 9, 10), forma, "");
    }

    private static void mesmoValor(BigDecimal esperado, BigDecimal obtido) {
        assertEquals(0, esperado.compareTo(obtido), "esperado " + esperado + ", veio " + obtido);
    }

    @Test
    void cancelarReabreOAluguelEEstornaNoBancoNoDiaDoCancelamento() throws Exception {
        Recibo recibo = receberAgosto(FormaPagamento.PIX);
        mesmoValor(recibo.getValorTotal(), caixaBanco.disponibilidades().banco());

        Lancamento estorno = recebimento.cancelarRecibo(recibo, "PIX lançado no aluguel errado");

        assertEquals(TipoLancamento.ESTORNO, estorno.tipo());
        assertEquals(Conta.BANCO, estorno.conta());
        assertEquals(HOJE, estorno.data());
        mesmoValor(recibo.getValorTotal().negate(), estorno.valor());
        assertTrue(estorno.descricao().contains(recibo.getNumero()));

        Aluguel reaberto = agosto();
        assertFalse(reaberto.estaPago());
        assertNull(reaberto.getNumeroRecibo());
        assertTrue(recebimento.reciboDoAluguel(reaberto).isEmpty());
        // o Banco volta a zero; a saída aparece no movimento de novembro
        mesmoValor(BigDecimal.ZERO, caixaBanco.disponibilidades().banco());
        mesmoValor(recibo.getValorTotal(), caixaBanco.movimento(Conta.BANCO, NOVEMBRO).saidas());
    }

    @Test
    void depoisDeCanceladoOAluguelPodeSerRecebidoDeNovoComNumeroNovo() throws Exception {
        Recibo errado = receberAgosto(FormaPagamento.PIX);
        recebimento.cancelarRecibo(errado, "Forma de pagamento errada");

        Recibo certo = receberAgosto(FormaPagamento.DINHEIRO);

        assertNotEquals(errado.getNumero(), certo.getNumero());
        assertEquals(certo.getNumero(), recebimento.reciboDoAluguel(agosto()).orElseThrow().getNumero());
        assertEquals(certo.getNumero(), agosto().getNumeroRecibo());
        mesmoValor(certo.getValorTotal(), caixaBanco.disponibilidades().caixa());
        mesmoValor(BigDecimal.ZERO, caixaBanco.disponibilidades().banco());
    }

    @Test
    void motivoEObrigatorioENaoCancelaDuasVezes() throws Exception {
        Recibo recibo = receberAgosto(FormaPagamento.DINHEIRO);
        assertThrows(RegraNegocioException.class, () -> recebimento.cancelarRecibo(recibo, "  "));
        assertThrows(RegraNegocioException.class, () -> recebimento.cancelarRecibo(recibo, "erro"));
        assertTrue(agosto().estaPago()); // nada mudou

        recebimento.cancelarRecibo(recibo, "Recebido em duplicidade");
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> recebimento.cancelarRecibo(recibo, "Recebido em duplicidade"));
        assertEquals("Este recibo já foi cancelado.", e.getErros().get(0));
    }

    @Test
    void soQuemTemAPermissaoCancela() throws Exception {
        assertTrue(Perfil.ADMINISTRADOR.getPermissoes().contains(Acao.CANCELAR_RECIBO));
        assertFalse(Perfil.SECRETARIA.getPermissoes().contains(Acao.CANCELAR_RECIBO));
        assertFalse(Perfil.FINANCEIRO.getPermissoes().contains(Acao.CANCELAR_RECIBO));
        Recibo recibo = receberAgosto(FormaPagamento.DINHEIRO);

        acesso.criarUsuario("Teste Secretaria", "secretaria@exemplo.org", Perfil.SECRETARIA, "teste123");
        Sessao.encerrar();
        acesso.login("secretaria@exemplo.org", "teste123");
        assertThrows(AcessoNegadoException.class,
                () -> recebimento.cancelarRecibo(recibo, "Recebido em duplicidade"));
    }

    @Test
    void bancoAntigoComUmReciboPorAluguelEAjustadoSemPerderRecibos() throws Exception {
        Recibo antigo = receberAgosto(FormaPagamento.PIX);
        // volta a tabela recibo ao jeito antigo (aluguel_id UNIQUE), com o recibo dentro
        try (Connection c = Database.conectar(); Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF");
            st.execute("CREATE TABLE recibo_velho AS SELECT * FROM recibo");
            st.execute("DROP TABLE recibo");
            st.execute("CREATE TABLE recibo (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "aluguel_id INTEGER NOT NULL UNIQUE REFERENCES aluguel(id), ano INTEGER NOT NULL, "
                    + "sequencial INTEGER NOT NULL, data_pagamento TEXT NOT NULL, forma_pagamento TEXT NOT NULL, "
                    + "documento TEXT, dias_atraso INTEGER NOT NULL, meses_encargos INTEGER NOT NULL, "
                    + "multa TEXT NOT NULL, juros TEXT NOT NULL, correcao TEXT NOT NULL, valor_total TEXT NOT NULL, "
                    + "emitido_por INTEGER NOT NULL REFERENCES usuario(id), data_emissao TEXT NOT NULL, "
                    + "UNIQUE (ano, sequencial))");
            st.execute("INSERT INTO recibo SELECT * FROM recibo_velho");
            st.execute("DROP TABLE recibo_velho");
        }

        Database.inicializar(); // abrir o sistema de novo ajusta o banco
        Database.inicializar(); // e abrir outra vez não faz nada

        try (Connection c = Database.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE name = 'recibo'")) {
            assertTrue(rs.next());
            assertFalse(rs.getString(1).matches("(?s).*aluguel_id\\s+INTEGER\\s+NOT\\s+NULL\\s+UNIQUE.*"));
        }
        Recibo lido = recebimento.reciboDoAluguel(agosto()).orElseThrow();
        assertEquals(antigo.getId(), lido.getId());
        assertEquals(antigo.getNumero(), lido.getNumero());

        // e agora cancelar e receber de novo funciona no banco ajustado
        recebimento.cancelarRecibo(lido, "Teste do banco antigo");
        Recibo novo = receberAgosto(FormaPagamento.DINHEIRO);
        assertTrue(agosto().estaPago());
        assertEquals(novo.getNumero(), agosto().getNumeroRecibo());
    }
}
