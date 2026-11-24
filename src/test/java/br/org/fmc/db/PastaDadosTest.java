package br.org.fmc.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Onde fica a pasta de dados: data/ pelo código; no programa pronto, a pasta de dados do usuário
 * de cada sistema (Windows, Mac ou Linux).
 */
class PastaDadosTest {

    private static final String LOCAL = "C:\\Users\\maria\\AppData\\Local";

    @Test
    void pelo_codigo_usa_a_pasta_data_do_projeto() {
        assertEquals(Path.of("data"), Database.pastaPadrao(null, "Windows 11", LOCAL, null, null));
        assertEquals(Path.of("data"), Database.pastaPadrao(null, "Mac OS X", null, "/Users/maria", null));
    }

    @Test
    void no_windows_usa_o_localappdata() {
        assertEquals(Path.of(LOCAL, "FMC", "data"),
                Database.pastaPadrao("C:\\Program Files\\FMC\\FMC.exe", "Windows 11", LOCAL, "C:\\Users\\maria", null));
    }

    @Test
    void no_windows_sem_localappdata_volta_para_a_pasta_data() {
        assertEquals(Path.of("data"), Database.pastaPadrao("C:\\FMC\\FMC.exe", "Windows 11", null, null, null));
        assertEquals(Path.of("data"), Database.pastaPadrao("C:\\FMC\\FMC.exe", "Windows 11", " ", null, null));
    }

    @Test
    void no_mac_usa_o_application_support() {
        assertEquals(Path.of("/Users/maria", "Library", "Application Support", "FMC", "data"),
                Database.pastaPadrao("/Applications/FMC.app/Contents/MacOS/FMC", "Mac OS X", null, "/Users/maria", null));
    }

    @Test
    void no_linux_usa_o_local_share_ou_o_xdg() {
        assertEquals(Path.of("/home/maria", ".local", "share", "FMC", "data"),
                Database.pastaPadrao("/opt/fmc/bin/FMC", "Linux", null, "/home/maria", null));
        assertEquals(Path.of("/dados/maria", "FMC", "data"),
                Database.pastaPadrao("/opt/fmc/bin/FMC", "Linux", null, "/home/maria", "/dados/maria"));
    }
}
