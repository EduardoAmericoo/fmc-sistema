package br.org.fmc.service;

import br.org.fmc.model.*;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.util.ArrayList;
import br.org.fmc.repository.ImovelRepository;
import java.sql.SQLException;
import java.util.List;

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

    public void salvar(Imovel imovel) throws SQLException {
        Sessao.exigir(imovel.getId() == null ? Acao.CRIAR_IMOVEL : Acao.EDITAR_IMOVEL);
        if (repositorio.existeCodigo(imovel.getCodigo(), imovel.getId())) {
            throw new IllegalArgumentException("Já existe um imóvel com este código de identificação.");
        }
        if (imovel.getUnidade() == null || imovel.getUnidade().isBlank()) {
            throw new IllegalArgumentException("Informe a unidade do imóvel.");
        }
        if (repositorio.existeUnidade(imovel.getUnidade(), imovel.getId())) {
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
        salvarFormulario(id, codigo, unidade, tipo, endereco, bairro, grupo, aluguel, luz, iptu,
                situacao, null, null);
    }

    public void salvarFormulario(Long id, String codigo, String unidade, TipoImovel tipo, String endereco,
            String bairro, String grupo, String aluguel, String luz, String iptu,
            SituacaoImovel situacao, String inquilino, java.time.LocalDate contratoAte) throws SQLException {
        Sessao.exigir(id == null ? Acao.CRIAR_IMOVEL : Acao.EDITAR_IMOVEL);
        List<String> erros = new ArrayList<>();
        if (unidade == null || unidade.isBlank()) erros.add("Informe a unidade do imóvel.");
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
        imovel.setNomeInquilino(inquilino);
        imovel.setContratoAte(contratoAte);
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
