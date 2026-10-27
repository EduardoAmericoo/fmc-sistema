package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Contrato;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Salva e busca aluguéis na tabela "aluguel" (BOARD-38).
 *
 * Usado pela lista de aluguéis (BOARD-38) e pela geração dos aluguéis do mês (BOARD-37):
 * a geração cria os aluguéis com salvar(), a lista lê com listarDoMes().
 * Um contrato tem no máximo um aluguel por mês: o banco recusa a repetição (UNIQUE).
 */
public class AluguelRepository {

    private final ContratoRepository contratos = new ContratoRepository();

    /** Aluguel novo: insere. Aluguel já gravado: atualiza outros valores e o pagamento. */
    public void salvar(Aluguel a) throws SQLException {
        if (a.getId() == null) {
            inserir(a);
        } else {
            atualizar(a);
        }
    }

    private void inserir(Aluguel a) throws SQLException {
        String sql = "INSERT INTO aluguel (contrato_id, competencia, vencimento, valor_aluguel, taxa_luz, valor_iptu, "
                + "outros, data_pagamento) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, a.getContrato().getId());
            st.setString(2, a.getCompetencia().toString());
            st.setString(3, a.getVencimento().toString());
            st.setString(4, a.getValorAluguel().toPlainString());
            st.setString(5, a.getTaxaLuz().toPlainString());
            st.setString(6, a.getValorIptu().toPlainString());
            st.setString(7, a.getOutros().toPlainString());
            st.setString(8, a.getDataPagamento() == null ? null : a.getDataPagamento().toString());
            st.executeUpdate();
            try (ResultSet chaves = st.getGeneratedKeys()) {
                if (chaves.next()) {
                    a.setId(chaves.getLong(1));
                }
            }
        }
    }

    private void atualizar(Aluguel a) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "UPDATE aluguel SET outros = ?, data_pagamento = ? WHERE id = ?")) {
            st.setString(1, a.getOutros().toPlainString());
            st.setString(2, a.getDataPagamento() == null ? null : a.getDataPagamento().toString());
            st.setLong(3, a.getId());
            st.executeUpdate();
        }
    }

    /** Os aluguéis que vencem no mês informado, em ordem de vencimento e depois de imóvel. */
    public List<Aluguel> listarDoMes(YearMonth mes) throws SQLException {
        Map<Long, Contrato> porId = new HashMap<>();
        contratos.listarTodos().forEach(c -> porId.put(c.getId(), c));
        List<Aluguel> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "SELECT * FROM aluguel WHERE competencia = ? ORDER BY vencimento, id")) {
            st.setString(1, mes.toString());
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    lista.add(montar(rs, porId.get(rs.getLong("contrato_id"))));
                }
            }
        }
        lista.sort((x, y) -> x.getVencimento().equals(y.getVencimento())
                ? x.getContrato().getImovel().getCodigo().compareTo(y.getContrato().getImovel().getCodigo())
                : x.getVencimento().compareTo(y.getVencimento()));
        return lista;
    }

    /** Os meses que já têm aluguéis gerados, do mais recente para o mais antigo (para o filtro de mês). */
    public List<YearMonth> mesesComAluguel() throws SQLException {
        List<YearMonth> meses = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(
                     "SELECT DISTINCT competencia FROM aluguel ORDER BY competencia DESC");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                meses.add(YearMonth.parse(rs.getString(1)));
            }
        }
        return meses;
    }

    /** Transforma uma linha do banco num objeto Aluguel, com os valores de quando foi gerado. */
    private Aluguel montar(ResultSet rs, Contrato contrato) throws SQLException {
        String pago = rs.getString("data_pagamento");
        Aluguel a = Aluguel.doBanco(contrato, YearMonth.parse(rs.getString("competencia")),
                LocalDate.parse(rs.getString("vencimento")), new BigDecimal(rs.getString("valor_aluguel")),
                new BigDecimal(rs.getString("taxa_luz")), new BigDecimal(rs.getString("valor_iptu")),
                new BigDecimal(rs.getString("outros")), pago == null ? null : LocalDate.parse(pago));
        a.setId(rs.getLong("id"));
        return a;
    }
}
