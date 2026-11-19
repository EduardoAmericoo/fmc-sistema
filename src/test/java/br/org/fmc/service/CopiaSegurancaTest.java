package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Perfil;
import br.org.fmc.service.ServicoCopiaSeguranca.Copia;
import br.org.fmc.service.ServicoCopiaSeguranca.Tipo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cópias de segurança: o banco inteiro guardado em data/copias, e de volta quando precisar. */
class CopiaSegurancaTest {

    @TempDir
    Path pasta;

    private final ServicoCopiaSeguranca copias = new ServicoCopiaSeguranca();
    private final ServicoAcesso acesso = new ServicoAcesso();

    @BeforeEach
    void bancoComAdministrador() throws Exception {
        Database.usarArquivo(pasta.resolve("data").resolve("fmc.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private static int usuarios() throws SQLException {
        try (Connection c = Database.conectar(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM usuario")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void copiaNaHoraGuardaOBancoInteiroNaPastaCopias() throws Exception {
        acesso.criarUsuario("Bruna Teste", "bruna@exemplo.org", Perfil.SECRETARIA, "teste123");

        Copia feita = copias.fazerAgora();

        assertEquals(pasta.resolve("data").resolve("copias"), feita.arquivo().getParent());
        assertEquals(Tipo.MANUAL, feita.tipo());
        assertTrue(Files.size(feita.arquivo()) > 0);
        assertEquals(List.of(feita.arquivo()), copias.listar().stream().map(Copia::arquivo).toList());
        copias.conferirQueEhDoSistema(feita.arquivo()); // é um banco do sistema, com as tabelas
    }

    @Test
    void automaticaEhUmaPorDiaEGuardaSoOsUltimos30Dias() throws Exception {
        LocalDate hoje = LocalDate.of(2026, 11, 18);
        for (int i = 34; i >= 0; i--) {
            copias.copiaAutomatica(hoje.minusDays(i));
        }
        copias.copiaAutomatica(hoje); // fechou o sistema duas vezes no mesmo dia: troca a de hoje

        List<String> nomes = copias.listar().stream().map(c -> c.arquivo().getFileName().toString()).sorted().toList();
        assertEquals(ServicoCopiaSeguranca.DIAS_GUARDADOS, nomes.size());
        assertEquals("fmc-2026-10-20-automatica.db", nomes.get(0)); // a mais antiga que ficou
        assertEquals("fmc-2026-11-18-automatica.db", nomes.get(nomes.size() - 1));
    }

    @Test
    void automaticaNaoApagaAsCopiasFeitasNaHora() throws Exception {
        Copia naHora = copias.fazerAgora();
        LocalDate hoje = LocalDate.of(2026, 11, 18);
        for (int i = 40; i >= 0; i--) {
            copias.copiaAutomatica(hoje.minusDays(i));
        }
        assertTrue(Files.exists(naHora.arquivo()));
    }

    @Test
    void restaurarVoltaOsDadosEGuardaComoEstavaAntes() throws Exception {
        Copia antesDoCadastro = copias.fazerAgora();
        acesso.criarUsuario("Bruna Teste", "bruna@exemplo.org", Perfil.SECRETARIA, "teste123");
        assertEquals(2, usuarios());

        Copia comoEstava = copias.restaurar(antesDoCadastro.arquivo());

        assertEquals(1, usuarios()); // a Bruna foi cadastrada depois da cópia
        assertEquals(Tipo.ANTES_DE_RESTAURAR, comoEstava.tipo());
        // dá para desfazer: restaurar a cópia "antes de restaurar" traz a Bruna de volta
        copias.restaurar(comoEstava.arquivo());
        assertEquals(2, usuarios());
    }

    @Test
    void arquivoQueNaoEhCopiaDoSistemaNaoMudaNada() throws Exception {
        Path qualquer = pasta.resolve("planilha.db");
        Files.writeString(qualquer, "isto não é um banco de dados");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> copias.restaurar(qualquer));

        assertTrue(e.getErros().get(0).contains("não é uma cópia do Sistema FMC"), e.getErros().toString());
        assertEquals(1, usuarios());
        assertTrue(copias.listar().isEmpty()); // nem a cópia "antes de restaurar" foi feita
    }

    @Test
    void salvarEmPenDriveGeraUmaCopiaQueDaParaRestaurar() throws Exception {
        Path penDrive = pasta.resolve("pendrive").resolve("Copia Sistema FMC.db");
        Files.createDirectories(penDrive.getParent());
        Files.writeString(penDrive, "cópia antiga com o mesmo nome");

        copias.salvarEm(penDrive);

        copias.conferirQueEhDoSistema(penDrive);
        assertTrue(copias.listar().isEmpty()); // não conta como cópia guardada no computador
    }

    @Test
    void soOAdministradorFazERestauraCopias() throws Exception {
        assertTrue(Perfil.ADMINISTRADOR.getPermissoes().contains(br.org.fmc.model.Acao.RESTAURAR_COPIA_SEGURANCA));
        assertFalse(Perfil.SECRETARIA.getPermissoes().contains(br.org.fmc.model.Acao.FAZER_COPIA_SEGURANCA));
        Copia feita = copias.fazerAgora();
        acesso.criarUsuario("Bruna Teste", "bruna@exemplo.org", Perfil.SECRETARIA, "teste123");
        Sessao.encerrar();
        acesso.login("bruna@exemplo.org", "teste123");

        assertThrows(AcessoNegadoException.class, () -> copias.fazerAgora());
        assertThrows(AcessoNegadoException.class, () -> copias.restaurar(feita.arquivo()));
    }
}
