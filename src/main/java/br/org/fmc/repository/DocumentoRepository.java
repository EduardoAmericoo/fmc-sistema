package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.DocumentoContrato;
import br.org.fmc.model.TipoDocumento;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda os dados dos documentos digitalizados dos contratos (tabela contrato_documento).
 * O arquivo em si fica na pasta data/documentos; aqui só fica o caminho até ele.
 * Não existe "excluir": só arquivar e reativar.
 */
public class DocumentoRepository {

    public DocumentoContrato salvar(DocumentoContrato d) throws SQLException {
        String sql = "INSERT INTO contrato_documento (contrato_id, tipo, data, arquivo, nome_original, tamanho, "
                + "observacao, enviado_em, enviado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, d.contratoId());
            st.setString(2, d.tipo().name());
            st.setString(3, d.data().toString());
            st.setString(4, d.arquivo());
            st.setString(5, d.nomeOriginal());
            st.setLong(6, d.tamanho());
            st.setString(7, d.observacao());
            st.setString(8, d.enviadoEm().toString());
            st.setString(9, d.enviadoPor());
            st.executeUpdate();
            try (ResultSet rs = st.getGeneratedKeys()) {
                Long id = rs.next() ? rs.getLong(1) : null;
                return new DocumentoContrato(id, d.contratoId(), d.tipo(), d.data(), d.arquivo(), d.nomeOriginal(),
                        d.tamanho(), d.observacao(), d.enviadoEm(), d.enviadoPor(), null);
            }
        }
    }

    public Optional<DocumentoContrato> buscarPorId(long id) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT * FROM contrato_documento WHERE id = ?")) {
            st.setLong(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
            }
        }
    }

    /** Documentos do contrato (ativos e arquivados), do mais recente para o mais antigo. */
    public List<DocumentoContrato> listarPorContrato(long contratoId) throws SQLException {
        String sql = "SELECT * FROM contrato_documento WHERE contrato_id = ? ORDER BY data DESC, id DESC";
        List<DocumentoContrato> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, contratoId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    lista.add(ler(rs));
                }
            }
        }
        return lista;
    }

    /** Arquiva (quando != null) ou reativa (quando null) o documento. */
    public void marcarArquivado(long id, LocalDateTime quando) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "UPDATE contrato_documento SET arquivado_em = ? WHERE id = ?")) {
            st.setString(1, quando == null ? null : quando.toString());
            st.setLong(2, id);
            st.executeUpdate();
        }
    }

    /** O caminho já está em uso por outro documento? (para não sobrescrever cópias) */
    public boolean arquivoEmUso(String arquivo) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "SELECT 1 FROM contrato_documento WHERE arquivo = ?")) {
            st.setString(1, arquivo);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static DocumentoContrato ler(ResultSet rs) throws SQLException {
        String arquivado = rs.getString("arquivado_em");
        return new DocumentoContrato(rs.getLong("id"), rs.getLong("contrato_id"),
                TipoDocumento.valueOf(rs.getString("tipo")), LocalDate.parse(rs.getString("data")),
                rs.getString("arquivo"), rs.getString("nome_original"), rs.getLong("tamanho"),
                rs.getString("observacao"), LocalDateTime.parse(rs.getString("enviado_em")),
                rs.getString("enviado_por"), arquivado == null ? null : LocalDateTime.parse(arquivado));
    }
}
