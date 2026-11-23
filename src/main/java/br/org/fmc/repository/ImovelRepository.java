package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistência dos imóveis, preservando as subclasses (Casa, Apartamento), e do arquivamento. */
public class ImovelRepository implements Repositorio<Imovel> {
    /** Junta o arquivamento: arquivado_em vem nulo para quem não está arquivado. */
    private static final String SELECT = "SELECT i.*, a.arquivado_em, a.motivo FROM imovel i "
            + "LEFT JOIN imovel_arquivado a ON a.imovel_id = i.id";

    /** Grava se o imóvel está arquivado ou não (tabela imovel_arquivado). */
    public void salvarArquivamento(Imovel imovel) throws SQLException {
        try (Connection c = Database.conectar()) {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM imovel_arquivado WHERE imovel_id = ?")) {
                s.setLong(1, imovel.getId());
                s.executeUpdate();
            }
            if (imovel.estaArquivado()) {
                try (PreparedStatement s = c.prepareStatement(
                        "INSERT INTO imovel_arquivado (imovel_id, arquivado_em, motivo) VALUES (?, ?, ?)")) {
                    s.setLong(1, imovel.getId());
                    s.setString(2, imovel.getArquivadoEm().toString());
                    s.setString(3, imovel.getMotivoArquivamento());
                    s.executeUpdate();
                }
            }
        }
    }

    public boolean existeUnidade(String unidade, Long idAtual) throws SQLException {
        String sql = "SELECT 1 FROM imovel WHERE trim(unidade) = ? COLLATE NOCASE AND (? IS NULL OR id <> ?)";
        try (Connection c = Database.conectar(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, unidade.trim());
            s.setObject(2, idAtual);
            s.setObject(3, idAtual);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    public boolean existeCodigo(String codigo, Long idAtual) throws SQLException {
        String sql = "SELECT 1 FROM imovel WHERE codigo = ? COLLATE NOCASE AND (? IS NULL OR id <> ?)";
        try (Connection c = Database.conectar(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, codigo.trim());
            s.setObject(2, idAtual);
            s.setObject(3, idAtual);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    @Override
    public void salvar(Imovel i) throws SQLException {
        boolean novo = i.getId() == null;
        String sql = novo
                ? "INSERT INTO imovel (codigo,tipo,endereco,bairro,grupo,valor_aluguel,taxa_luz,valor_iptu,situacao,unidade) VALUES (?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE imovel SET codigo=?,tipo=?,endereco=?,bairro=?,grupo=?,valor_aluguel=?,taxa_luz=?,valor_iptu=?,situacao=?,unidade=? WHERE id=?";
        try (Connection c = Database.conectar();
             PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, i.getCodigo());
            s.setString(2, i.getTipo().name());
            s.setString(3, i.getEndereco());
            s.setString(4, i.getBairro());
            s.setString(5, i.getGrupo());
            s.setString(6, i.getValorAluguel().toPlainString());
            s.setString(7, i.getTaxaLuz().toPlainString());
            s.setString(8, i.getValorIptu().toPlainString());
            s.setString(9, i.getSituacao().name());
            s.setString(10, i.getUnidade());
            if (!novo) s.setLong(11, i.getId());
            if (s.executeUpdate() != 1) throw new SQLException("Imóvel não encontrado.");
            if (novo) try (ResultSet keys = s.getGeneratedKeys()) {
                if (keys.next()) i.setId(keys.getLong(1));
            }
        }
    }

    /**
     * Imóvel ocupado com taxa de luz ou IPTU novos (ex.: o IPTU muda todo ano). Grava o imóvel e,
     * na mesma transação, leva os valores novos ao contrato ativo e aos aluguéis dele ainda não pagos
     * que vencem a partir de "desde". Os aluguéis que já venceram ficam como estavam: foram cobrados
     * com o valor da época.
     *
     * @return quantos aluguéis em aberto mudaram
     */
    public int salvarComLuzEIptuNoContrato(Imovel i, java.time.LocalDate desde) throws SQLException {
        try (Connection c = Database.conectar()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("UPDATE imovel SET codigo=?,tipo=?,endereco=?,bairro=?,"
                        + "grupo=?,valor_aluguel=?,taxa_luz=?,valor_iptu=?,situacao=?,unidade=? WHERE id=?")) {
                    s.setString(1, i.getCodigo());
                    s.setString(2, i.getTipo().name());
                    s.setString(3, i.getEndereco());
                    s.setString(4, i.getBairro());
                    s.setString(5, i.getGrupo());
                    s.setString(6, i.getValorAluguel().toPlainString());
                    s.setString(7, i.getTaxaLuz().toPlainString());
                    s.setString(8, i.getValorIptu().toPlainString());
                    s.setString(9, i.getSituacao().name());
                    s.setString(10, i.getUnidade());
                    s.setLong(11, i.getId());
                    if (s.executeUpdate() != 1) throw new SQLException("Imóvel não encontrado.");
                }
                try (PreparedStatement s = c.prepareStatement(
                        "UPDATE contrato SET taxa_luz=?, valor_iptu=? WHERE imovel_id=? AND status='ATIVO'")) {
                    s.setString(1, i.getTaxaLuz().toPlainString());
                    s.setString(2, i.getValorIptu().toPlainString());
                    s.setLong(3, i.getId());
                    s.executeUpdate();
                }
                int alugueis;
                try (PreparedStatement s = c.prepareStatement("UPDATE aluguel SET taxa_luz=?, valor_iptu=? "
                        + "WHERE data_pagamento IS NULL AND vencimento >= ? AND contrato_id IN "
                        + "(SELECT id FROM contrato WHERE imovel_id=? AND status='ATIVO')")) {
                    s.setString(1, i.getTaxaLuz().toPlainString());
                    s.setString(2, i.getValorIptu().toPlainString());
                    s.setString(3, desde.toString());
                    s.setLong(4, i.getId());
                    alugueis = s.executeUpdate();
                }
                c.commit();
                return alugueis;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    @Override
    public Optional<Imovel> buscarPorId(long id) throws SQLException {
        try (Connection c = Database.conectar();
             PreparedStatement s = c.prepareStatement(SELECT + " WHERE i.id = ?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(montar(r)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Imovel> listarTodos() throws SQLException {
        List<Imovel> itens = new ArrayList<>();
        try (Connection c = Database.conectar();
             PreparedStatement s = c.prepareStatement(SELECT + " ORDER BY i.codigo");
             ResultSet r = s.executeQuery()) {
            while (r.next()) {
                itens.add(montar(r));
            }
        }
        return itens;
    }

    /** Transforma uma linha do banco num objeto Imovel (Casa, Apartamento ou Imovel). */
    private Imovel montar(ResultSet r) throws SQLException {
        TipoImovel tipo = TipoImovel.valueOf(r.getString("tipo"));
        String codigo = r.getString("codigo"), endereco = r.getString("endereco");
        BigDecimal aluguel = new BigDecimal(r.getString("valor_aluguel"));
        Imovel i = switch (tipo) {
            case CASA -> new Casa(codigo, endereco, aluguel);
            case APARTAMENTO -> new Apartamento(codigo, endereco, aluguel);
            default -> new Imovel(codigo, tipo, endereco, aluguel);
        };
        i.setId(r.getLong("id"));
        i.setUnidade(r.getString("unidade"));
        i.setBairro(r.getString("bairro"));
        i.setGrupo(r.getString("grupo"));
        i.setTaxaLuz(new BigDecimal(r.getString("taxa_luz")));
        i.setValorIptu(new BigDecimal(r.getString("valor_iptu")));
        i.setSituacao(SituacaoImovel.valueOf(r.getString("situacao")));
        String arquivadoEm = r.getString("arquivado_em");
        if (arquivadoEm != null) {
            i.arquivar(java.time.LocalDate.parse(arquivadoEm), r.getString("motivo"));
        }
        return i;
    }
}
