package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-56 · Listar e buscar inquilinos por nome ou CPF, com os dados de teste (110 inquilinos fictícios). */
class BuscaInquilinosTest {

    @TempDir
    static Path pasta;

    private final ServicoInquilinos servico = new ServicoInquilinos();

    /** Carrega os dados de teste uma vez só: a busca não muda nada no banco. */
    @BeforeAll
    static void carregarDadosDeTeste() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        new ServicoDadosTeste().recarregarDoZero();
    }

    @BeforeEach
    void entrarComoAdministrador() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        new ServicoAcesso().login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private List<String> nomes(String busca) throws Exception {
        return servico.buscar(busca).stream().map(Inquilino::getNome).toList();
    }

    @Test
    void semBuscaListaTodosEmOrdemDeNome() throws Exception {
        List<String> todos = nomes("");
        assertEquals(110, todos.size());
        assertEquals(todos.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), todos);
        assertEquals(110, nomes("   ").size());
    }

    @Test
    void buscaPorNomeIgnoraMaiusculasAcentosEOrdemDasPalavras() throws Exception {
        assertEquals(List.of("Sofia Melo Costa"), nomes("sofia melo"));
        assertEquals(List.of("Sofia Melo Costa"), nomes("COSTA  Sofia"));
        assertEquals(List.of("José Castro Vasconcelos"), nomes("jose castro"));
        assertTrue(nomes("sofia").containsAll(List.of("Sofia Melo Costa", "Sofia Vasconcelos Farias",
                "Sofia Cavalcante Moraes", "Sofia Santana Santos")));
        assertTrue(nomes("sofia").stream().allMatch(n -> n.toLowerCase().contains("sofia")));
    }

    @Test
    void buscaPorCpfComOuSemPontuacaoEPorPedaco() throws Exception {
        assertEquals(List.of("Sofia Melo Costa"), nomes("884.570.565-06"));
        assertEquals(List.of("Sofia Melo Costa"), nomes("88457056506"));
        assertTrue(nomes("884.570").contains("Sofia Melo Costa"));
        assertTrue(nomes("884570").contains("Sofia Melo Costa"));
    }

    @Test
    void buscaSemResultadoDevolveListaVazia() throws Exception {
        assertEquals(List.of(), nomes("Zuleica Inexistente"));
        assertEquals(List.of(), nomes("000.000.000-00"));
    }

    @Test
    void quemNaoPodeVerInquilinosNaoBusca() {
        Usuario semAcesso = new Usuario("Sem acesso", "sem@exemplo.org", Perfil.SECRETARIA);
        semAcesso.personalizarPermissoes(Set.of()); // o Administrador tirou todas as permissões
        Sessao.iniciar(semAcesso);
        assertThrows(AcessoNegadoException.class, () -> servico.buscar("sofia"));
    }
}
