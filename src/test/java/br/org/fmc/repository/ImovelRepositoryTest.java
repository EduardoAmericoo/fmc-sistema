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
        assertNull(itens.get(0).getUnidade()); // unidade é opcional
        assertEquals("Residencial", salvo.getGrupo());
        assertEquals(casa.getValorAluguel(), salvo.getValorAluguel());
        assertEquals(casa.getTaxaLuz(), salvo.getTaxaLuz());
        assertEquals(casa.getValorIptu(), salvo.getValorIptu());
        assertEquals(SituacaoImovel.EM_MANUTENCAO, salvo.getSituacao());
        assertThrows(java.sql.SQLException.class, () -> repositorio.salvar(
                new Casa("C1", "Repetido", BigDecimal.TEN)));
    }
}
