package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.service.ServicoInquilinos.Ficha;
import br.org.fmc.service.ServicoInquilinos.Pagamento;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * [D8] BOARD-45 · Ficha do inquilino. Dados fictícios.
 *
 * "Hoje" é 12/10/2026. Maria teve um contrato no IM-002 (2025, encerrado) e hoje aluga o IM-001
 * desde 10/01/2026 (R$ 1.000 + R$ 50 de luz + R$ 20 de IPTU = R$ 1.070, vence dia 9):
 * junho pago em dia; julho pago com 11 dias de atraso; agosto e setembro em atraso;
 * outubro em tolerância (venceu há 3 dias); novembro a vencer.
 */
class FichaInquilinoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 12);

    @TempDir
    Path pasta;

    private final ServicoInquilinos servico = new ServicoInquilinos();
    private final AluguelRepository alugueis = new AluguelRepository();
    private Inquilino maria;
    private Contrato antigo;
    private Contrato atual;

    @BeforeEach
    void mariaComDoisContratosEAlugueisEmCadaSituacao() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));

        maria = servico.cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "(79) 99999-0000", "",
                EstadoCivil.SOLTEIRO, "Professora", "", "4.000,00", "", "", "", ""));
        ServicoContratos contratos = new ServicoContratos();
        antigo = contratos.cadastrar(contrato(novoImovel("IM-002"), LocalDate.of(2025, 1, 10)));
        contratos.encerrar(antigo.getId(), LocalDate.of(2025, 12, 20), MotivoEncerramento.SAIDA_DO_INQUILINO, "");
        atual = contratos.cadastrar(contrato(novoImovel("IM-001"), LocalDate.of(2026, 1, 10)));

        aluguel(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 9));
        aluguel(YearMonth.of(2026, 7), LocalDate.of(2026, 7, 20));
        aluguel(YearMonth.of(2026, 8), null);
        aluguel(YearMonth.of(2026, 9), null);
        aluguel(YearMonth.of(2026, 10), null);
        aluguel(YearMonth.of(2026, 11), null);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Imovel novoImovel(String codigo) throws Exception {
        new ServicoImoveis().salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        return new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(codigo))
                .findFirst().orElseThrow();
    }

    private DadosContrato contrato(Imovel imovel, LocalDate chaves) {
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "9.000,00", "Rua do Fiador, 10", "", "");
        return new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12, chaves, fiador, null);
    }

    private void aluguel(YearMonth mes, LocalDate pagoEm) throws Exception {
        Aluguel a = new Aluguel(atual, mes);
        if (pagoEm != null) {
            a.registrarPagamento(pagoEm);
        }
        alugueis.salvar(a);
    }

    @Test
    void contratoAtualEmDestaqueEOAntigoNaLista() throws Exception {
        Ficha f = servico.ficha(maria.getId());

        assertEquals("Maria Teste", f.inquilino().getNome());
        assertEquals(List.of(atual.getNumero(), antigo.getNumero()),
                f.contratos().stream().map(Contrato::getNumero).toList());
        assertEquals(atual.getNumero(), f.contratoAtual().getNumero());
    }

    @Test
    void cadaAluguelNaSuaSituacao() throws Exception {
        Ficha f = servico.ficha(maria.getId());

        // Do mais recente para o mais antigo.
        assertEquals(List.of(Pagamento.A_VENCER, Pagamento.EM_TOLERANCIA, Pagamento.EM_ATRASO, Pagamento.EM_ATRASO,
                        Pagamento.PAGO_COM_ATRASO, Pagamento.PAGO_EM_DIA),
                f.alugueis().stream().map(a -> Pagamento.de(a, f.hoje())).toList());
    }

    @Test
    void numerosDoFinanceiro() throws Exception {
        Ficha f = servico.ficha(maria.getId());

        assertEquals(2, f.pagos());
        assertEquals(1, f.pagosComAtraso());
        assertEquals(0, new BigDecimal("2140.00").compareTo(f.totalPago())); // junho + julho, sem encargos
        // Agosto e setembro em atraso: com multa e juros fica mais que os R$ 2.140 originais.
        assertTrue(f.emAtrasoHoje().compareTo(new BigDecimal("2140.00")) > 0, f.emAtrasoHoje().toString());
    }

    @Test
    void fichaDeInquilinoArquivadoContinuaAbrindo() throws Exception {
        Inquilino joana = servico.cadastrar(new DadosInquilino("Joana Teste", "390.533.447-05", "", "",
                EstadoCivil.SOLTEIRO, "", "", "3.000,00", "", "", "", ""));
        servico.arquivar(joana.getId(), "Mudou de cidade");

        Ficha f = servico.ficha(joana.getId());
        assertTrue(f.inquilino().estaArquivado());
        assertTrue(f.contratos().isEmpty());
        assertTrue(f.alugueis().isEmpty());
        assertEquals(null, f.contratoAtual());
    }

    @Test
    void quemNaoVeContratosNemAlugueisSoVeOCadastro() throws Exception {
        Usuario consulta = new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO);
        consulta.personalizarPermissoes(Set.of(Acao.VER_INQUILINOS));
        Sessao.iniciar(consulta);

        Ficha f = servico.ficha(maria.getId());
        assertEquals("Maria Teste", f.inquilino().getNome());
        assertTrue(f.contratos().isEmpty());
        assertTrue(f.alugueis().isEmpty());
    }

    @Test
    void semLoginNaoAbre() {
        Sessao.encerrar();
        assertThrows(AcessoNegadoException.class, () -> servico.ficha(maria.getId()));
    }
}
