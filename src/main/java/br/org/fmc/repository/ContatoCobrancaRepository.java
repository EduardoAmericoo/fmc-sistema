package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persiste os contatos de cobrança ([D7] BOARD-64). A observação fica em cobranca_contato (coluna
 * "resultado") e o que deu (tipo e data prometida) em cobranca_contato_resultado; os dois são
 * gravados juntos.
 */
public class ContatoCobrancaRepository {

    /** O começo de toda consulta: o contato, o que deu (se tiver) e quem registrou. */
    private static final String SELECT = "SELECT cc.*, r.tipo, r.data_prometida, "
            + "u.nome AS usuario_nome, u.email AS usuario_email, u.perfil AS usuario_perfil "
            + "FROM cobranca_contato cc JOIN usuario u ON u.id = cc.feito_por "
            + "LEFT JOIN cobranca_contato_resultado r ON r.contato_id = cc.id ";

    /** Grava o contato e o resultado numa transação: ou entram os dois, ou nenhum. */
    public ContatoCobranca salvar(ContatoCobranca contato) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement st = conexao.prepareStatement(
                        "INSERT INTO cobranca_contato (contrato_id, registrado_em, resultado, feito_por) "
                                + "VALUES (?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    st.setLong(1, contato.contratoId());
                    st.setString(2, contato.registradoEm().toString());
                    st.setString(3, contato.observacao()); // pode ser vazio quando o tipo já diz tudo
                    st.setLong(4, contato.feitoPor().getId());
                    st.executeUpdate();
                    try (ResultSet rs = st.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                if (contato.tipo() != null) {
                    try (PreparedStatement st = conexao.prepareStatement(
                            "INSERT INTO cobranca_contato_resultado (contato_id, tipo, data_prometida) VALUES (?, ?, ?)")) {
                        st.setLong(1, id);
                        st.setString(2, contato.tipo().name());
                        st.setString(3, contato.dataPrometida() == null ? null : contato.dataPrometida().toString());
                        st.executeUpdate();
                    }
                }
                conexao.commit();
                return new ContatoCobranca(id, contato.contratoId(), contato.registradoEm(), contato.tipo(),
                        contato.dataPrometida(), contato.observacao(), contato.feitoPor());
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /** O contato mais recente de cada contrato (chave: id do contrato). */
    public Map<Long, ContatoCobranca> ultimosPorContrato() throws SQLException {
        Map<Long, ContatoCobranca> ultimos = new LinkedHashMap<>();
        for (ContatoCobranca contato : consultar(SELECT + "ORDER BY cc.registrado_em DESC, cc.id DESC")) {
            ultimos.putIfAbsent(contato.contratoId(), contato);
        }
        return ultimos;
    }

    /** Todos os contatos de um contrato, do mais recente para o mais antigo. */
    public List<ContatoCobranca> listarDoContrato(long contratoId) throws SQLException {
        return consultar(SELECT + "WHERE cc.contrato_id = ? ORDER BY cc.registrado_em DESC, cc.id DESC", contratoId);
    }

    private List<ContatoCobranca> consultar(String sql, Object... parametros) throws SQLException {
        List<ContatoCobranca> lista = new ArrayList<>();
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

    private static ContatoCobranca ler(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario(rs.getString("usuario_nome"), rs.getString("usuario_email"),
                Perfil.valueOf(rs.getString("usuario_perfil")));
        usuario.setId(rs.getLong("feito_por"));
        String tipo = rs.getString("tipo");
        String data = rs.getString("data_prometida");
        String texto = rs.getString("resultado"); // a observação (nos contatos antigos, o texto todo)
        return new ContatoCobranca(rs.getLong("id"), rs.getLong("contrato_id"),
                LocalDateTime.parse(rs.getString("registrado_em")),
                tipo == null ? null : ResultadoContato.valueOf(tipo),
                data == null ? null : LocalDate.parse(data), texto, usuario);
    }
}
