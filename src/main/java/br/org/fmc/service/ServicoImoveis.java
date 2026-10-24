package br.org.fmc.service;

import br.org.fmc.model.*;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.util.ArrayList;
import br.org.fmc.repository.ImovelRepository;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public class ServicoImoveis {
    private final ImovelRepository repositorio = new ImovelRepository();

    public void excluir(long id) throws SQLException {
        Sessao.exigir(Acao.EXCLUIR_IMOVEL);
        repositorio.excluir(id);
    }

    public List<Imovel> listar() throws SQLException {
        Sessao.exigir(Acao.VER_IMOVEIS);
        return repositorio.listarTodos();
    }

    /**
     * Filtra a lista da tela (BOARD-55). Situação nula = todas. A busca ignora
     * maiúsculas e acentos e procura no código, na unidade, no endereço e no bairro.
     */
    public static List<Imovel> filtrar(List<Imovel> imoveis, SituacaoImovel situacao, String busca) {
        String termo = normalizar(busca);
        return imoveis.stream()
                .filter(i -> situacao == null || i.getSituacao() == situacao)
                .filter(i -> termo.isEmpty() || normalizar(i.getCodigo()).contains(termo)
                        || normalizar(i.getUnidade()).contains(termo)
                        || normalizar(i.getEndereco()).contains(termo)
                        || normalizar(i.getBairro()).contains(termo))
                .toList();
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public void salvar(Imovel imovel) throws SQLException {
        Sessao.exigir(imovel.getId() == null ? Acao.CRIAR_IMOVEL : Acao.EDITAR_IMOVEL);
        if (repositorio.existeCodigo(imovel.getCodigo(), imovel.getId())) {
            throw new IllegalArgumentException("Já existe um imóvel com este código de identificação.");
        }
        // A unidade é opcional; se for informada, não pode se repetir.
        if (imovel.getUnidade() != null && repositorio.existeUnidade(imovel.getUnidade(), imovel.getId())) {
            throw new IllegalArgumentException("Já existe um imóvel cadastrado com esta unidade. Informe uma unidade diferente.");
        }
        try {
            repositorio.salvar(imovel);
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) {
                throw new IllegalArgumentException("Já existe um imóvel com este código.", e);
            }
            throw e;
        }
    }

    /** Valida os campos antes de construir ou gravar o imóvel. */
    public void salvarFormulario(Long id, String codigo, String unidade, TipoImovel tipo, String endereco,
            String bairro, String grupo, String aluguel, String luz, String iptu,
            SituacaoImovel situacao) throws SQLException {
        Sessao.exigir(id == null ? Acao.CRIAR_IMOVEL : Acao.EDITAR_IMOVEL);
        List<String> erros = new ArrayList<>();
        if (codigo == null || codigo.isBlank()) erros.add("Informe o código de identificação do imóvel.");
        if (tipo == null) erros.add("Selecione o tipo do imóvel.");
        if (endereco == null || endereco.isBlank()) erros.add("Informe o endereço.");
        if (situacao == null) erros.add("Selecione a situação do imóvel.");
        BigDecimal valorAluguel = lerDinheiro(aluguel, "aluguel", true, erros);
        BigDecimal taxaLuz = lerDinheiro(luz, "taxa de luz", false, erros);
        BigDecimal valorIptu = lerDinheiro(iptu, "IPTU", false, erros);
        if (!erros.isEmpty()) throw new IllegalArgumentException(String.join("\n", erros));
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
        salvar(imovel);
    }

    private BigDecimal lerDinheiro(String texto, String campo, boolean obrigatorio, List<String> erros) {
        if (obrigatorio && (texto == null || texto.isBlank())) {
            erros.add("Informe o valor do " + campo + ".");
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal valor = Formatos.lerValor(texto);
            if (obrigatorio && valor.signum() <= 0) erros.add("O valor do " + campo + " deve ser maior que zero.");
            return valor;
        } catch (IllegalArgumentException e) {
            erros.add("Informe um valor válido e não negativo para " + campo + ".");
            return BigDecimal.ZERO;
        }
    }
}
