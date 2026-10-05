package br.org.fmc.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    @TempDir
    Path pastaTemporaria;

    @Test
    void criaOBancoEGravaELeUmRegistro() throws Exception {
        Database.usarArquivo(pastaTemporaria.resolve("teste.db"));
        Database.inicializar();

        try (Connection conexao = Database.conectar(); Statement st = conexao.createStatement()) {
            st.execute("INSERT INTO sistema (chave, valor) VALUES ('teste', 'ok')");
            try (ResultSet rs = st.executeQuery("SELECT valor FROM sistema WHERE chave = 'teste'")) {
                assertTrue(rs.next());
                assertEquals("ok", rs.getString(1));
            }
        }
    }

    @Test
    void inicializarDuasVezesNaoDaErro() throws Exception {
        Database.usarArquivo(pastaTemporaria.resolve("teste2.db"));
        Database.inicializar();
        Database.inicializar();
    }
}
