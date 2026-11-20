package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.EtapaVistoria;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.FotoVistoria;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Vistorias com fotos: as fotos da entrega e da devolução das chaves guardadas como prova. */
class VistoriasTest {

    @TempDir
    Path pasta;

    private final ServicoVistorias vistorias = new ServicoVistorias();
    private final ServicoAcesso acesso = new ServicoAcesso();
    private long contratoId;

    @BeforeEach
    void contratoDaMaria() throws Exception {
        Database.usarArquivo(pasta.resolve("data").resolve("fmc.db"));
        Database.inicializar();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        LocalDate chaves = LocalDate.of(2026, 6, 1);
        new ServicoImoveis().salvarFormulario(null, "IM-001", "", TipoImovel.CASA, "Rua Exemplo, 1", "Centro", "",
                "1.000,00", "50,00", "20,00", SituacaoImovel.VAGO);
        long imovel = new ImovelRepository().listarTodos().get(0).getId();
        long inquilino = new ServicoInquilinos().cadastrar(new DadosInquilino("Maria Teste", "529.982.247-25",
                "", "", EstadoCivil.SOLTEIRO, "", "", "5.000,00", "", "", "", "")).getId();
        new ServicoContratos().cadastrar(new DadosContrato(imovel, inquilino, PrazoContrato.MESES_12, chaves, null,
                new DadosContrato.Caucao("3.000,00", chaves, FormaPagamento.PIX, "")));
        contratoId = new ContratoRepository().listarTodos().get(0).getId();
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    /** Uma "foto" de teste: um retângulo colorido, salvo em PNG ou JPG. */
    private Path foto(String nome, int largura, int altura) throws Exception {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(new Color(0x8F, 0xB5, 0xDA));
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        Path arquivo = pasta.resolve("celular").resolve(nome);
        Files.createDirectories(arquivo.getParent());
        ImageIO.write(imagem, nome.endsWith(".png") ? "png" : "jpg", arquivo.toFile());
        return arquivo;
    }

    /** Uma foto JPG como o celular grava: deitada, com a anotação "girar 90°" e a data no EXIF. */
    private Path fotoDoCelularDeitada(String nome, String data) throws Exception {
        Path semExif = foto("sem-exif-" + nome, 400, 300);
        byte[] jpg = Files.readAllBytes(semExif);
        ByteBuffer tiff = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2); // IFD0: orientação e ponteiro para o EXIF
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) 6).putShort((short) 0);
        tiff.putShort((short) 0x8769).putShort((short) 4).putInt(1).putInt(38);
        tiff.putInt(0);
        tiff.putShort((short) 1); // IFD do EXIF (offset 38): data original
        tiff.putShort((short) 0x9003).putShort((short) 2).putInt(20).putInt(56);
        tiff.putInt(0);
        tiff.put((data + "\0").getBytes(StandardCharsets.US_ASCII)); // offset 56
        byte[] tiffBytes = java.util.Arrays.copyOf(tiff.array(), tiff.position());
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpg, 0, 2); // SOI
        int tamanho = 2 + 6 + tiffBytes.length;
        saida.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (tamanho >> 8), (byte) tamanho});
        saida.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        saida.write(tiffBytes);
        saida.write(jpg, 2, jpg.length - 2);
        Path arquivo = pasta.resolve("celular").resolve(nome);
        Files.write(arquivo, saida.toByteArray());
        return arquivo;
    }

    @Test
    void fotoEntraReduzidaComLacreDataEQuemRegistrou() throws Exception {
        List<FotoVistoria> salvas = vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "Pintura nova",
                List.of(foto("sala.png", 4000, 3000)));

        FotoVistoria f = salvas.get(0);
        Path guardada = ServicoVistorias.caminho(f);
        BufferedImage imagem = ImageIO.read(guardada.toFile());
        assertEquals(ServicoVistorias.LADO_MAXIMO, imagem.getWidth()); // reduzida, mantendo a proporção
        assertEquals(1200, imagem.getHeight());
        assertTrue(f.arquivo().startsWith("vistorias/contrato-" + contratoId + "/entrada/"), f.arquivo());
        assertEquals("Sala", f.comodo());
        assertEquals("Pintura nova", f.observacao());
        assertEquals("sala.png", f.nomeOriginal());
        assertEquals(64, f.hashSha256().length());
        assertTrue(ServicoVistorias.lacreConfere(f));
        assertEquals(Sessao.usuario().getNome(), f.registradaPor());
        assertEquals(1, vistorias.ativas(contratoId, EtapaVistoria.ENTRADA).size());
    }

    @Test
    void fotoDoCelularDeitadaFicaEmPeEGuardaADataEmQueFoiTirada() throws Exception {
        FotoVistoria f = vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Cozinha", "",
                List.of(fotoDoCelularDeitada("cozinha.jpg", "2026:06:01 09:30:15"))).get(0);

        BufferedImage imagem = ImageIO.read(ServicoVistorias.caminho(f).toFile());
        assertEquals(300, imagem.getWidth()); // era 400 x 300, deitada; agora em pé
        assertEquals(400, imagem.getHeight());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 30, 15), f.tiradaEm());
        assertEquals(null, f.observacao()); // observação em branco não é guardada
    }

    @Test
    void lacreAcusaFotoEditadaDepoisDoRegistro() throws Exception {
        FotoVistoria f = vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Banheiro", "",
                List.of(foto("banheiro.jpg", 800, 600))).get(0);
        Path guardada = ServicoVistorias.caminho(f);
        byte[] bytes = Files.readAllBytes(guardada);
        bytes[bytes.length / 2] ^= 0x7F; // alguém "retocou" a foto
        Files.write(guardada, bytes);

        assertFalse(ServicoVistorias.lacreConfere(f));
    }

    @Test
    void seUmaFotoNaoPodeEntrarNenhumaEntra() throws Exception {
        Path boa = foto("quarto.jpg", 800, 600);
        Path falsa = pasta.resolve("celular").resolve("documento.jpg");
        Files.writeString(falsa, "isto não é uma foto");

        RegraNegocioException e = assertThrows(RegraNegocioException.class, () -> vistorias.adicionar(contratoId,
                EtapaVistoria.ENTRADA, "Quarto 1", "", List.of(boa, falsa)));

        assertTrue(e.getErros().get(0).contains("documento.jpg"), e.getErros().toString());
        assertTrue(vistorias.listar(contratoId).isEmpty());
        assertFalse(Files.exists(ServicoDocumentos.pastaDocumentos().resolve("vistorias/contrato-" + contratoId
                + "/entrada")) && Files.list(ServicoDocumentos.pastaDocumentos().resolve("vistorias/contrato-"
                        + contratoId + "/entrada")).findAny().isPresent());
    }

    @Test
    void comodoEFotoSaoObrigatoriosEFotoDeIphoneExplicaComoMandar() throws Exception {
        RegraNegocioException semComodo = assertThrows(RegraNegocioException.class,
                () -> vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, " ", "", List.of()));
        assertTrue(semComodo.getErros().contains("Informe o cômodo das fotos (ex.: Sala, Cozinha, Banheiro)."));
        assertTrue(semComodo.getErros().contains("Escolha pelo menos uma foto."));

        Path heic = pasta.resolve("IMG_0001.HEIC");
        Files.writeString(heic, "x");
        RegraNegocioException iphone = assertThrows(RegraNegocioException.class,
                () -> vistorias.adicionar(contratoId, EtapaVistoria.SAIDA, "Sala", "", List.of(heic)));
        assertTrue(iphone.getErros().get(0).contains("Mais Compatível"), iphone.getErros().toString());
    }

    @Test
    void comparacaoJuntaEntradaESaidaPorComodoESemAsArquivadas() throws Exception {
        vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "", List.of(foto("s1.jpg", 400, 300)));
        vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Despensa", "", List.of(foto("d1.jpg", 400, 300)));
        vistorias.adicionar(contratoId, EtapaVistoria.SAIDA, "Sala", "Parede riscada",
                List.of(foto("s2.jpg", 400, 300)));
        FotoVistoria engano = vistorias.adicionar(contratoId, EtapaVistoria.SAIDA, "Sala", "",
                List.of(foto("s3.jpg", 400, 300))).get(0);
        vistorias.arquivar(engano.id());

        List<ServicoVistorias.Comodo> comodos = vistorias.comparacao(contratoId);

        assertEquals(List.of("Sala", "Despensa"), comodos.stream().map(ServicoVistorias.Comodo::nome).toList());
        assertEquals(1, comodos.get(0).entrada().size());
        assertEquals(1, comodos.get(0).saida().size()); // a arquivada ficou de fora
        assertEquals("Parede riscada", comodos.get(0).saida().get(0).observacao());
        assertTrue(comodos.get(1).saida().isEmpty());
        assertEquals(5 - 1, vistorias.listar(contratoId).size()); // arquivar não apaga: 4 registradas
        vistorias.reativar(engano.id());
        assertEquals(2, vistorias.comparacao(contratoId).get(0).saida().size());
    }

    @Test
    void laudoEmPdfComAsFotosDaEtapa() throws Exception {
        vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "Pintura nova",
                List.of(foto("a.jpg", 800, 600), foto("b.jpg", 600, 800), foto("c.png", 800, 600)));
        Path pdf = pasta.resolve("laudo.pdf");

        vistorias.laudo(contratoId, EtapaVistoria.ENTRADA, pdf);

        byte[] bytes = Files.readAllBytes(pdf);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 10_000);
        RegraNegocioException semFotos = assertThrows(RegraNegocioException.class,
                () -> vistorias.laudo(contratoId, EtapaVistoria.SAIDA, pasta.resolve("saida.pdf")));
        assertTrue(semFotos.getErros().get(0).contains("ainda não tem fotos"));
    }

    @Test
    void secretariaRegistraEFinanceiroSoVe() throws Exception {
        assertTrue(Perfil.SECRETARIA.getPermissoes().contains(br.org.fmc.model.Acao.REGISTRAR_VISTORIA));
        assertFalse(Perfil.FINANCEIRO.getPermissoes().contains(br.org.fmc.model.Acao.REGISTRAR_VISTORIA));
        vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "", List.of(foto("s.jpg", 400, 300)));
        acesso.criarUsuario("Carlos Financeiro", "financeiro@exemplo.org", Perfil.FINANCEIRO, "teste123");
        Sessao.encerrar();
        acesso.login("financeiro@exemplo.org", "teste123");

        assertEquals(1, vistorias.listar(contratoId).size());
        Path outra = foto("outra.jpg", 400, 300);
        assertThrows(AcessoNegadoException.class,
                () -> vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "", List.of(outra)));
    }

    @Test
    void fotosDaVistoriaEntramNaCopiaDeSeguranca() throws Exception {
        FotoVistoria f = vistorias.adicionar(contratoId, EtapaVistoria.ENTRADA, "Sala", "",
                List.of(foto("s.jpg", 400, 300))).get(0);

        new ServicoCopiaSeguranca().fazerAgora();

        Path espelho = new ServicoCopiaSeguranca().pastaEspelhoDocumentos().resolve(f.arquivo());
        assertTrue(Files.exists(espelho), espelho.toString());
        assertEquals(f.hashSha256(), ServicoVistorias.hash(espelho)); // a cópia é idêntica (mesmo lacre)
    }
}
