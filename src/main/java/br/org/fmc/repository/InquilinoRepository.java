package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Conjuge;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Inquilino;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Salva e busca inquilinos na tabela "inquilino" (BOARD-35). */
public class InquilinoRepository implements Repositorio<Inquilino> {

    private static final String COLUNAS = "id, nome, cpf, telefone, email, estado_civil, profissao, local_trabalho, "
            + "renda_mensal, outras_rendas, conjuge_nome, conjuge_cpf, conjuge_renda";

    @Override
    public void salvar(Inquilino inquilino) throws SQLException {
        if (inquilino.getId() == null) {
            inserir(inquilino);
        } else {
            atualizar(inquilino);
        }
    }

    private void inserir(Inquilino i) throws SQLException {
        String sql = "INSERT INTO inquilino (nome, cpf, telefone, email, estado_civil, profissao, local_trabalho, "
                + "renda_mensal, outras_rendas, conjuge_nome, conjuge_cpf, conjuge_renda) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(st, i);
            st.executeUpdate();
            try (ResultSet chaves = st.getGeneratedKeys()) {
                if (chaves.next()) {
                    i.setId(chaves.getLong(1));
                }
            }
        }
    }

    private void atualizar(Inquilino i) throws SQLException {
        String sql = "UPDATE inquilino SET nome = ?, cpf = ?, telefone = ?, email = ?, estado_civil = ?, "
                + "profissao = ?, local_trabalho = ?, renda_mensal = ?, outras_rendas = ?, "
                + "conjuge_nome = ?, conjuge_cpf = ?, conjuge_renda = ? WHERE id = ?";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            preencher(st, i);
            st.setLong(13, i.getId());
            st.executeUpdate();
        }
    }

    private void preencher(PreparedStatement st, Inquilino i) throws SQLException {
        st.setString(1, i.getNome());
        st.setString(2, i.getCpf().getNumero());
        st.setString(3, i.getTelefone());
        st.setString(4, i.getEmail());
        st.setString(5, i.getEstadoCivil().name());
        st.setString(6, i.getProfissao());
        st.setString(7, i.getLocalTrabalho());
        st.setString(8, i.getRendaMensal().toPlainString());
        st.setString(9, i.getOutrasRendas().toPlainString());
        Conjuge c = i.getConjuge();
        st.setString(10, c == null ? null : c.getNome());
        st.setString(11, c == null || c.getCpf() == null ? null : c.getCpf().getNumero());
        st.setString(12, c == null ? null : c.getRenda().toPlainString());
    }

    @Override
    public Optional<Inquilino> buscarPorId(long id) throws SQLException {
        return buscarUm("SELECT " + COLUNAS + " FROM inquilino WHERE id = ?", id);
    }

    public Optional<Inquilino> buscarPorCpf(Cpf cpf) throws SQLException {
        return buscarUm("SELECT " + COLUNAS + " FROM inquilino WHERE cpf = ?", cpf.getNumero());
    }

    @Override
    public List<Inquilino> listarTodos() throws SQLException {
        List<Inquilino> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT " + COLUNAS + " FROM inquilino ORDER BY nome");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        }
        return lista;
    }

    private Optional<Inquilino> buscarUm(String sql, Object parametro) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, parametro);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        }
    }

    /** Transforma uma linha do banco num objeto Inquilino. */
    private Inquilino montar(ResultSet rs) throws SQLException {
        Inquilino i = new Inquilino(rs.getString("nome"), new Cpf(rs.getString("cpf")));
        i.setId(rs.getLong("id"));
        i.setTelefone(rs.getString("telefone"));
        i.setEmail(rs.getString("email"));
        i.setEstadoCivil(EstadoCivil.valueOf(rs.getString("estado_civil")));
        i.setProfissao(rs.getString("profissao"));
        i.setLocalTrabalho(rs.getString("local_trabalho"));
        i.setRendaMensal(new BigDecimal(rs.getString("renda_mensal")));
        i.setOutrasRendas(new BigDecimal(rs.getString("outras_rendas")));
        String nomeConjuge = rs.getString("conjuge_nome");
        if (nomeConjuge != null) {
            Conjuge c = new Conjuge(nomeConjuge);
            String cpfConjuge = rs.getString("conjuge_cpf");
            c.setCpf(cpfConjuge == null ? null : new Cpf(cpfConjuge));
            c.setRenda(new BigDecimal(rs.getString("conjuge_renda")));
            i.setConjuge(c);
        }
        return i;
    }
}
