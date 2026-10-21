package br.org.fmc.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Inquilino;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-35 · Gravar e ler inquilinos no banco. Dados fictícios. */
class InquilinoRepositoryTest {

    private static final Cpf CPF_MARIA = new Cpf("529.982.247-25");
    private static final Cpf CPF_CARLOS = new Cpf("111.444.777-35");

    @TempDir
    Path pasta;

    private final InquilinoRepository repo = new InquilinoRepository();

    @BeforeEach
    void bancoNovo() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
    }

    @Test
    void salvaEBuscaComTodosOsDadosDaProposta() throws Exception {
        Inquilino i = new Inquilino("Maria Teste", CPF_MARIA);
        i.setTelefone("(79) 99999-0000");
        i.setEmail("maria@exemplo.org");
        i.setEstadoCivil(EstadoCivil.CASADO);
        i.setProfissao("Professora");
        i.setLocalTrabalho("Escola Exemplo");
        i.setRendaMensal(new BigDecimal("2500.50"));
        i.setOutrasRendas(new BigDecimal("300.00"));
        Conjuge c = new Conjuge("Carlos Teste");
        c.setCpf(CPF_CARLOS);
        c.setRenda(new BigDecimal("1200.00"));
        i.setConjuge(c);

        repo.salvar(i);
        assertNotNull(i.getId());

        Inquilino lido = repo.buscarPorCpf(CPF_MARIA).orElseThrow();
        assertEquals("Maria Teste", lido.getNome());
        assertEquals("(79) 99999-0000", lido.getTelefone());
        assertEquals(EstadoCivil.CASADO, lido.getEstadoCivil());
        assertEquals("Escola Exemplo", lido.getLocalTrabalho());
        assertEquals(new BigDecimal("2500.50"), lido.getRendaMensal());
        assertEquals("Carlos Teste", lido.getConjuge().getNome());
        assertEquals(CPF_CARLOS, lido.getConjuge().getCpf());
        assertEquals(new BigDecimal("4000.50"), lido.rendaConsiderada());
    }

    @Test
    void atualizaQuandoJaTemIdERemoveConjuge() throws Exception {
        Inquilino i = new Inquilino("Maria Teste", CPF_MARIA);
        i.setEstadoCivil(EstadoCivil.CASADO);
        i.setConjuge(new Conjuge("Carlos Teste"));
        repo.salvar(i);

        i.setNome("Maria Teste Silva");
        i.setEstadoCivil(EstadoCivil.DIVORCIADO); // tira o cônjuge
        repo.salvar(i);

        Inquilino lido = repo.buscarPorId(i.getId()).orElseThrow();
        assertEquals("Maria Teste Silva", lido.getNome());
        assertNull(lido.getConjuge());
        assertEquals(1, repo.listarTodos().size());
    }

    @Test
    void bancoNaoAceitaDoisInquilinosComOMesmoCpf() throws Exception {
        repo.salvar(new Inquilino("Maria Teste", CPF_MARIA));
        assertThrows(SQLException.class, () -> repo.salvar(new Inquilino("Outra Pessoa", CPF_MARIA)));
    }

    @Test
    void cpfInexistenteVoltaVazio() throws Exception {
        assertTrue(repo.buscarPorCpf(CPF_MARIA).isEmpty());
        assertTrue(repo.listarTodos().isEmpty());
    }
}
