package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.UsuarioRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BOARD-52 · Dados de teste: a carga no banco e a coerência dos arquivos CSV. */
class DadosTesteTest {

    @TempDir
    Path pasta;

    private final ServicoDadosTeste dados = new ServicoDadosTeste();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void bancoNovo() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private void entrarComoAdministrador() throws Exception {
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @Test
    void carregaTudoSemNenhumDadoRecusado() throws Exception {
        ServicoDadosTeste.Resumo resumo = dados.recarregarDoZero();

        assertEquals(124, resumo.imoveis());
        assertEquals(110, resumo.inquilinos());
        assertEquals(4, resumo.usuarios());
        assertEquals(110, resumo.contratos());
        entrarComoAdministrador();
        assertEquals(124, new ServicoImoveis().listar().size());
        assertEquals(110, new ServicoInquilinos().listar().size());
        assertEquals(5, acesso.listarUsuarios().size()); // Administrador inicial + 4 de teste
        List<Contrato> contratos = new ServicoContratos().listar();
        assertEquals(110, contratos.size());
        assertEquals("CT-1001", contratos.get(0).getNumero());
        assertEquals(100, contratos.stream().filter(c -> c.getGarantia() instanceof Fianca).count());
        assertEquals(10, contratos.stream().filter(c -> c.getGarantia() instanceof Caucao).count());
        // Aluguéis de teste (BOARD-38): o mês passado tem pagos e em aberto, para a tela mostrar as situações.
        assertTrue(resumo.alugueis() > 0);
        ServicoAlugueis alugueis = new ServicoAlugueis();
        java.time.YearMonth mesPassado = java.time.YearMonth.from(alugueis.hoje()).minusMonths(1);
        List<br.org.fmc.model.SituacaoAluguel> situacoes = alugueis.listarDoMes(mesPassado).stream()
                .map(a -> a.getSituacao(alugueis.hoje())).toList();
        assertTrue(situacoes.contains(br.org.fmc.model.SituacaoAluguel.PAGO));
        assertTrue(situacoes.stream().anyMatch(s -> s != br.org.fmc.model.SituacaoAluguel.PAGO));
        // Todo imóvel com contrato ficou ocupado, e só eles.
        assertEquals(110, new ServicoImoveis().listar().stream().filter(i -> i.getSituacao() == SituacaoImovel.OCUPADO).count());
    }

    @Test
    void recarregarApagaOQueHaviaEComecaDoZero() throws Exception {
        dados.recarregarDoZero();
        entrarComoAdministrador();
        new ServicoImoveis().salvarFormulario(null, "EXTRA-1", "", TipoImovel.CASA, "Rua Extra, 1", "", "",
                "1000", "", "", SituacaoImovel.VAGO);
        acesso.sair();

        dados.recarregarDoZero();

        entrarComoAdministrador();
        assertEquals(124, new ServicoImoveis().listar().size());
        assertFalse(new ServicoImoveis().listar().stream().anyMatch(i -> i.getCodigo().equals("EXTRA-1")));
        assertEquals(5, acesso.listarUsuarios().size());
    }

    @Test
    void bancoVazioCarregaSozinho() throws Exception {
        assertTrue(dados.carregarSeBancoVazio().isPresent());
        entrarComoAdministrador();
        assertEquals(124, new ServicoImoveis().listar().size());
    }

    @Test
    void bancoComDadosNaoEhMexido() throws Exception {
        acesso.garantirAdministradorInicial();
        entrarComoAdministrador();
        new ServicoImoveis().salvarFormulario(null, "MEU-1", "", TipoImovel.CASA, "Rua Minha, 1", "", "",
                "1000", "", "", SituacaoImovel.VAGO);
        acesso.sair();

        assertTrue(dados.carregarSeBancoVazio().isEmpty());

        entrarComoAdministrador();
        assertEquals(1, new ServicoImoveis().listar().size());
        assertEquals(1, acesso.listarUsuarios().size());
    }

    @Test
    void usuariosDeTesteEntramComAsPermissoesCertas() throws Exception {
        dados.recarregarDoZero();

        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL); // o de sempre continua entrando
        acesso.login("administrador@exemplo.org", ServicoDadosTeste.SENHA_TESTE);
        assertTrue(Sessao.pode(Acao.CRIAR_USUARIO));
        acesso.login("secretaria@exemplo.org", ServicoDadosTeste.SENHA_TESTE);
        assertTrue(Sessao.pode(Acao.CRIAR_INQUILINO));
        acesso.login("financeiro@exemplo.org", ServicoDadosTeste.SENHA_TESTE);
        assertFalse(Sessao.pode(Acao.CRIAR_IMOVEL));
        assertTrue(Sessao.pode(Acao.VER_IMOVEIS));
        acesso.login("aprendiz@exemplo.org", ServicoDadosTeste.SENHA_TESTE);
        assertFalse(Sessao.pode(Acao.CRIAR_INQUILINO));
        assertTrue(Sessao.pode(Acao.EDITAR_INQUILINO));
        assertNotNull(new UsuarioRepository().buscarPorEmail("aprendiz@exemplo.org").orElseThrow().getId());
    }

    @Test
    void cpfsSaoValidosENaoSeRepetem() {
        Set<String> cpfs = new HashSet<>();
        for (String[] c : ServicoDadosTeste.ler("inquilinos.csv")) {
            confereCpf(c[1], cpfs);
            if (!c[10].isBlank()) {
                confereCpf(c[10], cpfs);
            }
        }
        for (String[] c : ServicoDadosTeste.ler("fiadores.csv")) {
            confereCpf(c[2], cpfs);
            if (!c[7].isBlank()) {
                confereCpf(c[7], cpfs);
            }
        }
    }

    private static void confereCpf(String cpf, Set<String> vistos) {
        assertTrue(Cpf.ehValido(cpf), "CPF inválido: " + cpf);
        assertTrue(vistos.add(cpf.replaceAll("\\D", "")), "CPF repetido: " + cpf);
    }

    /** Os arquivos de contratos precisam bater com os de imóveis e inquilinos. */
    @Test
    void contratosBatemComImoveisEInquilinos() {
        Map<String, String[]> imoveis = new HashMap<>();
        ServicoDadosTeste.ler("imoveis.csv").forEach(c -> imoveis.put(c[0], c));
        Map<String, String[]> inquilinos = new HashMap<>();
        ServicoDadosTeste.ler("inquilinos.csv").forEach(c -> inquilinos.put(c[1], c));
        Set<String> imoveisComContrato = new HashSet<>();
        Set<String> numeros = new HashSet<>();
        List<String[]> contratos = ServicoDadosTeste.ler("contratos.csv");
        for (String[] c : contratos) {
            assertTrue(numeros.add(c[0]), "contrato repetido " + c[0]);
            String[] imovel = imoveis.get(c[1]);
            String[] inquilino = inquilinos.get(c[2]);
            assertNotNull(imovel, c[0] + ": imóvel não existe");
            assertNotNull(inquilino, c[0] + ": inquilino não existe");
            assertEquals("Ocupado", imovel[8], c[0] + ": imóvel com contrato precisa estar Ocupado");
            assertTrue(imoveisComContrato.add(c[1]), c[0] + ": imóvel com dois contratos");
            assertTrue(List.of("12", "24", "36").contains(c[4]), c[0] + ": prazo inválido");
            LocalDate chaves = LocalDate.parse(c[3]);
            assertEquals(chaves.plusMonths(Integer.parseInt(c[4])), LocalDate.parse(c[5]), c[0] + ": fim do contrato");
            BigDecimal familiar = Formatos.lerValor(inquilino[7]).add(Formatos.lerValor(inquilino[8]))
                    .add(Formatos.lerValor(inquilino[11]));
            BigDecimal minimo = Formatos.lerValor(imovel[5]).multiply(BigDecimal.valueOf(3));
            assertTrue(familiar.compareTo(minimo) >= 0, c[0] + ": renda familiar menor que 3x o aluguel");
        }
        long ocupados = imoveis.values().stream().filter(i -> i[8].equals("Ocupado")).count();
        assertEquals(ocupados, contratos.size(), "todo imóvel Ocupado tem contrato");

        Set<String> garantias = new HashSet<>();
        ServicoDadosTeste.ler("fiadores.csv").forEach(f -> assertTrue(numeros.contains(f[0]) && garantias.add(f[0])));
        ServicoDadosTeste.ler("caucoes.csv").forEach(c -> assertTrue(numeros.contains(c[0]) && garantias.add(c[0])));
        assertEquals(contratos.size(), garantias.size(), "todo contrato tem exatamente uma garantia");
    }
}
