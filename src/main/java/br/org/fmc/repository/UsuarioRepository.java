package br.org.fmc.repository;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Salva e busca usuários na tabela "usuario", com as permissões personalizadas de cada um. */
public class UsuarioRepository implements Repositorio<Usuario> {

    /** Junta as permissões personalizadas (BOARD-41): p.acoes vem nulo para quem segue o perfil. */
    private static final String SELECT = "SELECT u.id, u.nome, u.email, u.senha_hash, u.perfil, u.ativo, p.acoes, "
            + "sp.usuario_id AS provisoria "
            + "FROM usuario u LEFT JOIN usuario_permissoes p ON p.usuario_id = u.id "
            + "LEFT JOIN usuario_senha_provisoria sp ON sp.usuario_id = u.id";

    @Override
    public void salvar(Usuario usuario) throws SQLException {
        if (usuario.getId() == null) {
            inserir(usuario);
        } else {
            atualizar(usuario);
        }
        salvarPermissoes(usuario);
        salvarSenhaProvisoria(usuario);
    }

    private void inserir(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (nome, email, senha_hash, perfil, ativo) VALUES (?, ?, ?, ?, ?)";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(st, u);
            st.executeUpdate();
            try (ResultSet chaves = st.getGeneratedKeys()) {
                if (chaves.next()) {
                    u.setId(chaves.getLong(1));
                }
            }
        }
    }

    private void atualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, perfil = ?, ativo = ? WHERE id = ?";
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            preencher(st, u);
            st.setLong(6, u.getId());
            st.executeUpdate();
        }
    }

    private void preencher(PreparedStatement st, Usuario u) throws SQLException {
        st.setString(1, u.getNome());
        st.setString(2, u.getEmail());
        st.setString(3, u.getSenhaHash());
        st.setString(4, u.getPerfil().name());
        st.setInt(5, u.isAtivo() ? 1 : 0);
    }

    /** Grava as permissões personalizadas; quem segue o perfil fica sem linha em usuario_permissoes. */
    private void salvarPermissoes(Usuario u) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            try (PreparedStatement st = conexao.prepareStatement("DELETE FROM usuario_permissoes WHERE usuario_id = ?")) {
                st.setLong(1, u.getId());
                st.executeUpdate();
            }
            if (u.temPermissoesPersonalizadas()) {
                String acoes = u.getPermissoes().stream().map(Acao::name).collect(Collectors.joining(","));
                try (PreparedStatement st = conexao.prepareStatement(
                        "INSERT INTO usuario_permissoes (usuario_id, acoes) VALUES (?, ?)")) {
                    st.setLong(1, u.getId());
                    st.setString(2, acoes);
                    st.executeUpdate();
                }
            }
        }
    }

    /** BOARD-62: tem linha em usuario_senha_provisoria só quem ainda precisa trocar a senha. */
    private void salvarSenhaProvisoria(Usuario u) throws SQLException {
        try (Connection conexao = Database.conectar()) {
            try (PreparedStatement st = conexao.prepareStatement(
                    "DELETE FROM usuario_senha_provisoria WHERE usuario_id = ?")) {
                st.setLong(1, u.getId());
                st.executeUpdate();
            }
            if (u.temSenhaProvisoria()) {
                try (PreparedStatement st = conexao.prepareStatement(
                        "INSERT INTO usuario_senha_provisoria (usuario_id, criada_em) VALUES (?, ?)")) {
                    st.setLong(1, u.getId());
                    st.setString(2, LocalDateTime.now().withNano(0).toString());
                    st.executeUpdate();
                }
            }
        }
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) throws SQLException {
        return buscarUm(SELECT + " WHERE u.id = ?", id);
    }

    /** O e-mail é procurado em minúsculas, do mesmo jeito que é guardado. */
    public Optional<Usuario> buscarPorEmail(String email) throws SQLException {
        if (email == null) {
            return Optional.empty();
        }
        return buscarUm(SELECT + " WHERE u.email = ?", email.trim().toLowerCase());
    }

    @Override
    public List<Usuario> listarTodos() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(SELECT + " ORDER BY u.nome");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(montar(rs));
            }
        }
        return lista;
    }

    /** Já existe usuário com este e-mail? (sem diferenciar maiúsculas) */
    public boolean existeEmail(String email) throws SQLException {
        return buscarPorEmail(email).isPresent();
    }

    /** Quantos usuários existem (usado para criar o Administrador inicial). */
    public int contar() throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement("SELECT COUNT(*) FROM usuario");
             ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Optional<Usuario> buscarUm(String sql, Object parametro) throws SQLException {
        try (Connection conexao = Database.conectar();
             PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setObject(1, parametro);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(montar(rs)) : Optional.empty();
            }
        }
    }

    /** Transforma uma linha do banco num objeto Usuario. */
    private Usuario montar(ResultSet rs) throws SQLException {
        Usuario u = new Usuario(rs.getString("nome"), rs.getString("email"), Perfil.valueOf(rs.getString("perfil")));
        u.setId(rs.getLong("id"));
        u.setSenhaHash(rs.getString("senha_hash"));
        u.setAtivo(rs.getInt("ativo") == 1);
        u.setSenhaProvisoria(rs.getObject("provisoria") != null);
        String acoes = rs.getString("acoes");
        if (acoes != null) {
            u.personalizarPermissoes(lerAcoes(acoes));
        }
        return u;
    }

    /**
     * Nomes das permissões antes de serem divididas por tela e por ação (BOARD-41, 2ª versão).
     * Ajustes gravados com os nomes antigos continuam valendo, traduzidos para os novos.
     */
    private static final Map<String, Set<Acao>> NOMES_ANTIGOS = Map.of(
            "VER_CADASTROS", EnumSet.of(Acao.VER_IMOVEIS, Acao.VER_INQUILINOS, Acao.VER_CONTRATOS),
            "VER_FINANCEIRO", EnumSet.of(Acao.VER_ALUGUEIS),
            "CADASTRAR_IMOVEL", EnumSet.of(Acao.CRIAR_IMOVEL, Acao.EDITAR_IMOVEL),
            "CADASTRAR_INQUILINO", EnumSet.of(Acao.CRIAR_INQUILINO, Acao.EDITAR_INQUILINO),
            "CADASTRAR_CONTRATO", EnumSet.of(Acao.CRIAR_CONTRATO),
            "GERENCIAR_USUARIOS", EnumSet.of(Acao.VER_USUARIOS, Acao.CRIAR_USUARIO, Acao.EDITAR_PERMISSOES));

    /** "VER_IMOVEIS,VER_INQUILINOS" vira o conjunto de ações. Nomes que não existem mais são ignorados. */
    private static Set<Acao> lerAcoes(String texto) {
        Set<Acao> acoes = EnumSet.noneOf(Acao.class);
        for (String nome : texto.split(",")) {
            String n = nome.trim();
            if (NOMES_ANTIGOS.containsKey(n)) {
                acoes.addAll(NOMES_ANTIGOS.get(n));
            } else {
                Arrays.stream(Acao.values()).filter(a -> a.name().equals(n)).findFirst().ifPresent(acoes::add);
            }
        }
        return acoes;
    }
}
