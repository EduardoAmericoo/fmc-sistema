package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/** Grava o recibo e marca o aluguel como pago na mesma transação. */
public class ReciboRepository {

    public Recibo registrar(Aluguel aluguel, LocalDate dataPagamento, FormaPagamento formaPagamento,
            Encargos encargos, String documento, Usuario usuario) throws SQLException {
        int ano = dataPagamento.getYear();
        try (Connection conexao = Database.conectar()) {
            conexao.setAutoCommit(false);
            try {
                int sequencial = proximoSequencial(conexao, ano);
                Recibo recibo = new Recibo(ano, sequencial, aluguel, dataPagamento, formaPagamento,
                        encargos, documento, usuario);
                registrarPagamento(conexao, aluguel, dataPagamento);
                inserir(conexao, recibo);
                conexao.commit();
                aluguel.registrarPagamento(dataPagamento);
                recibo.setId(buscarId(conexao, ano, sequencial));
                return recibo;
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        }
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
