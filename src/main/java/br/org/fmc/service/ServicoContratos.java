package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.Garantia;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cadastro de contrato ativo (BOARD-36), com a garantia (BOARD-57) e a regra de renda (BOARD-58).
 *
 * Regras:
 * - só imóvel vago, sem outro contrato ativo, pode receber contrato;
 * - o inquilino não pode ter outro contrato ativo;
 * - a renda familiar do inquilino precisa ser pelo menos 3 vezes o aluguel (bloqueia o cadastro);
 * - toda locação tem garantia: fiador (CPF válido, diferente do inquilino; se casado, com cônjuge)
 *   ou caução (pelo menos 3 aluguéis, recebida até a entrega das chaves).
 * Ao cadastrar, o imóvel passa a "Ocupado".
 */
public class ServicoContratos {

    private final ContratoRepository contratos = new ContratoRepository();
    private final ImovelRepository imoveis = new ImovelRepository();
    private final InquilinoRepository inquilinos = new InquilinoRepository();

    /**
     * Cadastra um contrato novo, já ativo.
     * Junta todos os problemas numa única RegraNegocioException, para a tela mostrar tudo de uma vez.
     *
     * @return o contrato cadastrado, já com id e número (ex.: CT-1001)
     */
    public Contrato cadastrar(DadosContrato d) throws SQLException {
        Sessao.exigir(Acao.CRIAR_CONTRATO);
        List<String> erros = new ArrayList<>();

        Imovel imovel = buscarImovel(d.imovelId(), erros);
        Inquilino inquilino = buscarInquilino(d.inquilinoId(), erros);
        if (d.prazo() == null) {
            erros.add("Escolha o prazo do contrato.");
        }
        if (d.entregaChaves() == null) {
            erros.add("Informe a data de entrega das chaves.");
        }
        Garantia garantia = montarGarantia(d, inquilino, erros);

        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        Contrato contrato = new Contrato(imovel, inquilino, garantia, d.prazo(), d.entregaChaves());
        List<String> regras = contrato.validar(); // renda 3x e garantia
        if (!regras.isEmpty()) {
            throw new RegraNegocioException(regras);
        }
        contratos.salvar(contrato); // grava contrato + garantia e ocupa o imóvel, tudo junto
        return contrato;
    }

    /** Todos os contratos, em ordem de número. */
    public List<Contrato> listar() throws SQLException {
        Sessao.exigir(Acao.VER_CONTRATOS);
        return contratos.listarTodos();
    }

    /** Imóveis que podem receber contrato agora (vagos), para escolher no formulário. */
    public List<Imovel> imoveisVagos() throws SQLException {
        Sessao.exigir(Acao.CRIAR_CONTRATO);
        return imoveis.listarTodos().stream().filter(Imovel::estaDisponivel).toList();
    }

    /** Inquilinos sem contrato ativo, para escolher no formulário. */
    public List<Inquilino> inquilinosSemContrato() throws SQLException {
        Sessao.exigir(Acao.CRIAR_CONTRATO);
        Set<Long> comContrato = contratos.listarTodos().stream()
                .filter(c -> c.getStatus() == StatusContrato.ATIVO)
                .map(c -> c.getInquilino().getId())
                .collect(Collectors.toSet());
        return inquilinos.listarTodos().stream().filter(i -> !comContrato.contains(i.getId())).toList();
    }

    private Imovel buscarImovel(Long id, List<String> erros) throws SQLException {
        if (id == null) {
            erros.add("Escolha o imóvel.");
            return null;
        }
        Imovel imovel = imoveis.buscarPorId(id).orElse(null);
        if (imovel == null) {
            erros.add("Imóvel não encontrado.");
        } else if (imovel.estaArquivado()) {
            erros.add("O imóvel " + imovel.getCodigo() + " está arquivado: reative-o na tela Imóveis antes de fazer contrato.");
        } else if (imovel.getSituacao() != SituacaoImovel.VAGO) {
            erros.add("O imóvel " + imovel.getCodigo() + " está " + imovel.getSituacao().getDescricao().toLowerCase()
                    + ": só imóvel vago pode receber contrato.");
        } else if (contratos.existeAtivoNoImovel(id)) {
            erros.add("O imóvel " + imovel.getCodigo() + " já tem um contrato ativo.");
        }
        return imovel;
    }

    private Inquilino buscarInquilino(Long id, List<String> erros) throws SQLException {
        if (id == null) {
            erros.add("Escolha o inquilino.");
            return null;
        }
        Inquilino inquilino = inquilinos.buscarPorId(id).orElse(null);
        if (inquilino == null) {
            erros.add("Inquilino não encontrado.");
        } else if (contratos.existeAtivoDoInquilino(id)) {
            erros.add(inquilino.getNome() + " já tem um contrato ativo.");
        }
        return inquilino;
    }

    /** Monta a garantia escolhida; se algo estiver errado, anota em erros e devolve null. */
    private static Garantia montarGarantia(DadosContrato d, Inquilino inquilino, List<String> erros) {
        if (d.fiador() == null && d.caucao() == null) {
            erros.add("Escolha a garantia: fiador ou caução.");
            return null;
        }
        if (d.fiador() != null && d.caucao() != null) {
            erros.add("Escolha só uma garantia: fiador ou caução.");
            return null;
        }
        return d.fiador() != null ? montarFianca(d.fiador(), inquilino, erros) : montarCaucao(d.caucao(), erros);
    }

    private static Fianca montarFianca(DadosContrato.Fiador f, Inquilino inquilino, List<String> erros) {
        int errosAntes = erros.size();
        if (!preenchido(f.nome())) {
            erros.add("Informe o nome do fiador.");
        }
        if (!preenchido(f.cpf())) {
            erros.add("Informe o CPF do fiador.");
        } else if (!Cpf.ehValido(f.cpf())) {
            erros.add("Fiador: " + Cpf.problema(f.cpf()));
        } else if (inquilino != null && inquilino.getCpf().equals(new Cpf(f.cpf()))) {
            erros.add("O fiador não pode ser o próprio inquilino.");
        }
        if (f.estadoCivil() == null) {
            erros.add("Escolha o estado civil do fiador.");
        }
        BigDecimal renda = lerValor(f.renda(), "Renda do fiador inválida", erros);
        boolean temConjuge = f.estadoCivil() != null && f.estadoCivil().temConjuge();
        if (temConjuge && preenchido(f.conjugeCpf()) && !Cpf.ehValido(f.conjugeCpf())) {
            erros.add("Cônjuge do fiador: " + Cpf.problema(f.conjugeCpf()));
        }
        if (erros.size() > errosAntes) {
            return null;
        }

        Fiador fiador = new Fiador(f.nome(), new Cpf(f.cpf()));
        fiador.setTelefone(f.telefone());
        fiador.setEstadoCivil(f.estadoCivil());
        fiador.setRendaMensal(renda);
        fiador.setEndereco(f.endereco());
        // Sem o nome do cônjuge, a Fianca avisa "Fiador casado: informe também o cônjuge."
        if (temConjuge && preenchido(f.conjugeNome())) {
            Conjuge conjuge = new Conjuge(f.conjugeNome());
            conjuge.setCpf(preenchido(f.conjugeCpf()) ? new Cpf(f.conjugeCpf()) : null);
            fiador.setConjuge(conjuge);
        }
        return new Fianca(fiador);
    }

    private static Caucao montarCaucao(DadosContrato.Caucao c, List<String> erros) {
        int errosAntes = erros.size();
        BigDecimal valor = lerValor(c.valor(), "Valor da caução inválido", erros);
        if (erros.size() == errosAntes && valor.signum() <= 0) {
            erros.add("Informe o valor da caução.");
        }
        if (c.recebidaEm() == null) {
            erros.add("Informe a data em que a caução foi recebida.");
        }
        if (c.forma() == null) {
            erros.add("Escolha a forma de pagamento da caução.");
        }
        if (erros.size() > errosAntes) {
            return null;
        }
        Caucao caucao = new Caucao(valor, c.recebidaEm(), c.forma());
        caucao.setNumeroRecibo(c.recibo());
        return caucao;
    }

    private static BigDecimal lerValor(String texto, String erro, List<String> erros) {
        try {
            return Formatos.lerValor(texto);
        } catch (IllegalArgumentException e) {
            erros.add(erro + ": use só números, ex.: 2.500,00.");
            return BigDecimal.ZERO;
        }
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
