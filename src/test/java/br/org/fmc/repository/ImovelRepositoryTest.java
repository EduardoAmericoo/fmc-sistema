package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImovelRepositoryTest {
    @TempDir Path pasta;
    private final ImovelRepository repositorio = new ImovelRepository();

    @BeforeEach void preparar() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
    }

    @AfterEach void restaurar() {
        Database.usarArquivo(Path.of("data", "fmc.db"));
    }

    @Test void persisteSubclassesEAtualizaSemDuplicar() throws Exception {
        Casa casa = new Casa("C1", "Rua de teste", new BigDecimal("1234.56"));
        casa.setBairro("Centro");
        casa.setUnidade("Casa 22");
        casa.setNomeInquilino("Pessoa Teste");
        casa.setContratoAte(java.time.LocalDate.of(2028, 11, 3));
        casa.setGrupo("Residencial");
        casa.setTaxaLuz(new BigDecimal("12.34"));
        casa.setValorIptu(new BigDecimal("56.78"));
        repositorio.salvar(casa);
        repositorio.salvar(new Apartamento("A1", "Outra rua", new BigDecimal("900.00")));
        casa.setEndereco("Endereço atualizado");
        casa.setSituacao(SituacaoImovel.EM_MANUTENCAO);
        repositorio.salvar(casa);
        var itens = repositorio.listarTodos();
        assertEquals(2, itens.size());
        assertInstanceOf(Apartamento.class, itens.get(0));
        Imovel salvo = itens.get(1);
        assertInstanceOf(Casa.class, salvo);
        assertEquals(casa.getId(), salvo.getId());
        assertEquals("Endereço atualizado", salvo.getEndereco());
        assertEquals("Centro", salvo.getBairro());
        assertEquals("Casa 22", salvo.getUnidade());
        assertEquals("Pessoa Teste", salvo.getNomeInquilino());
        assertEquals(java.time.LocalDate.of(2028, 11, 3), salvo.getContratoAte());
        assertEquals("Residencial", salvo.getGrupo());
        assertEquals(casa.getValorAluguel(), salvo.getValorAluguel());
        assertEquals(casa.getTaxaLuz(), salvo.getTaxaLuz());
        assertEquals(casa.getValorIptu(), salvo.getValorIptu());
        assertEquals(SituacaoImovel.EM_MANUTENCAO, salvo.getSituacao());
        assertThrows(java.sql.SQLException.class, () -> repositorio.salvar(
                new Casa("C1", "Repetido", BigDecimal.TEN)));
    }

    @Test void atualizaBancoAntigoSemPerderImoveis() throws Exception {
        try (var c = Database.conectar(); var s = c.createStatement()) {
            s.execute("DROP TABLE imovel");
            s.execute("CREATE TABLE imovel (id INTEGER PRIMARY KEY, codigo TEXT UNIQUE, tipo TEXT, endereco TEXT, bairro TEXT, grupo TEXT, valor_aluguel TEXT, taxa_luz TEXT, valor_iptu TEXT, situacao TEXT)");
            s.execute("INSERT INTO imovel VALUES (1,'C1','CASA','Rua teste',NULL,NULL,'100','0','0','VAGO')");
        }
        Database.inicializar();
        Database.inicializar();
        var casa = repositorio.listarTodos().getFirst();
        assertEquals("C1", casa.getCodigo());
        assertNull(casa.getUnidade());
        assertNull(casa.getNomeInquilino());
        assertNull(casa.getContratoAte());
        casa.setUnidade("Casa 22");
        casa.setNomeInquilino("Pessoa Teste");
        casa.setContratoAte(java.time.LocalDate.of(2028, 11, 3));
        repositorio.salvar(casa);
        assertEquals("Casa 22", repositorio.listarTodos().getFirst().getUnidade());
        assertEquals("Pessoa Teste", repositorio.listarTodos().getFirst().getNomeInquilino());
        casa.setNomeInquilino("");
        casa.setContratoAte(null);
        repositorio.salvar(casa);
        assertNull(repositorio.listarTodos().getFirst().getNomeInquilino());
        assertNull(repositorio.listarTodos().getFirst().getContratoAte());
    }
}
