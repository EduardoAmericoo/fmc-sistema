package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Salva e busca usuários na tabela "usuario". */
public class UsuarioRepository implements Repositorio<Usuario> {

    private static final String COLUNAS = "id, nome, email, senha_hash, perfil, ativo";

    @Override
    public void salvar(Usuario usuario) throws SQLException {
        if (usuario.getId() == null) {
            inserir(usuario);
        } else {
            atualizar(usuario);
        }
    }

    private void inserir(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (nome, email, senha_hash, perfil, ativo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(st, u);
            st.executeUpdate();
            try (ResultSet chaves = st.getGeneratedKeys()) {
                if (chaves.next()) {
                    u.setId(chaves.getLong(1));
                }
            }
        }
    }

    private void atualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, perfil = ?, ativo = ? WHERE id = ?";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            preencher(st, u);
            st.setLong(6, u.getId());
            st.executeUpdate();
        }
    }

    private void preencher(PreparedStatement st, Usuario u) throws SQLException {
        st.setString(1, u.getNome());
        st.setString(2, u.getEmail());
        st.setString(3, u.getSenhaHash());
        st.setString(4, u.getPerfil().name());
        st.setInt(5, u.isAtivo() ? 1 : 0);
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) throws SQLException {
        return buscarUm("SELECT " + COLUNAS + " FROM usuario WHERE id = ?", id);
    }

    /** O e-mail é procurado em minúsculas, do mesmo jeito que é guardado. */
    public Optional<Usuario> buscarPorEmail(String email) throws SQLException {
        if (email == null) {
            return Optional.empty();
        }
        return buscarUm("SELECT " + COLUNAS + " FROM usuario WHERE email = ?", email.trim().toLowerCase());
    }

    @Override
    public List<Usuario> listarTodos() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT " + COLUNAS + " FROM usuario ORDER BY nome");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        }
        return lista;
    }

    /** Quantos usuários existem (usado para criar o Administrador inicial). */
    public int contar() throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT COUNT(*) FROM usuario");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Optional<Usuario> buscarUm(String sql, Object parametro) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, parametro);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        }
    }

    /** Transforma uma linha do banco num objeto Usuario. */
    private Usuario montar(ResultSet rs) throws SQLException {
        Usuario u = new Usuario(rs.getString("nome"), rs.getString("email"), Perfil.valueOf(rs.getString("perfil")));
        u.setId(rs.getLong("id"));
        u.setSenhaHash(rs.getString("senha_hash"));
        u.setAtivo(rs.getInt("ativo") == 1);
        return u;
    }
}
