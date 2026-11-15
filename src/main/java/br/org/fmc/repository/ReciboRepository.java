package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.DispensaEncargos;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Lancamento;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.TipoLancamento;
import br.org.fmc.model.Usuario;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Grava o recibo, a dispensa de encargos (se houver, BOARD-66) e marca o aluguel como pago,
 * tudo na mesma transação.
 */
public class ReciboRepository {

    public Recibo registrar(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            Encargos encargos, String documento, Usuario usuario) throws SQLException {
        return registrar(aluguel, dataPagamento, formaPagamento, encargos, documento, usuario, null);
    }

    /**
     * @param encargos os encargos COBRADOS (zerados quando houve dispensa)
     * @param dispensa os encargos dispensados, com motivo e autor; null se não houve dispensa
     */
    public Recibo registrar(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            Encargos encargos, String documento, Usuario usuario, DispensaEncargos dispensa) throws SQLException {
        int ano = dataPagamento.getYear();
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                int sequencial = proximoSequencial(conexao, ano);
                Recibo recibo = new Recibo(ano, sequencial, aluguel, dataPagamento, formaPagamento,
                        encargos, documento, usuario, dispensa);
                registrarPagamento(conexao, aluguel, dataPagamento);
                inserir(conexao, recibo);
                recibo.setId(buscarId(conexao, ano, sequencial));
                if (dispensa != null) {
                    inserirDispensa(conexao, recibo.getId(), dispensa);
                }
                // [D4] BOARD-43: o dinheiro entra sozinho no Caixa ou no Banco, na mesma transação
                LancamentoRepository.inserir(conexao, lancamentoDoRecibo(recibo));
                conexao.commit();
                aluguel.registrarPagamento(dataPagamento);
                return recibo;
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
    }

    /**
     * [D4] BOARD-43 · O lançamento que o recibo gera: o valor total pago (aluguel, taxas e
     * encargos cobrados), na data do pagamento, no Caixa (dinheiro e cheque) ou no Banco.
     */
    static Lancamento lancamentoDoRecibo(Recibo r) {
        Aluguel a = r.getAluguel();
        String descricao = "Aluguel " + String.format("%02d/%d", a.getCompetencia().getMonthValue(),
                a.getCompetencia().getYear()) + " · " + a.getContrato().getNumero() + " · "
                + a.getContrato().getInquilino().getNome() + " · recibo " + r.getNumero() + " · "
                + r.getFormaPagamento().getDescricao();
        return new Lancamento(null, r.getConta(), TipoLancamento.RECEBIMENTO_ALUGUEL, r.getDataPagamento(),
                r.getValorTotal(), descricao, r.getId(), r.getEmitidoPor(), r.getDataEmissao());
    }

    private int proximoSequencial(Connection conexao, int ano) throws SQLException {
        try (PreparedStatement st = conexao.prepareStatement(
                "SELECT COALESCE(MAX(sequencial), 0) + 1 FROM recibo WHERE ano = ?")) {
            st.setInt(1, ano);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private void registrarPagamento(Connection conexao, Aluguel aluguel, LocalDate dataPagamento) throws SQLException {
        try (PreparedStatement st = conexao.prepareStatement(
                "UPDATE aluguel SET data_pagamento = ? WHERE id = ? AND data_pagamento IS NULL")) {
            st.setString(1, dataPagamento.toString());
            st.setLong(2, aluguel.getId());
            if (st.executeUpdate() != 1) {
                throw new SQLException("Este aluguel já foi recebido ou não está mais disponível.");
            }
        }
    }

    private void inserir(Connection conexao, Recibo recibo) throws SQLException {
        String sql = "INSERT INTO recibo (aluguel_id, ano, sequencial, data_pagamento, forma_pagamento, documento, "
                + "dias_atraso, meses_encargos, multa, juros, correcao, valor_total, emitido_por, data_emissao) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, recibo.getAluguel().getId());
            st.setInt(2, recibo.getAno());
            st.setInt(3, recibo.getSequencial());
            st.setString(4, recibo.getDataPagamento().toString());
            st.setString(5, recibo.getFormaPagamento().name());
            st.setString(6, recibo.getDocumento());
            st.setLong(7, recibo.getEncargos().getDiasAtraso());
            st.setInt(8, recibo.getEncargos().getMeses());
            st.setString(9, recibo.getEncargos().getMulta().toPlainString());
            st.setString(10, recibo.getEncargos().getJuros().toPlainString());
            st.setString(11, recibo.getEncargos().getCorrecao().toPlainString());
            st.setString(12, recibo.getValorTotal().toPlainString());
            st.setLong(13, recibo.getEmitidoPor().getId());
            st.setString(14, recibo.getDataEmissao().toString());
            st.executeUpdate();
        }
    }

    private void inserirDispensa(Connection conexao, long reciboId, DispensaEncargos d) throws SQLException {
        String sql = "INSERT INTO recibo_dispensa (recibo_id, multa, juros, correcao, motivo, autorizado_por, "
                + "autorizado_em) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, reciboId);
            st.setString(2, d.getMulta().toPlainString());
            st.setString(3, d.getJuros().toPlainString());
            st.setString(4, d.getCorrecao().toPlainString());
            st.setString(5, d.getMotivo());
            st.setLong(6, d.getAutorizadoPor().getId());
            st.setString(7, d.getAutorizadoEm().toString());
            st.executeUpdate();
        }
    }

    /**
     * O recibo de um aluguel já recebido, para mostrar, imprimir ou salvar em PDF (BOARD-60).
     * Vazio quando o aluguel não tem recibo (ex.: os pagos dos dados de teste).
     */
    public Optional<Recibo> buscarDoAluguel(Aluguel aluguel) throws SQLException {
        String sql = "SELECT r.*, u.nome AS usuario_nome, u.email AS usuario_email, u.perfil AS usuario_perfil, "
                + "d.recibo_id AS dispensa_recibo, d.multa AS dispensa_multa, d.juros AS dispensa_juros, "
                + "d.correcao AS dispensa_correcao, d.motivo AS dispensa_motivo, "
                + "d.autorizado_por AS dispensa_autor_id, d.autorizado_em AS dispensa_em, "
                + "ud.nome AS dispensa_autor_nome, ud.email AS dispensa_autor_email, "
                + "ud.perfil AS dispensa_autor_perfil "
                + "FROM recibo r JOIN usuario u ON u.id = r.emitido_por "
                + "LEFT JOIN recibo_dispensa d ON d.recibo_id = r.id "
                + "LEFT JOIN usuario ud ON ud.id = d.autorizado_por "
                + "WHERE r.aluguel_id = ?";
        try (Connection conexao = Database.conectar(); PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setLong(1, aluguel.getId());
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Usuario emitidoPor = new Usuario(rs.getString("usuario_nome"), rs.getString("usuario_email"),
                        Perfil.valueOf(rs.getString("usuario_perfil")));
                emitidoPor.setId(rs.getLong("emitido_por"));
                Encargos encargos = Encargos.doBanco(rs.getLong("dias_atraso"), rs.getInt("meses_encargos"),
                        new BigDecimal(rs.getString("multa")), new BigDecimal(rs.getString("juros")),
                        new BigDecimal(rs.getString("correcao")));
                Recibo recibo = new Recibo(rs.getInt("ano"), rs.getInt("sequencial"), aluguel,
                        LocalDate.parse(rs.getString("data_pagamento")),
                        FormaPagamento.valueOf(rs.getString("forma_pagamento")), encargos,
                        rs.getString("documento"), emitidoPor, lerDispensa(rs));
                recibo.setId(rs.getLong("id"));
                recibo.setDataEmissao(LocalDateTime.parse(rs.getString("data_emissao")));
                return Optional.of(recibo);
            }
        }
    }

    /** A dispensa de encargos do recibo (BOARD-66), ou null se a consulta não achou dispensa. */
    private DispensaEncargos lerDispensa(ResultSet rs) throws SQLException {
        rs.getLong("dispensa_recibo");
        if (rs.wasNull()) {
            return null;
        }
        Usuario autor = new Usuario(rs.getString("dispensa_autor_nome"), rs.getString("dispensa_autor_email"),
                Perfil.valueOf(rs.getString("dispensa_autor_perfil")));
        autor.setId(rs.getLong("dispensa_autor_id"));
        return new DispensaEncargos(new BigDecimal(rs.getString("dispensa_multa")),
                new BigDecimal(rs.getString("dispensa_juros")), new BigDecimal(rs.getString("dispensa_correcao")),
                rs.getString("dispensa_motivo"), autor, LocalDateTime.parse(rs.getString("dispensa_em")));
    }

    private long buscarId(Connection conexao, int ano, int sequencial) throws SQLException {
        try (PreparedStatement st = conexao.prepareStatement(
                "SELECT id FROM recibo WHERE ano = ? AND sequencial = ?")) {
            st.setInt(1, ano);
            st.setInt(2, sequencial);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("O banco não devolveu o id do recibo.");
                }
                return rs.getLong(1);
            }
        }
    }
}
