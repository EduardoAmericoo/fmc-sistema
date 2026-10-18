package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.org.fmc.db.Database;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServicoSistemaTest {

    @TempDir
    Path pastaTemporaria;

    @Test
    void informaAVersaoDoSchemaGravadaNoBanco() throws Exception {
        Database.usarArquivo(pastaTemporaria.resolve("teste.db"));
        Database.inicializar();

        assertEquals("1", new ServicoSistema().versaoDoBanco());
    }
}
