package br.org.fmc.repository;

import br.org.fmc.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Lê as configurações gravadas na tabela "sistema" (ex.: a versão do schema).
 *
 * Serve também de exemplo de repositório: SQL só aqui, sempre com
 * PreparedStatement e try-with-resources.
 */
public class SistemaRepository {

    public Optional<String> buscarValor(String chave) throws SQLException {
        String sql = "SELECT valor FROM sistema WHERE chave = ?";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, chave);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("valor")) : Optional.empty();
            }
        }
    }
}
