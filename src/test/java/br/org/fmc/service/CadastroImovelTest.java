package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CadastroImovelTest {
    @TempDir Path pasta;
    private final ServicoImoveis servico = new ServicoImoveis();

    @BeforeEach void preparar() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        Sessao.iniciar(new Usuario("Administrador", "teste@example.org", Perfil.ADMINISTRADOR));
    }

    @AfterEach void restaurar() {
        Sessao.encerrar();
        Database.usarArquivo(Path.of("data", "fmc.db"));
    }

    @Test void informaTodosOsObrigatoriosSemGravar() throws Exception {
        var e = assertThrows(IllegalArgumentException.class, () -> servico.salvarFormulario(
                null, "  ", "", null, "", "", "", "", "", "", null));
        for (String campo : new String[]{"código", "unidade", "tipo", "endereço", "aluguel", "situação"}) {
            assertTrue(e.getMessage().contains(campo));
        }
        assertTrue(servico.listar().isEmpty());
    }

    @Test void rejeitaAluguelZeroNegativoOuInvalido() {
        for (String valor : new String[]{"0", "-1", "abc"}) {
            assertThrows(IllegalArgumentException.class, () -> cadastrar(null, "C1", valor));
        }
    }

    @Test void rejeitaCodigoRepetidoEPermiteEditarOProprio() throws Exception {
        cadastrar(null, "C1", "100,00");
        Long id = servico.listar().getFirst().getId();
        assertThrows(IllegalArgumentException.class, () -> cadastrar(null, " c1 ", "200"));
        cadastrar(id, "C1", "150,00");
        cadastrar(null, "A1", "300");
        Long outroId = servico.listar().stream().filter(i -> i.getCodigo().equals("A1")).findFirst().orElseThrow().getId();
        assertThrows(IllegalArgumentException.class, () -> cadastrar(outroId, "C1", "200"));
        assertEquals(2, servico.listar().size());
        var casa = servico.listar().stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
        assertEquals(new BigDecimal("150.00"), casa.getValorAluguel());
        assertEquals(0, BigDecimal.ZERO.compareTo(casa.getTaxaLuz()));
    }

    private void cadastrar(Long id, String codigo, String aluguel) throws Exception {
        servico.salvarFormulario(id, codigo, "Unidade " + codigo, TipoImovel.CASA, "Rua teste", "", "", aluguel,
                "", "", SituacaoImovel.VAGO);
    }

    @Test void bloqueiaUnidadeRepetidaMesmoComOutroCodigoOuEndereco() throws Exception {
        salvarUnidade(null, "01", "Apto 101", "Rua A");
        var erro = assertThrows(IllegalArgumentException.class,
                () -> salvarUnidade(null, "02", " apto 101 ", "Rua A"));
        assertTrue(erro.getMessage().contains("unidade"));
        assertThrows(IllegalArgumentException.class,
                () -> salvarUnidade(null, "02", "Apto 101", "Rua B"));
        assertEquals(1, servico.listar().size());
    }

    @Test void permiteEnderecoRepetidoEEditarSemMudarUnidade() throws Exception {
        salvarUnidade(null, "01", "101", "Rua A");
        Long id = servico.listar().getFirst().getId();
        salvarUnidade(null, "02", "102", "Rua A");
        salvarUnidade(id, "01", "101", "Rua A atualizada");
        assertThrows(IllegalArgumentException.class,
                () -> salvarUnidade(id, "01", "102", "Rua A atualizada"));
        assertEquals(2, servico.listar().size());
        assertEquals("101", servico.listar().stream().filter(i -> i.getId().equals(id))
                .findFirst().orElseThrow().getUnidade());
    }

    private void salvarUnidade(Long id, String codigo, String unidade, String endereco) throws Exception {
        servico.salvarFormulario(id, codigo, unidade, TipoImovel.APARTAMENTO, endereco,
                "", "", "1000", "", "", SituacaoImovel.VAGO);
    }

    @Test void excluiSomenteSelecionadoEPermiteReutilizarUnidade() throws Exception {
        salvarUnidade(null, "01", "101", "Rua A");
        salvarUnidade(null, "02", "102", "Rua A");
        Long id = servico.listar().stream().filter(i -> i.getCodigo().equals("01"))
                .findFirst().orElseThrow().getId();
        servico.excluir(id);
        assertEquals(1, servico.listar().size());
        assertEquals("02", servico.listar().getFirst().getCodigo());
        salvarUnidade(null, "03", "101", "Rua A");
        assertEquals(2, servico.listar().size());
        assertThrows(IllegalArgumentException.class, () -> servico.excluir(id));
    }

    @Test void naoExcluiSemPermissao() throws Exception {
        salvarUnidade(null, "01", "101", "Rua A");
        Long id = servico.listar().getFirst().getId();
        Sessao.iniciar(new Usuario("Consulta", "consulta@example.org", Perfil.FINANCEIRO));
        assertThrows(AcessoNegadoException.class, () -> servico.excluir(id));
        assertEquals(1, servico.listar().size());
    }
}
