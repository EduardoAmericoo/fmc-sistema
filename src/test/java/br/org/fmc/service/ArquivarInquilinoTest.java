package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
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
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.InquilinoRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Arquivar e reativar inquilino: quem não aluga mais sai da lista do dia a dia e do formulário
 * de contrato, mas o cadastro e o histórico ficam guardados. Dados fictícios.
 */
class ArquivarInquilinoTest {

    @TempDir
    Path pasta;

    private final ServicoInquilinos servico = new ServicoInquilinos();
    private final ServicoContratos contratos = new ServicoContratos();
    private final InquilinoRepository repo = new InquilinoRepository();

    private Imovel imovel;
    private Inquilino maria;

    @BeforeEach
    void bancoComUmImovelVagoEUmaInquilina() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        imovel = new ImovelRepository().listarTodos().get(0);
        maria = servico.cadastrar(dadosDaMaria());
        // "Hoje" no meio do contrato de teste, para poder encerrar com data de saída no passado.
        contratos.usarRelogio(Clock.fixed(LocalDate.of(2025, 7, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private static DadosInquilino dadosDaMaria() {
        return new DadosInquilino("Maria Teste", "529.982.247-25", "", "", EstadoCivil.SOLTEIRO, "", "",
                "3.000,00", "", "", "", "");
    }

    private Contrato contratoDaMaria() throws Exception {
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "5.000,00", "Rua do Fiador, 10", "", "");
        return contratos.cadastrar(new DadosContrato(imovel.getId(), maria.getId(), PrazoContrato.MESES_12,
                LocalDate.of(2025, 1, 10), fiador, null));
    }

    private List<String> nomes(List<Inquilino> lista) {
        return lista.stream().map(Inquilino::getNome).toList();
    }

    @Test
    void arquivadoSomeDaListaEDoFormularioDeContratoMasFicaGuardado() throws Exception {
        servico.arquivar(maria.getId(), "Mudou de cidade");

        Inquilino lido = repo.buscarPorId(maria.getId()).orElseThrow();
        assertTrue(lido.estaArquivado());
        assertEquals("Mudou de cidade", lido.getMotivoArquivamento());
        assertEquals(LocalDate.now(), lido.getArquivadoEm());

        List<Inquilino> todos = servico.listar();
        assertEquals(List.of(), ServicoInquilinos.filtrar(todos, ""));
        assertEquals(List.of("Maria Teste"), nomes(ServicoInquilinos.filtrar(todos, "", true)));
        assertEquals(List.of("Maria Teste"), nomes(ServicoInquilinos.filtrar(todos, "maria", true)));
        assertTrue(contratos.inquilinosSemContrato().isEmpty()); // não aparece para fazer contrato
    }

    @Test
    void naoArquivaQuemTemContratoAtivo() throws Exception {
        contratoDaMaria();

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.arquivar(maria.getId(), ""));
        assertEquals(List.of("Maria Teste tem contrato ativo. Encerre o contrato na tela Contratos antes de arquivar."),
                e.getErros());
        assertFalse(repo.buscarPorId(maria.getId()).orElseThrow().estaArquivado());
    }

    @Test
    void depoisDeEncerrarOContratoArquivaEOHistoricoContinuaComONome() throws Exception {
        Contrato contrato = contratoDaMaria();
        contratos.encerrar(contrato.getId(), LocalDate.of(2025, 6, 20), MotivoEncerramento.SAIDA_DO_INQUILINO, "");

        servico.arquivar(maria.getId(), "");

        Contrato lido = new ContratoRepository().buscarPorId(contrato.getId()).orElseThrow();
        assertEquals("Maria Teste", lido.getInquilino().getNome());
        assertTrue(lido.getInquilino().estaArquivado());
        assertEquals(null, repo.buscarPorId(maria.getId()).orElseThrow().getMotivoArquivamento()); // motivo é opcional
    }

    @Test
    void reativarTrazDeVoltaParaAListaEParaOContrato() throws Exception {
        servico.arquivar(maria.getId(), "Mudou de cidade");

        servico.reativar(maria.getId());

        Inquilino lido = repo.buscarPorId(maria.getId()).orElseThrow();
        assertFalse(lido.estaArquivado());
        assertEquals(null, lido.getMotivoArquivamento());
        assertEquals(List.of("Maria Teste"), nomes(ServicoInquilinos.filtrar(servico.listar(), "")));
        assertEquals(List.of("Maria Teste"), nomes(contratos.inquilinosSemContrato()));
    }

    @Test
    void naoFazContratoComInquilinoArquivado() throws Exception {
        servico.arquivar(maria.getId(), "");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, this::contratoDaMaria);
        assertTrue(e.getErros().contains(
                "Maria Teste está arquivado: reative o cadastro na tela Inquilinos antes de fazer contrato."),
                e.getErros().toString());
    }

    @Test
    void cpfDeInquilinoArquivadoAvisaParaReativar() throws Exception {
        servico.arquivar(maria.getId(), "");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.cadastrar(dadosDaMaria()));
        assertEquals(List.of("Este CPF já está cadastrado para Maria Teste, que está arquivado. "
                + "Marque \"Ver arquivados\" e reative o cadastro."), e.getErros());
    }

    @Test
    void naoArquivaDuasVezes() throws Exception {
        servico.arquivar(maria.getId(), "");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.arquivar(maria.getId(), ""));
        assertEquals(List.of("Maria Teste já está arquivado."), e.getErros());
    }

    @Test
    void soQuemTemAPermissaoArquivaEReativa() throws Exception {
        Sessao.iniciar(new Usuario("Consulta", "financeiro@exemplo.org", Perfil.FINANCEIRO));

        assertThrows(AcessoNegadoException.class, () -> servico.arquivar(maria.getId(), ""));
        assertThrows(AcessoNegadoException.class, () -> servico.reativar(maria.getId()));
        assertTrue(Perfil.SECRETARIA.permite(br.org.fmc.model.Acao.ARQUIVAR_INQUILINO));
    }
}
