package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persiste contatos de cobrança e consulta o contato mais recente de cada contrato. */
public class ContatoCobrancaRepository {

    public ContatoCobranca salvar(Contrato contrato, String resultado, Usuario feitoPor, LocalDateTime dataHora)
            throws SQLException {
        String sql = "INSERT INTO cobranca_contato (contrato_id, registrado_em, resultado, feito_por) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, contrato.getId());
            st.setString(2, dataHora.toString());
            st.setString(3, resultado);
            st.setLong(4, feitoPor.getId());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                Long id = rs.next() ? rs.getLong(1) : null;
                return new ContatoCobranca(id, contrato.getId(), dataHora, resultado, feitoPor);
            }
        }
    }

    public Map<Long, ContatoCobranca> ultimosPorContrato() throws SQLException {
        String sql = "SELECT cc.*, u.nome AS usuario_nome, u.email AS usuario_email, u.perfil AS usuario_perfil "
                + "FROM cobranca_contato cc JOIN usuario u ON u.id = cc.feito_por "
                + "ORDER BY cc.registrado_em DESC, cc.id DESC";
        Map<Long, ContatoCobranca> ultimos = new LinkedHashMap<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                long contratoId = rs.getLong("contrato_id");
                if (ultimos.containsKey(contratoId)) {
                    continue;
                }
                Usuario usuario = new Usuario(rs.getString("usuario_nome"), rs.getString("usuario_email"),
                        Perfil.valueOf(rs.getString("usuario_perfil")));
                usuario.setId(rs.getLong("feito_por"));
                ultimos.put(contratoId, new ContatoCobranca(rs.getLong("id"), contratoId,
                        LocalDateTime.parse(rs.getString("registrado_em")), rs.getString("resultado"), usuario));
            }
        }
        return ultimos;
    }
}
