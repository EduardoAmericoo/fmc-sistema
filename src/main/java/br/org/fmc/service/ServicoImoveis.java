package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Apartamento;
import br.org.fmc.model.Casa;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.util.Formatos;
import br.org.fmc.util.TextoBusca;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro, edição, busca, arquivamento e exclusão de imóveis (BOARD-34 e BOARD-55).
 *
 * Regras:
 * - código obrigatório e sem repetir; unidade opcional, mas, se informada, sem repetir;
 * - a situação "Ocupado" vem do contrato: só o cadastro de um contrato ocupa o imóvel,
 *   e enquanto o contrato estiver ativo a situação não muda à mão;
 * - imóvel que sai do patrimônio (ex.: vendido) é ARQUIVADO: some das listas e dos formulários,
 *   mas fica guardado com o histórico e pode ser reativado;
 * - EXCLUIR de verdade só serve para cadastro feito por engano, que nunca teve contrato.
 */
public class ServicoImoveis {

    private final ImovelRepository repositorio = new ImovelRepository();
    private final ContratoRepository contratos = new ContratoRepository();

    /** Todos os imóveis, arquivados inclusive (a tela decide o que mostrar), em ordem de código. */
    public List<Imovel> listar() throws SQLException {
        Sessao.exigir(Acao.VER_IMOVEIS);
        return repositorio.listarTodos();
    }

    /** Filtra a lista da tela sem os arquivados. Ver o outro filtrar(). */
    public static List<Imovel> filtrar(List<Imovel> imoveis, SituacaoImovel situacao, String busca) {
        return filtrar(imoveis, situacao, busca, false);
    }

    /**
     * Filtra a lista da tela (BOARD-55). Situação nula = todas. A busca ignora
     * maiúsculas e acentos e procura no código, na unidade, no endereço e no bairro.
     * Arquivados só aparecem quando pedidos (e aí aparecem só eles).
     */
    public static List<Imovel> filtrar(List<Imovel> imoveis, SituacaoImovel situacao, String busca,
            boolean arquivados) {
        String termo = TextoBusca.normalizar(busca);
        return imoveis.stream()
                .filter(i -> i.estaArquivado() == arquivados)
                .filter(i -> situacao == null || i.getSituacao() == situacao)
                .filter(i -> termo.isEmpty() || TextoBusca.normalizar(i.getCodigo()).contains(termo)
                        || TextoBusca.normalizar(i.getUnidade()).contains(termo)
                        || TextoBusca.normalizar(i.getEndereco()).contains(termo)
                        || TextoBusca.normalizar(i.getBairro()).contains(termo))
                .toList();
    }

    /** Cadastra (id nulo) ou edita um imóvel, conferindo todos os campos de uma vez. */
    public void salvarFormulario(Long id, String codigo, String unidade, TipoImovel tipo, String endereco,
            String bairro, String grupo, String aluguel, String luz, String iptu,
            SituacaoImovel situacao) throws SQLException {
        Sessao.exigir(id == null ? Acao.CRIAR_IMOVEL : Acao.EDITAR_IMOVEL);
        List<String> erros = new ArrayList<>();
        if (codigo == null || codigo.isBlank()) {
            erros.add("Informe o código de identificação do imóvel.");
        } else if (repositorio.existeCodigo(codigo, id)) {
            erros.add("Já existe um imóvel com o código " + codigo.trim() + ".");
        }
        if (unidade != null && !unidade.isBlank() && repositorio.existeUnidade(unidade, id)) {
            erros.add("Já existe um imóvel com a unidade \"" + unidade.trim() + "\". Informe uma unidade diferente.");
        }
        if (tipo == null) {
            erros.add("Escolha o tipo do imóvel.");
        }
        if (endereco == null || endereco.isBlank()) {
            erros.add("Informe o endereço.");
        }
        if (situacao == null) {
            erros.add("Escolha a situação do imóvel.");
        } else {
            conferirSituacao(id, situacao, erros);
        }
        BigDecimal valorAluguel = lerDinheiro(aluguel, "aluguel", true, erros);
        BigDecimal taxaLuz = lerDinheiro(luz, "taxa de luz", false, erros);
        BigDecimal valorIptu = lerDinheiro(iptu, "IPTU", false, erros);
        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        Imovel imovel = switch (tipo) {
            case CASA -> new Casa(codigo, endereco, valorAluguel);
            case APARTAMENTO -> new Apartamento(codigo, endereco, valorAluguel);
            default -> new Imovel(codigo, tipo, endereco, valorAluguel);
        };
        imovel.setId(id);
        imovel.setUnidade(unidade);
        imovel.setBairro(bairro);
        imovel.setGrupo(grupo);
        imovel.setTaxaLuz(taxaLuz);
        imovel.setValorIptu(valorIptu);
        imovel.setSituacao(situacao);
        repositorio.salvar(imovel);
    }

    /** "Ocupado" só vem do contrato: não dá para ocupar à mão, nem desocupar com contrato ativo. */
    private void conferirSituacao(Long id, SituacaoImovel situacao, List<String> erros) throws SQLException {
        boolean temContratoAtivo = id != null && contratos.existeAtivoNoImovel(id);
        if (temContratoAtivo && situacao != SituacaoImovel.OCUPADO) {
            erros.add("Este imóvel tem contrato ativo: a situação fica \"Ocupado\" até o contrato ser encerrado.");
        } else if (!temContratoAtivo && situacao == SituacaoImovel.OCUPADO) {
            erros.add("Só um contrato deixa o imóvel \"Ocupado\". Para ocupá-lo, cadastre o contrato na tela Contratos.");
        }
    }

    /**
     * Arquiva o imóvel (ex.: vendido): some das listas e dos formulários, mas o histórico fica guardado.
     * Não dá para arquivar com contrato ativo.
     *
     * @param motivo opcional, ex.: "Vendido em outubro de 2026"
     */
    public Imovel arquivar(long id, String motivo) throws SQLException {
        Sessao.exigir(Acao.ARQUIVAR_IMOVEL);
        Imovel imovel = buscar(id);
        if (imovel.estaArquivado()) {
            throw new RegraNegocioException("O imóvel " + imovel.getCodigo() + " já está arquivado.");
        }
        if (contratos.existeAtivoNoImovel(id)) {
            throw new RegraNegocioException("O imóvel " + imovel.getCodigo()
                    + " tem contrato ativo. Encerre o contrato antes de arquivar.");
        }
        imovel.arquivar(LocalDate.now(), motivo);
        repositorio.salvarArquivamento(imovel);
        return imovel;
    }

    /** Traz um imóvel arquivado de volta para as listas do dia a dia. */
    public Imovel reativar(long id) throws SQLException {
        Sessao.exigir(Acao.ARQUIVAR_IMOVEL);
        Imovel imovel = buscar(id);
        imovel.reativar();
        repositorio.salvarArquivamento(imovel);
        return imovel;
    }

    /**
     * Apaga o imóvel de verdade. Só para cadastro feito por engano: se o imóvel já teve
     * contrato, o histórico precisa ficar, e o caminho é arquivar.
     */
    public void excluir(long id) throws SQLException {
        Sessao.exigir(Acao.EXCLUIR_IMOVEL);
        Imovel imovel = buscar(id);
        if (contratos.existeNoImovel(id)) {
            throw new RegraNegocioException("O imóvel " + imovel.getCodigo() + " tem histórico de contrato e não pode "
                    + "ser excluído. Para tirá-lo das listas, use Arquivar.");
        }
        repositorio.excluir(id);
    }

    private Imovel buscar(long id) throws SQLException {
        return repositorio.buscarPorId(id).orElseThrow(() -> new RegraNegocioException("Imóvel não encontrado."));
    }

    private static BigDecimal lerDinheiro(String texto, String campo, boolean obrigatorio, List<String> erros) {
        if (obrigatorio && (texto == null || texto.isBlank())) {
            erros.add("Informe o valor do " + campo + ".");
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal valor = Formatos.lerValor(texto);
            if (obrigatorio && valor.signum() <= 0) {
                erros.add("O valor do " + campo + " precisa ser maior que zero.");
            }
            return valor;
        } catch (IllegalArgumentException e) {
            erros.add("Valor do " + campo + " inválido: use só números, ex.: 1.500,00.");
            return BigDecimal.ZERO;
        }
    }
}
