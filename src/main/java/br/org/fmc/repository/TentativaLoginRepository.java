package br.org.fmc.repository;

import br.org.fmc.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Guarda as senhas erradas seguidas de cada e-mail, na tabela "tentativa_login". */
public class TentativaLoginRepository {

    /** Quantos erros seguidos e, se houver, até quando o e-mail está em pausa. */
    public record Tentativas(int erros, LocalDateTime pausaAte) {
    }

    public Optional<Tentativas> buscar(String email) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "SELECT erros, pausa_ate FROM tentativa_login WHERE email = ?")) {
            st.setString(1, email);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String pausa = rs.getString("pausa_ate");
                return Optional.of(new Tentativas(rs.getInt("erros"),
                        pausa == null ? null : LocalDateTime.parse(pausa)));
            }
        }
    }

    public void salvar(String email, Tentativas t) throws SQLException {
        String sql = "INSERT INTO tentativa_login (email, erros, pausa_ate) VALUES (?, ?, ?) "
                + "ON CONFLICT(email) DO UPDATE SET erros = excluded.erros, pausa_ate = excluded.pausa_ate";
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, email);
            st.setInt(2, t.erros());
            st.setString(3, t.pausaAte() == null ? null : t.pausaAte().withNano(0).toString());
            st.executeUpdate();
        }
    }

    /** Zera as tentativas (login certo). */
    public void apagar(String email) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("DELETE FROM tentativa_login WHERE email = ?")) {
            st.setString(1, email);
            st.executeUpdate();
        }
    }
}
