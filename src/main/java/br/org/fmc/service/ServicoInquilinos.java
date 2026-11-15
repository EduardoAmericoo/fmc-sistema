package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.RegrasNegocio;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContatoCobrancaRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final AluguelRepository alugueis = new AluguelRepository();
    private final ContatoCobrancaRepository contatos = new ContatoCobrancaRepository();

    /** Relógio do sistema; os testes trocam por um relógio parado (a ficha depende de "hoje"). */
    private Clock relogio = Clock.systemDefaultZone();

    public ServicoInquilinos() {
        this(new InquilinoRepository());
    }

    public ServicoInquilinos(InquilinoRepository inquilinos) {
        this.inquilinos = inquilinos;
    }

    /** Só para os testes: faz o serviço usar outro relógio. */
    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    // ---------- [D8] Ficha do inquilino (BOARD-45) ----------

    /** Como foi (ou está) cada aluguel, para a faixa colorida mês a mês da ficha. */
    public enum Pagamento {
        PAGO_EM_DIA("Pago em dia"),
        PAGO_COM_ATRASO("Pago com atraso"),
        EM_ATRASO("Em atraso"),
        EM_TOLERANCIA("Em tolerância"),
        A_VENCER("A vencer");

        private final String descricao;

        Pagamento(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        /** Pago com atraso = pago depois dos dias de tolerância (aí houve multa e juros). */
        public static Pagamento de(Aluguel a, LocalDate hoje) {
            if (a.estaPago()) {
                return a.diasAtraso(a.getDataPagamento()) > RegrasNegocio.TOLERANCIA_DIAS
                        ? PAGO_COM_ATRASO : PAGO_EM_DIA;
            }
            return switch (a.getSituacao(hoje)) {
                case EM_ATRASO -> EM_ATRASO;
                case EM_TOLERANCIA -> EM_TOLERANCIA;
                default -> A_VENCER;
            };
        }
    }

    /**
     * Tudo de um inquilino num lugar só: o cadastro, os contratos (o mais recente primeiro)
     * e os aluguéis desses contratos (o mais recente primeiro). Contratos e aluguéis só vêm
     * para quem pode ver essas telas; para os outros, as listas vêm vazias.
     */
    public record Ficha(Inquilino inquilino, List<Contrato> contratos, List<Aluguel> alugueis,
            List<ContatoCobranca> contatos, LocalDate hoje) {

        /** O contrato que aparece em destaque: o ativo, ou o mais recente se não houver ativo. */
        public Contrato contratoAtual() {
            return contratos.stream().filter(c -> c.getStatus() == StatusContrato.ATIVO).findFirst()
                    .orElse(contratos.isEmpty() ? null : contratos.get(0));
        }

        /** Soma do que já foi pago (valor do aluguel, sem encargos). */
        public BigDecimal totalPago() {
            return alugueis.stream().filter(Aluguel::estaPago).map(Aluguel::base)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** Quanto deve hoje, com multa, juros e correção, dos aluguéis em atraso. */
        public BigDecimal emAtrasoHoje() {
            return alugueis.stream().filter(a -> Pagamento.de(a, hoje) == Pagamento.EM_ATRASO)
                    .map(a -> a.base().add(a.encargosEm(hoje).total()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public long pagos() {
            return alugueis.stream().filter(Aluguel::estaPago).count();
        }

        public long pagosComAtraso() {
            return alugueis.stream().filter(a -> Pagamento.de(a, hoje) == Pagamento.PAGO_COM_ATRASO).count();
        }

        public long emAtraso() {
            return alugueis.stream().filter(a -> Pagamento.de(a, hoje) == Pagamento.EM_ATRASO).count();
        }

        /** Desde quando aluga com a Fundação: a entrega das chaves do primeiro contrato. */
        public LocalDate inquilinoDesde() {
            return contratos.stream().map(Contrato::getDataEntregaChaves).min(Comparator.naturalOrder()).orElse(null);
        }

        /**
         * Melhoria da SR2 · Como o inquilino costuma pagar, numa frase, para o topo da ficha.
         * Ajuda a decidir o tom da cobrança: bom pagador num mês ruim ou atraso de sempre.
         * O nível olha o HISTÓRICO, para não confundir um bom pagador num mês ruim com um mau pagador:
         * Bom: 90% ou mais pagos em dia. Alguns atrasos: de 70% a 89%.
         * Atenção: menos de 70% em dia, ou 3 ou mais aluguéis em atraso hoje, ou nenhum pago e 2 ou mais em atraso.
         */
        public Resumo resumo() {
            long pagos = pagos();
            long emDia = pagos - pagosComAtraso();
            long devendo = emAtraso();
            List<String> partes = new ArrayList<>();
            LocalDate desde = inquilinoDesde();
            if (desde != null) {
                partes.add("inquilino desde " + desde.format(MES_ANO) + tempo(desde));
            }
            if (pagos > 0) {
                partes.add(emDia + " de " + pagos + (pagos == 1 ? " aluguel pago" : " aluguéis pagos")
                        + " em dia (" + (emDia * 100 / pagos) + "%)");
            }
            if (devendo > 0) {
                partes.add("hoje deve " + devendo + (devendo == 1 ? " aluguel" : " aluguéis"));
            }
            Nivel nivel;
            if (devendo >= ATRASADOS_ATENCAO || (pagos == 0 && devendo >= 2)
                    || (pagos > 0 && emDia * 100 < pagos * PERCENTUAL_ATENCAO)) {
                nivel = Nivel.ATENCAO;
            } else if (pagos == 0) {
                nivel = Nivel.SEM_HISTORICO;
            } else if (emDia * 100 >= pagos * PERCENTUAL_BOM) {
                nivel = Nivel.BOM;
            } else {
                nivel = Nivel.REGULAR;
            }
            String texto = String.join(" · ", partes);
            return new Resumo(nivel, nivel.getDescricao() + (texto.isEmpty() ? "" : ": " + texto) + ".");
        }

        /** " (2 anos e 3 meses)", " (5 meses)" ou "" se começou neste mês. */
        private String tempo(LocalDate desde) {
            Period p = Period.between(desde, hoje);
            List<String> partes = new ArrayList<>();
            if (p.getYears() > 0) {
                partes.add(p.getYears() + (p.getYears() == 1 ? " ano" : " anos"));
            }
            if (p.getMonths() > 0) {
                partes.add(p.getMonths() + (p.getMonths() == 1 ? " mês" : " meses"));
            }
            return partes.isEmpty() ? "" : " (" + String.join(" e ", partes) + ")";
        }
    }

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");
    /** Abaixo disto (em % dos aluguéis pagos em dia), a ficha pede atenção. */
    static final int PERCENTUAL_ATENCAO = 70;
    /** A partir disto (em % pagos em dia), é bom pagador. */
    static final int PERCENTUAL_BOM = 90;
    /** Com tantos aluguéis em atraso hoje, a ficha pede atenção, mesmo com bom histórico. */
    static final int ATRASADOS_ATENCAO = 3;

    /** Como o inquilino costuma pagar (ver Ficha.resumo). */
    public enum Nivel {
        BOM("Bom pagador"),
        REGULAR("Paga, mas com alguns atrasos"),
        ATENCAO("Atenção, atrasa com frequência"),
        SEM_HISTORICO("Ainda sem pagamentos");

        private final String descricao;

        Nivel(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    public record Resumo(Nivel nivel, String texto) {
    }

    /** Monta a ficha do inquilino ([D8] BOARD-45). Só leitura: não grava nada. */
    public Ficha ficha(long id) throws SQLException {
        Sessao.exigir(Acao.VER_INQUILINOS);
        Inquilino inquilino = buscarPorId(id);
        List<Contrato> doInquilino = !Sessao.pode(Acao.VER_CONTRATOS) ? List.of()
                : contratos.listarTodos().stream()
                        .filter(c -> c.getInquilino().getId().equals(id))
                        .sorted(Comparator.comparing(Contrato::getDataEntregaChaves).reversed())
                        .toList();
        List<Aluguel> lista = new ArrayList<>();
        if (Sessao.pode(Acao.VER_ALUGUEIS)) {
            for (Contrato c : doInquilino) {
                lista.addAll(alugueis.listarDoContrato(c));
            }
            lista.sort(Comparator.comparing(Aluguel::getVencimento).reversed());
        }
        // [D7] BOARD-64: os contatos de cobrança de todos os contratos, do mais recente para o mais antigo.
        List<ContatoCobranca> contatosDoInquilino = new ArrayList<>();
        if (Sessao.pode(Acao.VER_ALUGUEIS)) {
            for (Contrato c : doInquilino) {
                contatosDoInquilino.addAll(contatos.listarDoContrato(c.getId()));
            }
            contatosDoInquilino.sort(Comparator.comparing(ContatoCobranca::registradoEm).reversed());
        }
        return new Ficha(inquilino, doInquilino, lista, contatosDoInquilino, LocalDate.now(relogio));
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
