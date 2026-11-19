package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Conta;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.model.Usuario;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** [D4] BOARD-43 · Grava e lê os lançamentos do Caixa e do Banco. */
public class LancamentoRepository {

    private static final String SELECT = "SELECT l.*, u.nome AS usuario_nome, u.email AS usuario_email, "
            + "u.perfil AS usuario_perfil FROM lancamento l JOIN usuario u ON u.id = l.registrado_por ";

    /**
     * Grava usando a conexão de quem chamou: assim o lançamento entra na MESMA transação do
     * recibo (ou entram os dois, ou nenhum).
     */
    public static long inserir(Connection conexao, Lancamento l) throws SQLException {
        String sql = "INSERT INTO lancamento (conta, tipo, data, valor, descricao, recibo_id, registrado_por, "
                + "registrado_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            st.setString(1, l.conta().name());
            st.setString(2, l.tipo().name());
            st.setString(3, l.data().toString());
            st.setString(4, l.valor().toPlainString());
            st.setString(5, l.descricao());
            st.setObject(6, l.reciboId());
            st.setLong(7, l.registradoPor().getId());
            st.setString(8, l.registradoEm().toString());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Grava um lançamento avulso (ex.: o saldo inicial), numa transação própria. */
    public long salvar(Lancamento l) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            return inserir(conexao, l);
        }
    }

    /**
     * Grava o depósito do Caixa no Banco: a saída do Caixa e a entrada no Banco na MESMA
     * transação (ou entram as duas, ou nenhuma), para o total da Fundação nunca ficar errado.
     */
    public void salvarTransferencia(Lancamento saidaDoCaixa, Lancamento entradaNoBanco) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                inserir(conexao, saidaDoCaixa);
                inserir(conexao, entradaNoBanco);
                conexao.commit();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /** Os lançamentos da conta entre as datas (inclusive), em ordem de data. */
    public List<Lancamento> listar(Conta conta, LocalDate de, LocalDate ate) throws SQLException {
        return consultar(SELECT + "WHERE l.conta = ? AND l.data >= ? AND l.data <= ? ORDER BY l.data, l.id",
                conta.name(), de.toString(), ate.toString());
    }

    /** O lançamento gerado por um recibo, se houver. */
    public Optional<Lancamento> doRecibo(long reciboId) throws SQLException {
        return consultar(SELECT + "WHERE l.recibo_id = ? AND l.tipo = ? ORDER BY l.id",
                reciboId, TipoLancamento.RECEBIMENTO_ALUGUEL.name()).stream().findFirst();
    }

    /** O saldo inicial já informado da conta, se houver. */
    public Optional<Lancamento> saldoInicial(Conta conta) throws SQLException {
        return consultar(SELECT + "WHERE l.conta = ? AND l.tipo = ? ORDER BY l.id",
                conta.name(), TipoLancamento.SALDO_INICIAL.name()).stream().findFirst();
    }

    /** Soma de tudo o que entrou e saiu da conta ANTES da data (o "saldo anterior" daquele dia). */
    public BigDecimal saldoAntesDe(Conta conta, LocalDate data) throws SQLException {
        BigDecimal saldo = BigDecimal.ZERO;
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "SELECT valor FROM lancamento WHERE conta = ? AND data < ?")) {
            st.setString(1, conta.name());
            st.setString(2, data.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    saldo = saldo.add(new BigDecimal(rs.getString(1))); // dinheiro: soma exata, sem arredondar
                }
            }
        }
        return saldo;
    }

    private List<Lancamento> consultar(String sql, Object... parametros) throws SQLException {
        List<Lancamento> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                st.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    lista.add(ler(rs));
                }
            }
        }
        return lista;
    }

    private static Lancamento ler(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario(rs.getString("usuario_nome"), rs.getString("usuario_email"),
                Perfil.valueOf(rs.getString("usuario_perfil")));
        usuario.setId(rs.getLong("registrado_por"));
        long recibo = rs.getLong("recibo_id");
        Long reciboId = rs.wasNull() ? null : recibo;
        return new Lancamento(rs.getLong("id"), Conta.valueOf(rs.getString("conta")),
                TipoLancamento.valueOf(rs.getString("tipo")), LocalDate.parse(rs.getString("data")),
                new BigDecimal(rs.getString("valor")), rs.getString("descricao"), reciboId, usuario,
                LocalDateTime.parse(rs.getString("registrado_em")));
    }
}
