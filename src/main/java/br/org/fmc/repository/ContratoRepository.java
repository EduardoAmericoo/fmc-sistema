package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Caucao;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.Encerramento;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Fianca;
import br.org.fmc.model.Fiador;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Garantia;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.Prorrogacao;
import br.org.fmc.model.Reajuste;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.model.Usuario;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Salva e busca contratos nas tabelas "contrato" e "garantia" (BOARD-36).
 *
 * Cadastrar um contrato mexe em três tabelas: grava o contrato, grava a garantia
 * e marca o imóvel como ocupado. Isso é feito numa transação: ou as três coisas
 * acontecem, ou nenhuma (como um "tudo ou nada"). Assim nunca fica um contrato
 * sem garantia ou um imóvel ocupado sem contrato.
 */
public class ContratoRepository implements Repositorio<Contrato> {

    private final ImovelRepository imoveis = new ImovelRepository();
    private final InquilinoRepository inquilinos = new InquilinoRepository();

    /**
     * Contrato novo: grava contrato + garantia e ocupa o imóvel, tudo junto.
     * Contrato já gravado: atualiza só a situação (ativo/encerrado).
     */
    @Override
    public void salvar(Contrato contrato) throws SQLException {
        salvar(contrato, null);
    }

    /**
     * Grava o contrato. Se a garantia for caução e quem cadastrou for informado, a caução recebida
     * entra no Caixa ou no Banco na mesma transação (o dinheiro já está com a Fundação).
     */
    public void salvar(Contrato contrato, Usuario quem) throws SQLException {
        if (contrato.getId() == null) {
            inserir(contrato, quem);
        } else {
            atualizarStatus(contrato);
        }
    }

    private void inserir(Contrato c, Usuario quem) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false); // começa a transação
            try {
                long id = inserirContrato(conexao, c);
                String numero = "CT-" + (1000 + id); // ex.: CT-1001
                try (PreparedStatement st = conexao.prepareStatement("UPDATE contrato SET numero = ? WHERE id = ?")) {
                    st.setString(1, numero);
                    st.setLong(2, id);
                    st.executeUpdate();
                }
                inserirGarantia(conexao, id, c.getGarantia());
                ocuparImovel(conexao, c.getImovel());
                if (quem != null && c.getGarantia() instanceof Caucao caucao) {
                    String descricao = "Caução do contrato " + numero + " · " + c.getInquilino().getNome()
                            + (caucao.getNumeroRecibo() == null ? "" : " · recibo " + caucao.getNumeroRecibo());
                    LancamentoRepository.inserir(conexao, new Lancamento(null, Conta.de(caucao.getFormaPagamento()),
                            TipoLancamento.CAUCAO_RECEBIDA, caucao.getDataRecebimento(), caucao.getValor(), descricao,
                            null, quem, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
                }
                conexao.commit(); // deu tudo certo: grava de verdade
                c.setId(id);
                c.setNumero(numero);
                c.getImovel().ocupar();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback(); // algo falhou: desfaz tudo
                throw e;
            }
        }
    }

    private long inserirContrato(Connection conexao, Contrato c) throws SQLException {
        String sql = "INSERT INTO contrato (imovel_id, inquilino_id, valor_aluguel, taxa_luz, valor_iptu, prazo, "
                + "data_entrega_chaves, data_termino, dia_vencimento, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, c.getImovel().getId());
            st.setLong(2, c.getInquilino().getId());
            st.setString(3, c.getValorAluguel().toPlainString());
            st.setString(4, c.getTaxaLuz().toPlainString());
            st.setString(5, c.getValorIptu().toPlainString());
            st.setString(6, c.getPrazo().name());
            st.setString(7, c.getDataEntregaChaves().toString());
            st.setString(8, c.getDataTermino().toString());
            st.setInt(9, c.getDiaVencimento());
            st.setString(10, c.getStatus().name());
            st.executeUpdate();
            try (ResultSet chaves = st.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new SQLException("O banco não devolveu o id do contrato.");
                }
                return chaves.getLong(1);
            }
        }
    }

    private void inserirGarantia(Connection conexao, long contratoId, Garantia garantia) throws SQLException {
        String sql = "INSERT INTO garantia (contrato_id, tipo, fiador_nome, fiador_cpf, fiador_telefone, "
                + "fiador_estado_civil, fiador_renda, fiador_endereco, fiador_conjuge_nome, fiador_conjuge_cpf, "
                + "caucao_valor, caucao_data, caucao_forma, caucao_recibo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            for (int coluna = 3; coluna <= 14; coluna++) {
                st.setString(coluna, null); // começa tudo vazio; abaixo preenche só o tipo certo
            }
            st.setLong(1, contratoId);
            if (garantia instanceof Fianca fianca) {
                Fiador f = fianca.getFiador();
                st.setString(2, "FIADOR");
                st.setString(3, f.getNome());
                st.setString(4, f.getCpf().getNumero());
                st.setString(5, f.getTelefone());
                st.setString(6, f.getEstadoCivil().name());
                st.setString(7, f.getRendaMensal().toPlainString());
                st.setString(8, f.getEndereco());
                Conjuge conjuge = f.getConjuge();
                if (conjuge != null) {
                    st.setString(9, conjuge.getNome());
                    st.setString(10, conjuge.getCpf() == null ? null : conjuge.getCpf().getNumero());
                }
            } else if (garantia instanceof Caucao caucao) {
                st.setString(2, "CAUCAO");
                st.setString(11, caucao.getValor().toPlainString());
                st.setString(12, caucao.getDataRecebimento().toString());
                st.setString(13, caucao.getFormaPagamento().name());
                st.setString(14, caucao.getNumeroRecibo());
            } else {
                throw new IllegalArgumentException("Tipo de garantia desconhecido: " + garantia);
            }
            st.executeUpdate();
        }
    }

    /** Só ocupa se o imóvel ainda estiver vago (proteção caso duas pessoas cadastrem ao mesmo tempo). */
    private void ocuparImovel(Connection conexao, Imovel imovel) throws SQLException {
        String sql = "UPDATE imovel SET situacao = 'OCUPADO' WHERE id = ? AND situacao = 'VAGO'";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, imovel.getId());
            if (st.executeUpdate() != 1) {
                throw new SQLException("O imóvel " + imovel.getCodigo() + " não está mais vago.");
            }
        }
    }

    private void atualizarStatus(Contrato c) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("UPDATE contrato SET status = ? WHERE id = ?")) {
            st.setString(1, c.getStatus().name());
            st.setLong(2, c.getId());
            st.executeUpdate();
        }
    }

    /** O imóvel já tem um contrato ativo? */
    public boolean existeAtivoNoImovel(long imovelId) throws SQLException {
        return existeAtivo("imovel_id", imovelId);
    }

    /** O inquilino já tem um contrato ativo (em outro imóvel)? */
    public boolean existeAtivoDoInquilino(long inquilinoId) throws SQLException {
        return existeAtivo("inquilino_id", inquilinoId);
    }

    private boolean existeAtivo(String coluna, long id) throws SQLException {
        return existe("SELECT 1 FROM contrato WHERE " + coluna + " = ? AND status = 'ATIVO'", id);
    }

    private boolean existe(String sql, long id) throws SQLException {
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<Contrato> buscarPorId(long id) throws SQLException {
        List<Contrato> achados = buscar("WHERE c.id = ?", id);
        return achados.isEmpty() ? Optional.empty() : Optional.of(achados.get(0));
    }

    /** Todos os contratos, em ordem de número. */
    @Override
    public List<Contrato> listarTodos() throws SQLException {
        return buscar("", null);
    }

    private List<Contrato> buscar(String filtro, Object parametro) throws SQLException {
        // Carrega imóveis e inquilinos uma vez só, em vez de ir ao banco a cada contrato.
        Map<Long, Imovel> porImovel = new HashMap<>();
        imoveis.listarTodos().forEach(i -> porImovel.put(i.getId(), i));
        Map<Long, Inquilino> porInquilino = new HashMap<>();
        inquilinos.listarTodos().forEach(i -> porInquilino.put(i.getId(), i));

        Map<Long, List<Prorrogacao>> porContrato = prorrogacoesPorContrato();
        Map<Long, List<Reajuste>> reajustes = reajustesPorContrato();
        Map<Long, Encerramento> encerrados = encerramentosPorContrato();

        String sql = "SELECT c.*, g.* FROM contrato c JOIN garantia g ON g.contrato_id = c.id "
                + filtro + " ORDER BY c.numero";
        List<Contrato> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            if (parametro != null) {
                st.setObject(1, parametro);
            }
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Contrato c = montar(rs, porImovel.get(rs.getLong("imovel_id")),
                            porInquilino.get(rs.getLong("inquilino_id")));
                    // O término gravado já inclui as prorrogações (BOARD-169).
                    c.restaurarProrrogacoes(LocalDate.parse(rs.getString("data_termino")),
                            porContrato.getOrDefault(c.getId(), List.of()));
                    c.restaurarReajustes(reajustes.getOrDefault(c.getId(), List.of())); // [D9]
                    c.restaurarEncerramento(encerrados.get(c.getId())); // null se está ativo (BOARD-174)
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    /**
     * Grava uma prorrogação (BOARD-169) numa transação: o histórico e o novo término do contrato
     * entram juntos, ou nenhum dos dois.
     */
    public void salvarProrrogacao(Contrato contrato, Prorrogacao p) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                String sql = "INSERT INTO contrato_prorrogacao (contrato_id, data, termino_anterior, novo_termino, "
                        + "prazo, feita_por) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    st.setLong(1, contrato.getId());
                    st.setString(2, p.getData().toString());
                    st.setString(3, p.getTerminoAnterior().toString());
                    st.setString(4, p.getNovoTermino().toString());
                    st.setString(5, p.getPrazo().name());
                    st.setString(6, p.getFeitaPor());
                    st.executeUpdate();
                    try (ResultSet chaves = st.getGeneratedKeys()) {
                        if (chaves.next()) {
                            p.setId(chaves.getLong(1));
                        }
                    }
                }
                try (PreparedStatement st = conexao.prepareStatement(
                        "UPDATE contrato SET data_termino = ? WHERE id = ?")) {
                    st.setString(1, contrato.getDataTermino().toString());
                    st.setLong(2, contrato.getId());
                    st.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /**
     * Grava o encerramento (BOARD-174) numa transação: o registro do encerramento, o contrato
     * "Encerrado" e o imóvel "Vago" entram juntos, ou nada muda.
     */
    public void salvarEncerramento(Contrato contrato) throws SQLException {
        Encerramento e = contrato.getEncerramento();
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                String sql = "INSERT INTO contrato_encerramento (contrato_id, data_saida, motivo, observacao, "
                        + "registrado_em, feito_por) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement st = conexao.prepareStatement(sql)) {
                    st.setLong(1, contrato.getId());
                    st.setString(2, e.getDataSaida().toString());
                    st.setString(3, e.getMotivo().name());
                    st.setString(4, e.getObservacao());
                    st.setString(5, e.getRegistradoEm().toString());
                    st.setString(6, e.getFeitoPor());
                    st.executeUpdate();
                }
                try (PreparedStatement st = conexao.prepareStatement("UPDATE contrato SET status = ? WHERE id = ?")) {
                    st.setString(1, StatusContrato.ENCERRADO.name());
                    st.setLong(2, contrato.getId());
                    st.executeUpdate();
                }
                try (PreparedStatement st = conexao.prepareStatement("UPDATE imovel SET situacao = ? WHERE id = ?")) {
                    st.setString(1, SituacaoImovel.VAGO.name());
                    st.setLong(2, contrato.getImovel().getId());
                    st.executeUpdate();
                }
                cancelarAlugueisDepoisDaSaida(conexao, contrato.getId(), e);
                conexao.commit();
            } catch (SQLException | RuntimeException erro) {
                conexao.rollback();
                throw erro;
            }
        }
    }

    /**
     * Encerramento registrado dias depois da saída: o sistema já pode ter gerado aluguéis que vencem
     * depois que o inquilino foi embora, e eles não são devidos. Esses aluguéis (não pagos e sem
     * recibo) vão para aluguel_cancelado, com o motivo, e saem da cobrança. Nada se perde: a cópia
     * fica guardada com quem encerrou e quando.
     */
    private static void cancelarAlugueisDepoisDaSaida(Connection conexao, long contratoId, Encerramento e)
            throws SQLException {
        String filtro = " FROM aluguel WHERE contrato_id = ? AND data_pagamento IS NULL AND vencimento > ?"
                + " AND NOT EXISTS (SELECT 1 FROM recibo r WHERE r.aluguel_id = aluguel.id)";
        try (PreparedStatement st = conexao.prepareStatement("INSERT INTO aluguel_cancelado (aluguel_id, contrato_id, "
                + "competencia, vencimento, valor_aluguel, taxa_luz, valor_iptu, outros, motivo, cancelado_por, "
                + "cancelado_em) SELECT id, contrato_id, competencia, vencimento, valor_aluguel, taxa_luz, valor_iptu, "
                + "outros, ?, ?, ?" + filtro)) {
            st.setString(1, "Vence depois da saída do inquilino (" + e.getDataSaida() + "): não é devido.");
            st.setString(2, e.getFeitoPor());
            st.setString(3, e.getRegistradoEm().toString());
            st.setLong(4, contratoId);
            st.setString(5, e.getDataSaida().toString());
            st.executeUpdate();
        }
        try (PreparedStatement st = conexao.prepareStatement("DELETE" + filtro)) {
            st.setLong(1, contratoId);
            st.setString(2, e.getDataSaida().toString());
            st.executeUpdate();
        }
    }

    /** Os encerramentos, por contrato. */
    private Map<Long, Encerramento> encerramentosPorContrato() throws SQLException {
        Map<Long, Encerramento> mapa = new HashMap<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT * FROM contrato_encerramento");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getLong("contrato_id"), new Encerramento(LocalDate.parse(rs.getString("data_saida")),
                        MotivoEncerramento.valueOf(rs.getString("motivo")), rs.getString("observacao"),
                        LocalDate.parse(rs.getString("registrado_em")), rs.getString("feito_por")));
            }
        }
        return mapa;
    }

    /**
     * Grava um reajuste anual ([D9] BOARD-46) numa transação: o histórico, o aluguel novo do
     * contrato e o valor dos aluguéis ainda não pagos que vencem depois do aniversário.
     * Tudo entra junto, ou nada entra.
     */
    public void salvarReajuste(Contrato contrato, Reajuste r) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                String sql = "INSERT INTO contrato_reajuste (contrato_id, aniversario, aplicado_em, percentual, "
                        + "valor_anterior, valor_novo, feito_por) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    st.setLong(1, contrato.getId());
                    st.setString(2, r.getAniversario().toString());
                    st.setString(3, r.getAplicadoEm().toString());
                    st.setString(4, r.getPercentual().toPlainString());
                    st.setString(5, r.getValorAnterior().toPlainString());
                    st.setString(6, r.getValorNovo().toPlainString());
                    st.setString(7, r.getFeitoPor());
                    st.executeUpdate();
                    try (ResultSet chaves = st.getGeneratedKeys()) {
                        if (chaves.next()) {
                            r.setId(chaves.getLong(1));
                        }
                    }
                }
                try (PreparedStatement st = conexao.prepareStatement(
                        "UPDATE contrato SET valor_aluguel = ? WHERE id = ?")) {
                    st.setString(1, r.getValorNovo().toPlainString());
                    st.setLong(2, contrato.getId());
                    st.executeUpdate();
                }
                // O aluguel que vence depois do aniversário já é do ano novo (o 1º aluguel vence
                // 30 dias depois das chaves, então cada aluguel paga o mês que passou).
                try (PreparedStatement st = conexao.prepareStatement("UPDATE aluguel SET valor_aluguel = ? "
                        + "WHERE contrato_id = ? AND data_pagamento IS NULL AND vencimento > ?")) {
                    st.setString(1, r.getValorNovo().toPlainString());
                    st.setLong(2, contrato.getId());
                    st.setString(3, r.getAniversario().toString());
                    st.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /** Todos os reajustes, agrupados por contrato e em ordem de aniversário ([D9]). */
    private Map<Long, List<Reajuste>> reajustesPorContrato() throws SQLException {
        Map<Long, List<Reajuste>> mapa = new HashMap<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT * FROM contrato_reajuste ORDER BY aniversario, id");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Reajuste r = new Reajuste(LocalDate.parse(rs.getString("aniversario")),
                        LocalDate.parse(rs.getString("aplicado_em")), new BigDecimal(rs.getString("percentual")),
                        new BigDecimal(rs.getString("valor_anterior")), new BigDecimal(rs.getString("valor_novo")),
                        rs.getString("feito_por"));
                r.setId(rs.getLong("id"));
                mapa.computeIfAbsent(rs.getLong("contrato_id"), k -> new ArrayList<>()).add(r);
            }
        }
        return mapa;
    }

    /** Todas as prorrogações, agrupadas por contrato e em ordem de data. */
    private Map<Long, List<Prorrogacao>> prorrogacoesPorContrato() throws SQLException {
        Map<Long, List<Prorrogacao>> mapa = new HashMap<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT * FROM contrato_prorrogacao ORDER BY data, id");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Prorrogacao p = new Prorrogacao(LocalDate.parse(rs.getString("data")),
                        LocalDate.parse(rs.getString("termino_anterior")), PrazoContrato.valueOf(rs.getString("prazo")),
                        rs.getString("feita_por"));
                p.setId(rs.getLong("id"));
                mapa.computeIfAbsent(rs.getLong("contrato_id"), k -> new ArrayList<>()).add(p);
            }
        }
        return mapa;
    }

    /** Transforma uma linha do banco (contrato + garantia) num objeto Contrato. */
    private Contrato montar(ResultSet rs, Imovel imovel, Inquilino inquilino) throws SQLException {
        Contrato c = new Contrato(imovel, inquilino, montarGarantia(rs),
                PrazoContrato.valueOf(rs.getString("prazo")), LocalDate.parse(rs.getString("data_entrega_chaves")));
        c.setId(rs.getLong("id"));
        c.setNumero(rs.getString("numero"));
        // Os valores do dia do cadastro, e não os de hoje no imóvel.
        c.setValores(new BigDecimal(rs.getString("valor_aluguel")), new BigDecimal(rs.getString("taxa_luz")),
                new BigDecimal(rs.getString("valor_iptu")));
        c.setStatus(StatusContrato.valueOf(rs.getString("status")));
        return c;
    }

    private Garantia montarGarantia(ResultSet rs) throws SQLException {
        if ("CAUCAO".equals(rs.getString("tipo"))) {
            Caucao caucao = new Caucao(new BigDecimal(rs.getString("caucao_valor")),
                    LocalDate.parse(rs.getString("caucao_data")), FormaPagamento.valueOf(rs.getString("caucao_forma")));
            caucao.setNumeroRecibo(rs.getString("caucao_recibo"));
            return caucao;
        }
        Fiador f = new Fiador(rs.getString("fiador_nome"), new Cpf(rs.getString("fiador_cpf")));
        f.setTelefone(rs.getString("fiador_telefone"));
        f.setEstadoCivil(EstadoCivil.valueOf(rs.getString("fiador_estado_civil")));
        f.setRendaMensal(new BigDecimal(rs.getString("fiador_renda")));
        f.setEndereco(rs.getString("fiador_endereco"));
        String nomeConjuge = rs.getString("fiador_conjuge_nome");
        if (nomeConjuge != null) {
            Conjuge conjuge = new Conjuge(nomeConjuge);
            String cpfConjuge = rs.getString("fiador_conjuge_cpf");
            conjuge.setCpf(cpfConjuge == null ? null : new Cpf(cpfConjuge));
            f.setConjuge(conjuge);
        }
        return new Fianca(f);
    }
}
