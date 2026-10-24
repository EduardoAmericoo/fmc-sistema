package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Persistencia dos campos de Imovel, preservando suas subclasses. */
public class ImovelRepository {
    public void excluir(long id) throws SQLException {
        try (Connection c = Database.conectar();
             PreparedStatement s = c.prepareStatement("DELETE FROM imovel WHERE id = ?")) {
            s.setLong(1, id);
            if (s.executeUpdate() != 1) throw new IllegalArgumentException("Imóvel não encontrado.");
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

    public void salvar(Imovel i) throws SQLException {
        boolean novo = i.getId() == null;
        String sql = novo
                ? "INSERT INTO imovel (codigo,tipo,endereco,bairro,grupo,valor_aluguel,taxa_luz,valor_iptu,situacao,unidade,nome_inquilino,contrato_ate) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE imovel SET codigo=?,tipo=?,endereco=?,bairro=?,grupo=?,valor_aluguel=?,taxa_luz=?,valor_iptu=?,situacao=?,unidade=?,nome_inquilino=?,contrato_ate=? WHERE id=?";
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
            s.setString(11, i.getNomeInquilino());
            s.setString(12, i.getContratoAte() == null ? null : i.getContratoAte().toString());
            if (!novo) s.setLong(13, i.getId());
            if (s.executeUpdate() != 1) throw new SQLException("Imóvel não encontrado.");
            if (novo) try (ResultSet keys = s.getGeneratedKeys()) {
                if (keys.next()) i.setId(keys.getLong(1));
            }
        }
    }

    public List<Imovel> listarTodos() throws SQLException {
        List<Imovel> itens = new ArrayList<>();
        try (Connection c = Database.conectar();
             PreparedStatement s = c.prepareStatement("SELECT * FROM imovel ORDER BY codigo");
             ResultSet r = s.executeQuery()) {
            while (r.next()) {
                TipoImovel tipo = TipoImovel.valueOf(r.getString("tipo"));
                String codigo = r.getString("codigo"), endereco = r.getString("endereco");
                BigDecimal aluguel = new BigDecimal(r.getString("valor_aluguel"));
                Imovel i = switch (tipo) {
                    case CASA -> new Casa(codigo, endereco, aluguel);
                    case APARTAMENTO -> new Apartamento(codigo, endereco, aluguel);
                    default -> new Imovel(codigo, tipo, endereco, aluguel);
                };
                i.setId(r.getLong("id"));
                i.setNomeInquilino(r.getString("nome_inquilino"));
                String ate = r.getString("contrato_ate");
                i.setContratoAte(ate == null ? null : java.time.LocalDate.parse(ate));
                String unidade = r.getString("unidade");
                if (unidade != null && !unidade.isBlank()) i.setUnidade(unidade);
                i.setBairro(r.getString("bairro"));
                i.setGrupo(r.getString("grupo"));
                i.setTaxaLuz(new BigDecimal(r.getString("taxa_luz")));
                i.setValorIptu(new BigDecimal(r.getString("valor_iptu")));
                i.setSituacao(SituacaoImovel.valueOf(r.getString("situacao")));
                itens.add(i);
            }
        }
        return itens;
    }
}
