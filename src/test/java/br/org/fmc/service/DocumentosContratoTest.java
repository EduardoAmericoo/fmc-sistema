package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.DocumentoContrato;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoDocumento;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ImovelRepository;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Documentos digitalizados do contrato. Dados fictícios.
 * O banco e a pasta data/documentos ficam numa pasta temporária; os arquivos "escolhidos"
 * ficam em outra (como se fosse um pendrive), para provar que o sistema guarda uma cópia.
 */
class DocumentosContratoTest {

    private static final LocalDate HOJE = LocalDate.of(2025, 7, 1);

    @TempDir
    Path dados;
    @TempDir
    Path pendrive;

    private final ServicoDocumentos servico = new ServicoDocumentos();
    private Contrato contrato;

    @BeforeEach
    void bancoComUmContrato() throws Exception {
        Database.usarArquivo(dados.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);

        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 52", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        Imovel imovel = new ImovelRepository().listarTodos().get(0);
        Inquilino maria = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25", "",
                "", EstadoCivil.SOLTEIRO, "", "", "3.000,00", "", "", "", ""));
        DadosContrato.Fiador fiador = new DadosContrato.Fiador("José Fiador", "123.456.789-09", "(79) 99999-1111",
                EstadoCivil.SOLTEIRO, "5.000,00", "Rua do Fiador, 10", "", "");
        contrato = new ServicoContratos().cadastrar(new DadosContrato(imovel.getId(), maria.getId(),
                PrazoContrato.MESES_12, LocalDate.of(2025, 1, 10), fiador, null));
        servico.usarRelogio(Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()));
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    private Path arquivo(String nome, int bytes) throws Exception {
        Path p = pendrive.resolve(nome);
        byte[] conteudo = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            conteudo[i] = (byte) (i % 251);
        }
        Files.write(p, conteudo);
        return p;
    }

    private DocumentoContrato enviar(Path origem) throws Exception {
        return servico.enviar(contrato.getId(), TipoDocumento.CONTRATO_ASSINADO, HOJE, origem, "Via assinada");
    }

    private List<String> errosAoEnviar(TipoDocumento tipo, LocalDate data, Path origem) {
        return assertThrows(RegraNegocioException.class,
                () -> servico.enviar(contrato.getId(), tipo, data, origem, null)).getErros();
    }

    @Test
    void enviaECopiaParaDentroDoSistema() throws Exception {
        Path origem = arquivo("Contrato Assinado.pdf", 2048);
        byte[] conteudo = Files.readAllBytes(origem);

        DocumentoContrato doc = enviar(origem);

        assertEquals(TipoDocumento.CONTRATO_ASSINADO, doc.tipo());
        assertEquals(HOJE, doc.data());
        assertEquals("Contrato Assinado.pdf", doc.nomeOriginal());
        assertEquals(2048, doc.tamanho());
        assertEquals("Via assinada", doc.observacao());
        assertEquals(Sessao.usuario().getNome(), doc.enviadoPor());

        Path copia = servico.arquivoParaAbrir(doc.id());
        assertTrue(copia.startsWith(dados.resolve("documentos").toAbsolutePath()));
        assertArrayEquals(conteudo, Files.readAllBytes(copia));
    }

    @Test
    void documentoContinuaAbrindoSeOOriginalForApagado() throws Exception {
        Path origem = arquivo("rg.jpg", 500);
        DocumentoContrato doc = enviar(origem);

        Files.delete(origem); // tirou o pendrive / apagou o original

        Path copia = servico.arquivoParaAbrir(doc.id());
        assertTrue(Files.isRegularFile(copia));
        assertEquals(500, Files.size(copia));
    }

    @Test
    void aceitaPdfJpgJpegEPngInclusiveEmMaiusculas() throws Exception {
        for (String nome : List.of("a.pdf", "b.JPG", "c.jpeg", "d.PNG")) {
            enviar(arquivo(nome, 10));
        }
        assertEquals(4, servico.listar(contrato.getId()).size());
    }

    @Test
    void recusaOutrosTiposDeArquivo() throws Exception {
        for (String nome : List.of("planilha.xlsx", "texto.docx", "foto.heic", "sem-extensao")) {
            List<String> erros = errosAoEnviar(TipoDocumento.OUTRO, HOJE, arquivo(nome, 10));
            assertTrue(erros.get(0).contains("não é aceito"), erros.toString());
            assertTrue(erros.get(0).contains("PDF"), erros.toString());
        }
        assertTrue(servico.listar(contrato.getId()).isEmpty());
    }

    @Test
    void aceitaAte10MbERecusaMaior() throws Exception {
        Path limite = pendrive.resolve("limite.pdf");
        Path grande = pendrive.resolve("grande.pdf");
        try (RandomAccessFile f = new RandomAccessFile(limite.toFile(), "rw")) {
            f.setLength(ServicoDocumentos.TAMANHO_MAXIMO);
        }
        try (RandomAccessFile f = new RandomAccessFile(grande.toFile(), "rw")) {
            f.setLength(ServicoDocumentos.TAMANHO_MAXIMO + 1);
        }

        enviar(limite);
        List<String> erros = errosAoEnviar(TipoDocumento.OUTRO, HOJE, grande);
        assertTrue(erros.get(0).contains("10 MB"), erros.toString());
        assertEquals(1, servico.listar(contrato.getId()).size());
    }

    @Test
    void recusaArquivoVazioOuQueNaoExiste() throws Exception {
        assertTrue(errosAoEnviar(TipoDocumento.OUTRO, HOJE, arquivo("vazio.pdf", 0)).get(0).contains("vazio"));
        assertTrue(errosAoEnviar(TipoDocumento.OUTRO, HOJE, pendrive.resolve("sumiu.pdf")).get(0)
                .contains("não foi encontrado"));
        assertTrue(errosAoEnviar(TipoDocumento.OUTRO, HOJE, null).get(0).contains("Escolha o arquivo"));
    }

    @Test
    void dataNoFuturoNaoPode() throws Exception {
        List<String> erros = errosAoEnviar(TipoDocumento.RG_CPF_INQUILINO, HOJE.plusDays(1), arquivo("rg.pdf", 10));
        assertEquals(List.of("A data do documento não pode ser no futuro."), erros);
        // hoje e no passado pode
        servico.enviar(contrato.getId(), TipoDocumento.RG_CPF_INQUILINO, HOJE.minusYears(3), arquivo("rg.pdf", 10),
                null);
    }

    @Test
    void tipoEDataSaoObrigatorios() throws Exception {
        List<String> erros = errosAoEnviar(null, null, arquivo("x.pdf", 10));
        assertTrue(erros.contains("Escolha o tipo de documento."));
        assertTrue(erros.contains("Informe a data do documento."));
    }

    @Test
    void mesmoNomeDuasVezesNaoSobrescreve() throws Exception {
        DocumentoContrato primeiro = enviar(arquivo("rg.pdf", 10));
        DocumentoContrato segundo = enviar(arquivo("rg.pdf", 20));

        assertNotEquals(primeiro.arquivo(), segundo.arquivo());
        assertEquals(10, Files.size(servico.arquivoParaAbrir(primeiro.id())));
        assertEquals(20, Files.size(servico.arquivoParaAbrir(segundo.id())));
    }

    @Test
    void nomeComAcentoEEspacoViraNomeSeguroNaPastaDoContrato() throws Exception {
        DocumentoContrato doc = enviar(arquivo("Comprovante de renda – João (março).PDF", 10));
        assertEquals(contrato.getNumero() + "/Comprovante-de-renda-Joao-marco.pdf", doc.arquivo());
        assertEquals("Comprovante de renda – João (março).PDF", doc.nomeOriginal());
    }

    @Test
    void arquivarTiraDaListaMasGuardaOArquivoEReativaVolta() throws Exception {
        DocumentoContrato doc = enviar(arquivo("errado.pdf", 10));

        servico.arquivar(doc.id());
        DocumentoContrato lido = servico.listar(contrato.getId()).get(0);
        assertTrue(lido.arquivado());
        assertTrue(Files.isRegularFile(servico.arquivoParaAbrir(doc.id()))); // não apaga o arquivo
        assertThrows(RegraNegocioException.class, () -> servico.arquivar(doc.id()));

        servico.reativar(doc.id());
        assertFalse(servico.listar(contrato.getId()).get(0).arquivado());
        assertThrows(RegraNegocioException.class, () -> servico.reativar(doc.id()));
    }

    @Test
    void naoExisteExcluir() {
        for (var metodo : ServicoDocumentos.class.getMethods()) {
            assertFalse(metodo.getName().toLowerCase().contains("exclu"), metodo.getName());
            assertFalse(metodo.getName().toLowerCase().contains("apagar"), metodo.getName());
        }
    }

    @Test
    void arquivoApagadoForaDoSistemaDaMensagemClara() throws Exception {
        DocumentoContrato doc = enviar(arquivo("rg.pdf", 10));
        Files.delete(servico.arquivoParaAbrir(doc.id()));

        RegraNegocioException e = assertThrows(RegraNegocioException.class,
                () -> servico.arquivoParaAbrir(doc.id()));
        assertTrue(e.getMessage().contains("não foi encontrado"));
    }

    @Test
    void contratoInexistente() throws Exception {
        Path origem = arquivo("rg.pdf", 10);
        assertThrows(RegraNegocioException.class,
                () -> servico.enviar(9999, TipoDocumento.OUTRO, HOJE, origem, null));
    }

    @Test
    void soQuemTemAPermissaoAnexaOuArquiva() throws Exception {
        DocumentoContrato doc = enviar(arquivo("rg.pdf", 10));
        Path outro = arquivo("cpf.pdf", 10);

        Usuario financeiro = new Usuario("Fin Teste", "fin@exemplo.org", Perfil.FINANCEIRO);
        Sessao.iniciar(financeiro);
        assertFalse(Sessao.pode(Acao.ANEXAR_DOCUMENTOS));
        assertThrows(AcessoNegadoException.class,
                () -> servico.enviar(contrato.getId(), TipoDocumento.OUTRO, HOJE, outro, null));
        assertThrows(AcessoNegadoException.class, () -> servico.arquivar(doc.id()));
        // mas consegue ver e abrir
        assertEquals(1, servico.listar(contrato.getId()).size());
        assertTrue(Files.isRegularFile(servico.arquivoParaAbrir(doc.id())));
    }

    @Test
    void secretariaPodeAnexarPorPadrao() {
        assertTrue(Perfil.SECRETARIA.permite(Acao.ANEXAR_DOCUMENTOS));
        assertTrue(Perfil.ADMINISTRADOR.permite(Acao.ANEXAR_DOCUMENTOS));
        assertFalse(Perfil.FINANCEIRO.permite(Acao.ANEXAR_DOCUMENTOS));
        assertEquals(Acao.VER_CONTRATOS, Acao.ANEXAR_DOCUMENTOS.getRequisito());
    }

    // ---------- documentos obrigatórios e etapas (como no protótipo) ----------

    @Test
    void contratoComFiadorSemDocumentosTemOitoObrigatoriosNoPapel() throws Exception {
        List<TipoDocumento> faltam = ServicoDocumentos.faltando(contrato, servico.listar(contrato.getId()));

        assertEquals(List.of(TipoDocumento.PROPOSTA_LOCACAO, TipoDocumento.RG_CPF_INQUILINO,
                TipoDocumento.COMPROVANTE_RENDA, TipoDocumento.CONSULTA_CPF, TipoDocumento.DOCUMENTOS_FIADOR,
                TipoDocumento.VISTORIA, TipoDocumento.CONTRATO_ASSINADO, TipoDocumento.TERMO_ENTREGA_CHAVES),
                faltam);
        // com fiador, o recibo de caução não é obrigatório; e os de saída só depois de encerrar
        assertFalse(faltam.contains(TipoDocumento.COMPROVANTE_CAUCAO));
        assertFalse(faltam.contains(TipoDocumento.VISTORIA_SAIDA));
    }

    @Test
    void enviandoOsObrigatoriosADocumentacaoFicaCompletaEArquivadoNaoConta() throws Exception {
        DocumentoContrato ultimo = null;
        int n = 0;
        for (TipoDocumento tipo : ServicoDocumentos.faltando(contrato, List.of())) {
            ultimo = servico.enviar(contrato.getId(), tipo, HOJE, arquivo("doc" + (n++) + ".pdf", 100), null);
        }
        assertTrue(ServicoDocumentos.faltando(contrato, servico.listar(contrato.getId())).isEmpty());

        servico.arquivar(ultimo.id());
        assertEquals(List.of(ultimo.tipo()), ServicoDocumentos.faltando(contrato, servico.listar(contrato.getId())));
    }

    @Test
    void todoTipoTemEtapaECadaEtapaTemTipo() {
        for (TipoDocumento.Etapa etapa : TipoDocumento.Etapa.values()) {
            assertTrue(java.util.Arrays.stream(TipoDocumento.values()).anyMatch(t -> t.getEtapa() == etapa),
                    "etapa sem tipo: " + etapa);
        }
        assertEquals(TipoDocumento.Etapa.ENTRADA, TipoDocumento.CONTRATO_ASSINADO.getEtapa());
        assertEquals("Contrato de locação", TipoDocumento.CONTRATO_ASSINADO.getDescricao());
    }
}
