package br.org.fmc.repository;

import br.org.fmc.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Esqueci a senha · O pedido de recuperação em aberto de cada usuário (um por vez). */
public class RecuperacaoSenhaRepository {

    /** O código (só o hash), quando foi pedido, até quando vale e quantas vezes erraram. */
    public record Pedido(long usuarioId, String codigoHash, LocalDateTime criadoEm, LocalDateTime expiraEm,
            int tentativas) {
    }

    /** Grava um pedido novo; o anterior do mesmo usuário, se houver, deixa de valer. */
    public void salvar(Pedido pedido) throws SQLException {
        String sql = "INSERT OR REPLACE INTO senha_recuperacao (usuario_id, codigo_hash, criado_em, expira_em, tentativas) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Database.conectar(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setLong(1, pedido.usuarioId());
            st.setString(2, pedido.codigoHash());
            st.setString(3, pedido.criadoEm().toString());
            st.setString(4, pedido.expiraEm().toString());
            st.setInt(5, pedido.tentativas());
            st.executeUpdate();
        }
    }

    public Optional<Pedido> buscar(long usuarioId) throws SQLException {
        try (Connection c = Database.conectar();
             PreparedStatement st = c.prepareStatement("SELECT * FROM senha_recuperacao WHERE usuario_id = ?")) {
            st.setLong(1, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Pedido(usuarioId, rs.getString("codigo_hash"),
                        LocalDateTime.parse(rs.getString("criado_em")), LocalDateTime.parse(rs.getString("expira_em")),
                        rs.getInt("tentativas")));
            }
        }
    }

    /** Conta mais um código errado. */
    public void contarErro(long usuarioId) throws SQLException {
        try (Connection c = Database.conectar(); PreparedStatement st = c.prepareStatement(
                "UPDATE senha_recuperacao SET tentativas = tentativas + 1 WHERE usuario_id = ?")) {
            st.setLong(1, usuarioId);
            st.executeUpdate();
        }
    }

    /** O código foi usado, venceu ou estourou as tentativas: não vale mais. */
    public void apagar(long usuarioId) throws SQLException {
        try (Connection c = Database.conectar();
             PreparedStatement st = c.prepareStatement("DELETE FROM senha_recuperacao WHERE usuario_id = ?")) {
            st.setLong(1, usuarioId);
            st.executeUpdate();
        }
    }
}
