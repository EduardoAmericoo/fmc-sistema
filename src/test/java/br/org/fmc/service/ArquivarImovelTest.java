package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ImovelRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Imóvel arquivado (ex.: vendido), excluído só sem histórico, e a situação "Ocupado" vinda do contrato. */
class ArquivarImovelTest {

    @TempDir
    Path pasta;

    private final ServicoImoveis servico = new ServicoImoveis();
    private final ImovelRepository repo = new ImovelRepository();

    @BeforeEach
    void bancoComAdministradorLogado() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Imovel novoImovel(String codigo, SituacaoImovel situacao) throws Exception {
        servico.salvarFormulario(null, codigo, "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "", "1.000,00",
                "", "", situacao);
        return repo.listarTodos().stream().filter(i -> i.getCodigo().equals(codigo)).findFirst().orElseThrow();
    }

    /** Cadastra uma inquilina e um contrato ativo no imóvel (que passa a Ocupado). */
    private void contratoAtivo(Imovel imovel) throws Exception {
        long inquilina = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "", "",
                EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel.getId(), inquilina, PrazoContrato.MESES_12,
                LocalDate.of(2026, 11, 1), null,
                new DadosContrato.Caucao("3.000,00", LocalDate.of(2026, 10, 30), br.org.fmc.model.FormaPagamento.PIX, "")));
    }

    @Test
    void imovelVendidoEArquivadoSomeDaListaEDosContratosMasFicaGuardado() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);

        servico.arquivar(imovel.getId(), "Vendido em outubro de 2026");

        Imovel lido = repo.buscarPorId(imovel.getId()).orElseThrow();
        assertTrue(lido.estaArquivado());
        assertEquals("Vendido em outubro de 2026", lido.getMotivoArquivamento());
        List<Imovel> todos = servico.listar();
        assertEquals(List.of(), ServicoImoveis.filtrar(todos, null, ""));
        assertEquals(List.of("IM-200"), ServicoImoveis.filtrar(todos, null, "", true).stream()
                .map(Imovel::getCodigo).toList());
        assertTrue(new ServicoContratos().imoveisVagos().isEmpty()); // não aparece para fazer contrato
    }

    @Test
    void reativarTrazDeVolta() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);
        servico.arquivar(imovel.getId(), "");

        servico.reativar(imovel.getId());

        assertFalse(repo.buscarPorId(imovel.getId()).orElseThrow().estaArquivado());
        assertEquals(1, new ServicoContratos().imoveisVagos().size());
    }

    @Test
    void naoArquivaImovelComContratoAtivo() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);
        contratoAtivo(imovel);

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.arquivar(imovel.getId(), ""));
        assertEquals(List.of("O imóvel IM-200 tem contrato ativo. Encerre o contrato antes de arquivar."), e.getErros());
    }

    @Test
    void imovelComHistoricoNaoSeExcluiSoSeArquiva() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);
        contratoAtivo(imovel);

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> servico.excluir(imovel.getId()));
        assertEquals(List.of("O imóvel IM-200 tem histórico de contrato e não pode ser excluído. "
                + "Para tirá-lo das listas, use Arquivar."), e.getErros());
    }

    @Test
    void cadastroFeitoPorEnganoPodeSerExcluidoMesmoArquivado() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);
        servico.arquivar(imovel.getId(), "Cadastrado duas vezes");

        servico.excluir(imovel.getId());

        assertTrue(repo.buscarPorId(imovel.getId()).isEmpty());
    }

    @Test
    void ocupadoSoPeloContrato() throws Exception {
        RegraNegocioException novo = assertThrows(RegraNegocioException.class,
                () -> novoImovel("IM-200", SituacaoImovel.OCUPADO));
        assertEquals(List.of("Só um contrato deixa o imóvel \"Ocupado\". Para ocupá-lo, cadastre o contrato na tela "
                + "Contratos."), novo.getErros());

        Imovel imovel = novoImovel("IM-201", SituacaoImovel.VAGO);
        contratoAtivo(imovel);
        RegraNegocioException desocupar = assertThrows(RegraNegocioException.class,
                () -> servico.salvarFormulario(imovel.getId(), "IM-201", "", TipoImovel.CASA, "Rua Exemplo, 1",
                        "Centro", "", "1.000,00", "", "", SituacaoImovel.VAGO));
        assertEquals(List.of("Este imóvel tem contrato ativo: a situação fica \"Ocupado\" até o contrato ser encerrado."),
                desocupar.getErros());
    }

    @Test
    void secretariaArquivaMasFinanceiroNao() throws Exception {
        Imovel imovel = novoImovel("IM-200", SituacaoImovel.VAGO);
        Sessao.iniciar(new Usuario("Consulta", "consulta@exemplo.org", Perfil.FINANCEIRO));
        assertThrows(AcessoNegadoException.class, () -> servico.arquivar(imovel.getId(), ""));

        Sessao.iniciar(new Usuario("Secretária", "secretaria@exemplo.org", Perfil.SECRETARIA));
        servico.arquivar(imovel.getId(), "");
        assertTrue(repo.buscarPorId(imovel.getId()).orElseThrow().estaArquivado());
    }
}
