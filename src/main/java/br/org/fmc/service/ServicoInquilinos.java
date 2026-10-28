package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Inquilino;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cadastro e edição de inquilinos com os dados da proposta (BOARD-35).
 *
 * Regras: CPF válido pelos dígitos verificadores, sem dois inquilinos com o
 * mesmo CPF. Criar, editar e ver dependem cada um da sua permissão (BOARD-41).
 *
 * Inquilino que não aluga mais é ARQUIVADO, nunca apagado: o histórico de contratos,
 * aluguéis e recibos precisa continuar com o nome dele. Dá para reativar.
 */
public class ServicoInquilinos {

    private final InquilinoRepository inquilinos;
    private final ContratoRepository contratos = new ContratoRepository();

    public ServicoInquilinos() {
        this(new InquilinoRepository());
    }

    public ServicoInquilinos(InquilinoRepository inquilinos) {
        this.inquilinos = inquilinos;
    }

    /**
     * Cadastra um inquilino novo.
     * Junta todos os problemas encontrados numa única RegraNegocioException,
     * para a tela mostrar tudo de uma vez.
     *
     * @return o inquilino cadastrado, já com id
     */
    public Inquilino cadastrar(DadosInquilino dados) throws SQLException {
        Sessao.exigir(Acao.CRIAR_INQUILINO);
        return salvar(null, dados);
    }

    /** Altera os dados de um inquilino já cadastrado. Mesmas regras do cadastro. */
    public Inquilino editar(long id, DadosInquilino dados) throws SQLException {
        Sessao.exigir(Acao.EDITAR_INQUILINO);
        if (inquilinos.buscarPorId(id).isEmpty()) {
            throw new RegraNegocioException("Inquilino não encontrado.");
        }
        return salvar(id, dados);
    }

    /** Todos os inquilinos, em ordem de nome. Qualquer perfil que veja os cadastros. */
    public List<Inquilino> listar() throws SQLException {
        Sessao.exigir(Acao.VER_INQUILINOS);
        return inquilinos.listarTodos();
    }

    /**
     * Inquilinos que atendem à busca, por nome ou CPF (BOARD-56), em ordem de nome.
     * Busca vazia devolve todos. Ver Inquilino.atendeBusca() para as regras.
     */
    public List<Inquilino> buscar(String busca) throws SQLException {
        return filtrar(listar(), busca);
    }

    /**
     * Filtra uma lista já carregada, sem ir ao banco: a tela usa isto para filtrar
     * enquanto a pessoa digita. Deixa de fora os arquivados.
     */
    public static List<Inquilino> filtrar(List<Inquilino> inquilinos, String busca) {
        return filtrar(inquilinos, busca, false);
    }

    /** Filtra pela busca e mostra só os arquivados (arquivados = true) ou só os que não estão. */
    public static List<Inquilino> filtrar(List<Inquilino> inquilinos, String busca, boolean arquivados) {
        return inquilinos.stream().filter(i -> i.estaArquivado() == arquivados && i.atendeBusca(busca)).toList();
    }

    /**
     * Arquiva o inquilino (ex.: saiu do imóvel e não vai alugar de novo): some da lista do dia a dia
     * e do formulário de contrato, mas o cadastro e o histórico ficam guardados.
     * Não dá para arquivar quem tem contrato ativo.
     *
     * @param motivo opcional, ex.: "Mudou de cidade"
     */
    public Inquilino arquivar(long id, String motivo) throws SQLException {
        Sessao.exigir(Acao.ARQUIVAR_INQUILINO);
        Inquilino inquilino = buscarPorId(id);
        if (inquilino.estaArquivado()) {
            throw new RegraNegocioException(inquilino.getNome() + " já está arquivado.");
        }
        if (contratos.existeAtivoDoInquilino(id)) {
            throw new RegraNegocioException(inquilino.getNome()
                    + " tem contrato ativo. Encerre o contrato na tela Contratos antes de arquivar.");
        }
        inquilino.arquivar(LocalDate.now(), motivo);
        inquilinos.salvarArquivamento(inquilino);
        return inquilino;
    }

    /** Traz um inquilino arquivado de volta para a lista do dia a dia (ex.: vai alugar de novo). */
    public Inquilino reativar(long id) throws SQLException {
        Sessao.exigir(Acao.ARQUIVAR_INQUILINO);
        Inquilino inquilino = buscarPorId(id);
        inquilino.reativar();
        inquilinos.salvarArquivamento(inquilino);
        return inquilino;
    }

    private Inquilino buscarPorId(long id) throws SQLException {
        return inquilinos.buscarPorId(id).orElseThrow(() -> new RegraNegocioException("Inquilino não encontrado."));
    }

    private Inquilino salvar(Long id, DadosInquilino d) throws SQLException {
        List<String> erros = new ArrayList<>();

        if (d.nome() == null || d.nome().isBlank()) {
            erros.add("Informe o nome completo.");
        }
        Cpf cpf = null;
        if (d.cpf() == null || d.cpf().isBlank()) {
            erros.add("Informe o CPF.");
        } else if (!Cpf.ehValido(d.cpf())) {
            erros.add(Cpf.problema(d.cpf()));
        } else {
            cpf = new Cpf(d.cpf());
            Optional<Inquilino> mesmoCpf = inquilinos.buscarPorCpf(cpf);
            if (mesmoCpf.isPresent() && !mesmoCpf.get().getId().equals(id)) {
                erros.add("Este CPF já está cadastrado para " + mesmoCpf.get().getNome()
                        + (mesmoCpf.get().estaArquivado()
                                ? ", que está arquivado. Marque \"Ver arquivados\" e reative o cadastro."
                                : ". Para mudar os dados, clique no nome na lista."));
            }
        }
        if (d.email() != null && !d.email().isBlank() && !d.email().trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            erros.add("Informe um e-mail válido.");
        }
        if (d.estadoCivil() == null) {
            erros.add("Escolha o estado civil.");
        }
        BigDecimal renda = lerValor(d.rendaMensal(), "Renda mensal inválida", erros);
        BigDecimal outras = lerValor(d.outrasRendas(), "Outras rendas inválidas", erros);

        Conjuge conjuge = null;
        if (d.estadoCivil() != null && d.estadoCivil().temConjuge() && conjugeInformado(d)) {
            conjuge = montarConjuge(d, erros);
        }

        if (!erros.isEmpty()) {
            throw new RegraNegocioException(erros);
        }

        Inquilino inquilino = new Inquilino(d.nome(), cpf);
        inquilino.setId(id);
        inquilino.setTelefone(d.telefone());
        inquilino.setEmail(d.email());
        inquilino.setEstadoCivil(d.estadoCivil());
        inquilino.setProfissao(d.profissao());
        inquilino.setLocalTrabalho(d.localTrabalho());
        inquilino.setRendaMensal(renda);
        inquilino.setOutrasRendas(outras);
        inquilino.setConjuge(conjuge);
        inquilinos.salvar(inquilino);
        return inquilino;
    }

    private static boolean conjugeInformado(DadosInquilino d) {
        return preenchido(d.conjugeNome()) || preenchido(d.conjugeCpf()) || preenchido(d.conjugeRenda());
    }

    /** Monta o cônjuge; se algo estiver errado, anota em erros e devolve null. */
    private static Conjuge montarConjuge(DadosInquilino d, List<String> erros) {
        int errosAntes = erros.size();
        if (!preenchido(d.conjugeNome())) {
            erros.add("Informe o nome do cônjuge.");
        }
        // O CPF do cônjuge não é obrigatório, mas, se for digitado, precisa ser válido.
        if (preenchido(d.conjugeCpf()) && !Cpf.ehValido(d.conjugeCpf())) {
            erros.add("Cônjuge: " + Cpf.problema(d.conjugeCpf()));
        }
        BigDecimal renda = lerValor(d.conjugeRenda(), "Renda do cônjuge inválida", erros);
        if (erros.size() > errosAntes) {
            return null;
        }
        Conjuge c = new Conjuge(d.conjugeNome());
        c.setCpf(preenchido(d.conjugeCpf()) ? new Cpf(d.conjugeCpf()) : null);
        c.setRenda(renda);
        return c;
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
