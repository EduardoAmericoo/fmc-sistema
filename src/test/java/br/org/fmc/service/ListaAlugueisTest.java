package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoAluguel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ImovelRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * BOARD-38 · Lista de aluguéis com situação e filtros. Dados fictícios.
 * Dois contratos com entrega das chaves em 02/07/2026: o 1º aluguel vence em 01/08 e os
 * seguintes todo dia 1 (01/09, 01/10, 01/11...).
 */
class ListaAlugueisTest {

    private static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    @TempDir
    Path pasta;

    private final ServicoAlugueis servico = new ServicoAlugueis();
    private final AluguelRepository repo = new AluguelRepository();
    private Contrato maria;
    private Contrato carlos;

    @BeforeEach
    void bancoComDoisContratos() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        maria = contrato("IM-001", "Maria Teste", "529.982.247-25");
        carlos = contrato("IM-002", "Carlos Souza", "111.444.777-35");
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Contrato contrato(String codigo, String nome, String cpf) throws Exception {
        new ServicoImoveis().salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().stream().filter(i -> i.getCodigo().equals(codigo))
                .findFirst().orElseThrow().getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino(nome, cpf, "", "", EstadoCivil.SOLTEIRO,
                "", "", "5.000,00", "", "", "", "")).getId();
        return new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12,
                LocalDate.of(2026, 7, 2), null,
                new DadosContrato.Caucao("3.000,00", LocalDate.of(2026, 7, 1), FormaPagamento.PIX, "")));
    }

    private Aluguel aluguel(Contrato c, YearMonth mes, LocalDate pagoEm) throws SQLException {
        Aluguel a = new Aluguel(c, mes);
        if (pagoEm != null) {
            a.registrarPagamento(pagoEm);
        }
        repo.salvar(a);
        return a;
    }

    private void hojeE(LocalDate dia) {
        servico.usarRelogio(Clock.fixed(dia.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void situacaoMudaComADataDeHoje() throws Exception {
        aluguel(maria, OUTUBRO, null); // vence em 01/10
        Aluguel a = servico.listarDoMes(OUTUBRO).get(0);

        assertEquals(SituacaoAluguel.A_VENCER, a.getSituacao(LocalDate.of(2026, 9, 25)));    // antes
        assertEquals(SituacaoAluguel.A_VENCER, a.getSituacao(LocalDate.of(2026, 10, 1)));    // no dia
        assertEquals(SituacaoAluguel.EM_TOLERANCIA, a.getSituacao(LocalDate.of(2026, 10, 2))); // 1 dia depois
        assertEquals(SituacaoAluguel.EM_TOLERANCIA, a.getSituacao(LocalDate.of(2026, 10, 6))); // 5º dia
        assertEquals(SituacaoAluguel.EM_ATRASO, a.getSituacao(LocalDate.of(2026, 10, 7)));     // 6º dia
    }

    @Test
    void aluguelPagoFicaPagoMesmoDepoisDoVencimento() throws Exception {
        aluguel(maria, SETEMBRO, LocalDate.of(2026, 9, 10)); // pago com atraso, mas pago
        Aluguel a = servico.listarDoMes(SETEMBRO).get(0);
        assertEquals(LocalDate.of(2026, 9, 10), a.getDataPagamento());
        assertEquals(SituacaoAluguel.PAGO, a.getSituacao(LocalDate.of(2026, 12, 1)));
    }

    @Test
    void listaSoOsAlugueisDoMesEscolhido() throws Exception {
        aluguel(maria, SETEMBRO, LocalDate.of(2026, 9, 1));
        aluguel(maria, OUTUBRO, null);
        aluguel(carlos, OUTUBRO, null);

        assertEquals(1, servico.listarDoMes(SETEMBRO).size());
        assertEquals(List.of("IM-001", "IM-002"), servico.listarDoMes(OUTUBRO).stream()
                .map(a -> a.getContrato().getImovel().getCodigo()).toList());
        assertTrue(servico.listarDoMes(YearMonth.of(2026, 12)).isEmpty());
    }

    @Test
    void filtroPorMesTrazOsMesesComAlugueisEOMesAtual() throws Exception {
        aluguel(maria, SETEMBRO, LocalDate.of(2026, 9, 1));
        aluguel(maria, OUTUBRO, null);
        hojeE(LocalDate.of(2026, 11, 3)); // novembro ainda sem aluguéis gerados

        assertEquals(List.of(YearMonth.of(2026, 11), OUTUBRO, SETEMBRO), servico.mesesDisponiveis());
    }

    @Test
    void filtraPorSituacaoEPorBusca() throws Exception {
        aluguel(maria, OUTUBRO, LocalDate.of(2026, 10, 1)); // paga
        aluguel(carlos, OUTUBRO, null);                      // em aberto
        LocalDate hoje = LocalDate.of(2026, 10, 9);          // 8 dias depois do vencimento
        List<Aluguel> outubro = servico.listarDoMes(OUTUBRO);

        assertEquals(List.of("Carlos Souza"), nomes(ServicoAlugueis.filtrar(outubro, SituacaoAluguel.EM_ATRASO, "", hoje)));
        assertEquals(List.of("Maria Teste"), nomes(ServicoAlugueis.filtrar(outubro, SituacaoAluguel.PAGO, null, hoje)));
        assertEquals(List.of(), ServicoAlugueis.filtrar(outubro, SituacaoAluguel.A_VENCER, "", hoje));
        assertEquals(2, ServicoAlugueis.filtrar(outubro, null, "", hoje).size());
        assertEquals(List.of("Carlos Souza"), nomes(ServicoAlugueis.filtrar(outubro, null, "im-002", hoje)));
        assertEquals(List.of("Maria Teste"), nomes(ServicoAlugueis.filtrar(outubro, null, "MARIA", hoje)));
        assertEquals(List.of("Maria Teste"), nomes(ServicoAlugueis.filtrar(outubro, null, maria.getNumero(), hoje)));
    }

    @Test
    void totaisPorSituacaoSomamOValorDoMes() throws Exception {
        aluguel(maria, OUTUBRO, LocalDate.of(2026, 10, 1));
        aluguel(carlos, OUTUBRO, null);
        Map<SituacaoAluguel, ServicoAlugueis.Total> totais =
                ServicoAlugueis.totais(servico.listarDoMes(OUTUBRO), LocalDate.of(2026, 10, 4));

        assertEquals(1, totais.get(SituacaoAluguel.PAGO).quantidade());
        assertEquals(new BigDecimal("1070.00"), totais.get(SituacaoAluguel.PAGO).valor()); // aluguel + luz + IPTU
        assertEquals(1, totais.get(SituacaoAluguel.EM_TOLERANCIA).quantidade());
        assertEquals(0, totais.get(SituacaoAluguel.EM_ATRASO).quantidade());
    }

    @Test
    void mesmoContratoNaoTemDoisAlugueisNoMesmoMes() throws Exception {
        aluguel(maria, OUTUBRO, null);
        assertThrows(SQLException.class, () -> aluguel(maria, OUTUBRO, null)); // base para a BOARD-37 não duplicar
    }

    @Test
    void quemNaoPodeVerAlugueisNaoVeALista() {
        Usuario semAcesso = new Usuario("Sem acesso", "sem@exemplo.org", Perfil.SECRETARIA);
        semAcesso.personalizarPermissoes(Set.of());
        Sessao.iniciar(semAcesso);
        assertThrows(AcessoNegadoException.class, () -> servico.listarDoMes(OUTUBRO));

        Sessao.iniciar(new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO)); // só consulta: vê
        assertDoesNotThrow(() -> servico.listarDoMes(OUTUBRO));
    }

    private static List<String> nomes(List<Aluguel> lista) {
        return lista.stream().map(a -> a.getContrato().getInquilino().getNome()).toList();
    }
}
