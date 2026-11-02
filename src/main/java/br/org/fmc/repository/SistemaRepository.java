package br.org.fmc.repository;

import br.org.fmc.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lê as configurações gravadas na tabela "sistema" (ex.: a versão do schema).
 *
 * Serve também de exemplo de repositório: SQL só aqui, sempre com
 * PreparedStatement e try-with-resources.
 */
public class SistemaRepository {

    /**
     * O banco está "novo"? Sem imóveis, sem inquilinos e, no máximo, o Administrador inicial.
     * Usado para carregar os dados de teste sozinho na primeira vez que o sistema abre.
     */
    public boolean bancoSemDados() throws SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM imovel) + (SELECT COUNT(*) FROM inquilino) AS cadastros, "
                + "(SELECT COUNT(*) FROM usuario) AS usuarios";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            return rs.next() && rs.getInt("cadastros") == 0 && rs.getInt("usuarios") <= 1;
        }
    }

    /**
     * Apaga os dados de todas as tabelas (menos a "sistema"), deixando o banco vazio
     * para recarregar os dados de teste (BOARD-52). As tabelas continuam existindo.
     * Cuidado: é irreversível. Só é chamado pela carga de dados de teste.
     */
    public void apagarTodosOsDados() throws SQLException {
        try (Connection conexao = Database.conectar(); Statement st = conexao.createStatement()) {
            st.execute("PRAGMA foreign_keys = OFF"); // apaga em qualquer ordem, sem esbarrar nas ligações
            List<String> tabelas = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                    + "AND name <> 'sistema' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tabelas.add(rs.getString(1));
                }
            }
            for (String tabela : tabelas) {
                st.execute("DELETE FROM \"" + tabela + "\"");
            }
            st.execute("DELETE FROM sqlite_sequence"); // os ids voltam a começar do 1
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

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
