package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.repository.InquilinoRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-35 · Cadastrar inquilino com validação de CPF. Dados fictícios. */
class CadastroInquilinoTest {

    private static final String CPF_MARIA = "529.982.247-25";
    private static final String CPF_CARLOS = "11144477735";

    @TempDir
    Path pasta;

    private final InquilinoRepository repo = new InquilinoRepository();
    private final ServicoInquilinos servico = new ServicoInquilinos(repo);
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private static DadosInquilino maria(String cpf) {
        return new DadosInquilino("Maria Teste", cpf, "(79) 99999-0000", "maria@exemplo.org",
                EstadoCivil.SOLTEIRO, "Professora", "Escola Exemplo", "2.500,00", "", "", "", "");
    }

    @Test
    void cadastraInquilinoComOsDadosDaProposta() throws Exception {
        Inquilino novo = servico.cadastrar(maria(CPF_MARIA));
        assertNotNull(novo.getId());

        Inquilino lido = repo.buscarPorId(novo.getId()).orElseThrow();
        assertEquals("52998224725", lido.getCpf().getNumero());
        assertEquals("Professora", lido.getProfissao());
        assertEquals(new BigDecimal("2500.00"), lido.getRendaMensal());
    }

    @Test
    void cpfInvalidoNaoEAceito() throws Exception {
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(maria("529.982.247-26")));
        assertEquals(List.of("CPF inválido: confira os dígitos."), e.getErros());
        assertTrue(repo.listarTodos().isEmpty());
    }

    @Test
    void naoPermiteDoisInquilinosComOMesmoCpf() throws Exception {
        servico.cadastrar(maria(CPF_MARIA));
        // Mesmo CPF digitado sem pontos: continua sendo repetido.
        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.cadastrar(maria("52998224725")));
        assertEquals(List.of("Já existe um inquilino com este CPF: Maria Teste."), e.getErros());
        assertEquals(1, repo.listarTodos().size());
    }

    @Test
    void editaInquilinoMantendoOProprioCpf() throws Exception {
        Inquilino maria = servico.cadastrar(maria(CPF_MARIA));
        DadosInquilino casada = new DadosInquilino("Maria Teste Silva", CPF_MARIA, "", "",
                EstadoCivil.CASADO, "", "", "2500", "", "Carlos Teste", CPF_CARLOS, "1.000,00");

        servico.editar(maria.getId(), casada);

        Inquilino lido = repo.buscarPorId(maria.getId()).orElseThrow();
        assertEquals("Maria Teste Silva", lido.getNome());
        assertNull(lido.getTelefone());
        assertEquals("Carlos Teste", lido.getConjuge().getNome());
        assertEquals(new BigDecimal("3500.00"), lido.rendaConsiderada());
        assertEquals(1, repo.listarTodos().size());
    }

    @Test
    void edicaoNaoPodeUsarOCpfDeOutroInquilino() throws Exception {
        servico.cadastrar(maria(CPF_MARIA));
        Inquilino carlos = servico.cadastrar(new DadosInquilino("Carlos Teste", CPF_CARLOS, "", "",
                EstadoCivil.SOLTEIRO, "", "", "", "", "", "", ""));

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.editar(carlos.getId(), maria(CPF_MARIA)));
        assertTrue(e.getErros().contains("Já existe um inquilino com este CPF: Maria Teste."));
    }

    @Test
    void mostraTodosOsErrosDeUmaVez() {
        DadosInquilino errado = new DadosInquilino(" ", "", "", "sem-arroba", EstadoCivil.CASADO,
                "", "", "dois mil", "", "", "123", "");
        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.cadastrar(errado));
        assertEquals(List.of(
                "Informe o nome completo.",
                "Informe o CPF.",
                "Informe um e-mail válido.",
                "Renda mensal inválida: use só números, ex.: 2.500,00.",
                "Informe o nome do cônjuge.",
                "CPF do cônjuge inválido: confira os dígitos."), e.getErros());
    }

    @Test
    void solteiroIgnoraCamposDoConjuge() throws Exception {
        DadosInquilino solteira = new DadosInquilino("Maria Teste", CPF_MARIA, "", "", EstadoCivil.SOLTEIRO,
                "", "", "", "", "Sobrou da tela", "", "");
        assertNull(servico.cadastrar(solteira).getConjuge());
    }

    @Test
    void financeiroConsultaMasNaoCadastra() throws Exception {
        servico.cadastrar(maria(CPF_MARIA));
        acesso.criarUsuario("Contabilidade", "contabilidade@exemplo.org", Perfil.FINANCEIRO, "senha1");
        acesso.sair();
        acesso.login("contabilidade@exemplo.org", "senha1");

        assertEquals(1, servico.listar().size());
        assertThrows(AcessoNegadoException.class, () -> servico.cadastrar(maria(CPF_CARLOS)));
    }

    @Test
    void secretariaCadastra() throws Exception {
        acesso.criarUsuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA, "senha1");
        acesso.sair();
        acesso.login("secretaria@exemplo.org", "senha1");

        assertNotNull(servico.cadastrar(maria(CPF_MARIA)).getId());
    }

    @Test
    void semLoginNaoCadastra() {
        acesso.sair();
        assertThrows(AcessoNegadoException.class, () -> servico.cadastrar(maria(CPF_MARIA)));
    }
}
