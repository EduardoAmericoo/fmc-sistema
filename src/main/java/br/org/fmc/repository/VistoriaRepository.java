package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FotoVistoria;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda os dados das fotos das vistorias (tabela vistoria_foto). A foto em si fica em
 * data/documentos/vistorias; aqui fica o caminho, o cômodo e o lacre (hash). Não existe excluir.
 */
public class VistoriaRepository {

    public FotoVistoria salvar(FotoVistoria f) throws SQLException {
        String sql = "INSERT INTO vistoria_foto (contrato_id, etapa, comodo, observacao, arquivo, nome_original, "
                + "hash_sha256, registrada_em, registrada_por, tirada_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, f.contratoId());
            st.setString(2, f.etapa().name());
            st.setString(3, f.comodo());
            st.setString(4, f.observacao());
            st.setString(5, f.arquivo());
            st.setString(6, f.nomeOriginal());
            st.setString(7, f.hashSha256());
            st.setString(8, f.registradaEm().toString());
            st.setString(9, f.registradaPor());
            st.setString(10, f.tiradaEm() == null ? null : f.tiradaEm().toString());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                Long id = rs.next() ? rs.getLong(1) : null;
                return new FotoVistoria(id, f.contratoId(), f.etapa(), f.comodo(), f.observacao(), f.arquivo(),
                        f.nomeOriginal(), f.hashSha256(), f.registradaEm(), f.registradaPor(), f.tiradaEm(), null);
            }
        }
    }

    public Optional<FotoVistoria> buscarPorId(long id) throws SQLException {
        List<FotoVistoria> achadas = consultar("SELECT * FROM vistoria_foto WHERE id = ?", id);
        return achadas.stream().findFirst();
    }

    /** As fotos do contrato (ativas e arquivadas), na ordem em que foram registradas. */
    public List<FotoVistoria> listarPorContrato(long contratoId) throws SQLException {
        return consultar("SELECT * FROM vistoria_foto WHERE contrato_id = ? ORDER BY id", contratoId);
    }

    /** Arquiva (quando != null) ou reativa (quando null) a foto. */
    public void marcarArquivada(long id, LocalDateTime quando) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "UPDATE vistoria_foto SET arquivada_em = ? WHERE id = ?")) {
            st.setString(1, quando == null ? null : quando.toString());
            st.setLong(2, id);
            st.executeUpdate();
        }
    }

    private List<FotoVistoria> consultar(String sql, Object parametro) throws SQLException {
        List<FotoVistoria> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, parametro);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String arquivada = rs.getString("arquivada_em");
                    String tirada = rs.getString("tirada_em");
                    lista.add(new FotoVistoria(rs.getLong("id"), rs.getLong("contrato_id"),
                            EtapaVistoria.valueOf(rs.getString("etapa")), rs.getString("comodo"),
                            rs.getString("observacao"), rs.getString("arquivo"), rs.getString("nome_original"),
                            rs.getString("hash_sha256"), LocalDateTime.parse(rs.getString("registrada_em")),
                            rs.getString("registrada_por"), tirada == null ? null : LocalDateTime.parse(tirada),
                            arquivada == null ? null : LocalDateTime.parse(arquivada)));
                }
            }
        }
        return lista;
    }
}
