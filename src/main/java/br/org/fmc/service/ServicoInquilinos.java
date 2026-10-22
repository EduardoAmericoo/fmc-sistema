package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Inquilino;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cadastro e edição de inquilinos com os dados da proposta (BOARD-35).
 *
 * Regras: CPF válido pelos dígitos verificadores, sem dois inquilinos com o
 * mesmo CPF, e só quem pode cadastrar inquilinos (Administrador e Secretaria).
 */
public class ServicoInquilinos {

    private final InquilinoRepository inquilinos;

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
        Sessao.exigir(Acao.CADASTRAR_INQUILINO);
        return salvar(null, dados);
    }

    /** Altera os dados de um inquilino já cadastrado. Mesmas regras do cadastro. */
    public Inquilino editar(long id, DadosInquilino dados) throws SQLException {
        Sessao.exigir(Acao.CADASTRAR_INQUILINO);
        if (inquilinos.buscarPorId(id).isEmpty()) {
            throw new RegraNegocioException("Inquilino não encontrado.");
        }
        return salvar(id, dados);
    }

    /** Todos os inquilinos, em ordem de nome. Qualquer perfil que veja os cadastros. */
    public List<Inquilino> listar() throws SQLException {
        Sessao.exigir(Acao.VER_CADASTROS);
        return inquilinos.listarTodos();
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
                        + ". Para mudar os dados, clique no nome na lista.");
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
