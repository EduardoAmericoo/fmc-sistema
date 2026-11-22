package br.org.fmc.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Onde fica a pasta de dados: data/ pelo código, %LOCALAPPDATA%\FMC\data no FMC.exe. */
class PastaDadosTest {

    @Test
    void pelo_codigo_usa_a_pasta_data_do_projeto() {
        assertEquals(Path.of("data"), Database.pastaPadrao(null, "C:\\Users\\maria\\AppData\\Local"));
    }

    @Test
    void no_programa_pronto_usa_a_pasta_do_usuario() {
        assertEquals(Path.of("C:\\Users\\maria\\AppData\\Local", "FMC", "data"),
                Database.pastaPadrao("C:\\Program Files\\FMC\\FMC.exe", "C:\\Users\\maria\\AppData\\Local"));
    }

    @Test
    void sem_localappdata_volta_para_a_pasta_data() {
        assertEquals(Path.of("data"), Database.pastaPadrao("C:\\Program Files\\FMC\\FMC.exe", null));
        assertEquals(Path.of("data"), Database.pastaPadrao("C:\\Program Files\\FMC\\FMC.exe", " "));
    }
}
