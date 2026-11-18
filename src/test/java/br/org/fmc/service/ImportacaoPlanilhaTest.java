package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.db.Database;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.service.ModeloPlanilha.Aba;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Importar planilha: os dados da Fundação de uma vez, conferidos antes e gravados tudo ou nada. */
class ImportacaoPlanilhaTest {

    @TempDir
    Path pasta;

    private final ImportacaoPlanilha importacao = new ImportacaoPlanilha();

    @BeforeEach
    void bancoVazioComAdministrador() throws Exception {
        Database.usarArquivo(pasta.resolve("teste.db"));
        Database.inicializar();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
    }

    @AfterEach
    void sair() {
        Sessao.encerrar();
    }

    /** A planilha dos dados de teste (124 imóveis, 110 inquilinos, 110 contratos), no modelo do sistema. */
    private Path planilhaDosDadosDeTeste() throws Exception {
        Map<Aba, List<List<Object>>> dados = new EnumMap<>(Aba.class);
        List<List<Object>> imoveis = new ArrayList<>();
        for (String[] c : ServicoDadosTeste.ler("imoveis.csv")) {
            imoveis.add(List.of(c[0], c[1], c[2], c[3], c[4], new BigDecimal(c[5]), new BigDecimal(c[6]),
                    new BigDecimal(c[7]), c[8]));
        }
        dados.put(Aba.IMOVEIS, imoveis);
        List<List<Object>> inquilinos = new ArrayList<>();
        for (String[] c : ServicoDadosTeste.ler("inquilinos.csv")) {
            inquilinos.add(List.of(c[0], c[1], c[2], c[3], c[4], c[5], c[6], new BigDecimal(c[7]), c[8], c[9], c[10],
                    c[11]));
        }
        dados.put(Aba.INQUILINOS, inquilinos);
        Map<String, String[]> fiadores = new HashMap<>();
        ServicoDadosTeste.ler("fiadores.csv").forEach(f -> fiadores.put(f[0], f));
        Map<String, String[]> caucoes = new HashMap<>();
        ServicoDadosTeste.ler("caucoes.csv").forEach(k -> caucoes.put(k[0], k));
        List<List<Object>> contratos = new ArrayList<>();
        for (String[] c : ServicoDadosTeste.ler("contratos.csv")) {
            String[] f = fiadores.get(c[0]);
            String[] k = caucoes.get(c[0]);
            List<Object> linha = new ArrayList<>(List.of(c[1], c[2], LocalDate.parse(c[3]), c[4],
                    f != null ? "Fiador" : "Caução"));
            if (f != null) { // nome, cpf, celular, estado civil, renda, endereço, cônjuge, cpf do cônjuge
                linha.addAll(List.of(f[1], f[2], f[3], f[4], new BigDecimal(f[5]), f[8], f[6], f[7], "", "", "", ""));
            } else {
                linha.addAll(List.of("", "", "", "", "", "", "", "", new BigDecimal(k[1]), LocalDate.parse(k[2]), k[3],
                        k[4]));
            }
            contratos.add(linha);
        }
        dados.put(Aba.CONTRATOS, contratos);
        Path arquivo = pasta.resolve("dados-da-fundacao.xlsx");
        ModeloPlanilha.escrever(arquivo, dados);
        return arquivo;
    }

    @Test
    void importaOsDadosDaFundacaoDeUmaVez() throws Exception {
        Path planilha = planilhaDosDadosDeTeste();

        ImportacaoPlanilha.Resultado conferido = importacao.conferir(planilha);
        assertTrue(conferido.semProblemas(), conferido.problemas().toString());
        assertEquals(124, conferido.imoveis());
        assertEquals(110, conferido.inquilinos());
        assertEquals(110, conferido.contratos());
        assertTrue(new ServicoImoveis().listar().isEmpty()); // conferir não grava nada

        ImportacaoPlanilha.Resultado importado = importacao.importar(planilha);
        assertTrue(importado.semProblemas(), importado.problemas().toString());
        assertEquals(124, new ServicoImoveis().listar().size());
        assertEquals(110, new ServicoInquilinos().listar().size());
        assertEquals(110, new ServicoContratos().listar().size());
        assertEquals(110, new ServicoImoveis().listar().stream()
                .filter(i -> i.getSituacao() == SituacaoImovel.OCUPADO).count()); // o contrato ocupa o imóvel
        // Os aluguéis de antes contam como pagos no papel: nenhum mês vencido antes deste foi gerado.
        YearMonth atual = YearMonth.now();
        List<Aluguel> emAberto = new ServicoAlugueis().listarEmAberto();
        assertTrue(emAberto.stream().noneMatch(a -> a.getCompetencia().isBefore(atual)), emAberto.toString());
    }

    @Test
    void planilhaComErrosMostraAbaELinhaENaoGravaNada() throws Exception {
        Map<Aba, List<List<Object>>> dados = new EnumMap<>(Aba.class);
        dados.put(Aba.IMOVEIS, List.of(
                List.of("IM-001", "Apto 101", "Apartamento", "Rua A, 1", "Centro", new BigDecimal("1000"), "", "", "Vago"),
                List.of("IM-002", "Apto 102", "Kitnet", "Rua A, 1", "Centro", new BigDecimal("900"), "", "", "Vago")));
        dados.put(Aba.INQUILINOS, List.of(
                List.of("Maria Teste", "529.982.247-25", "", "", "Solteiro(a)", "", "", new BigDecimal("5000")),
                List.of("João Teste", "123.456.789-00", "", "", "Solteiro(a)", "", "", new BigDecimal("5000"))));
        dados.put(Aba.CONTRATOS, List.of(
                List.of("IM-999", "529.982.247-25", LocalDate.of(2026, 1, 10), "12", "Fiador")));
        Path planilha = pasta.resolve("com-erros.xlsx");
        ModeloPlanilha.escrever(planilha, dados);

        ImportacaoPlanilha.Resultado r = importacao.importar(planilha);

        assertFalse(r.semProblemas());
        List<String> locais = r.problemas().stream().map(ImportacaoPlanilha.Problema::local).toList();
        assertTrue(locais.contains("Imóveis, linha 5"), locais.toString()); // IM-002: tipo "Kitnet"
        assertTrue(locais.contains("Inquilinos, linha 5"), locais.toString()); // CPF inválido
        assertTrue(locais.contains("Contratos, linha 4"), locais.toString()); // imóvel IM-999 não existe
        assertTrue(r.problemas().stream().anyMatch(p -> p.mensagem().contains("Kitnet")
                && p.mensagem().contains("Apartamento")), r.problemas().toString());
        // tudo ou nada: nem as linhas certas foram gravadas
        assertTrue(new ServicoImoveis().listar().isEmpty());
        assertTrue(new ServicoInquilinos().listar().isEmpty());
    }

    @Test
    void modeloBaixadoTemAsAbasEALinhaDeExemploEIgnorada() throws Exception {
        Path modelo = pasta.resolve("modelo.xlsx");
        ModeloPlanilha.gerar(modelo);
        assertTrue(Files.size(modelo) > 0);

        ImportacaoPlanilha.Resultado r = importacao.conferir(modelo);

        assertTrue(r.semProblemas(), r.problemas().toString());
        assertEquals(0, r.total()); // só o exemplo, que não conta
    }

    @Test
    void arquivoQueNaoEPlanilhaDaUmAvisoClaro() throws Exception {
        Path texto = pasta.resolve("dados.csv");
        Files.writeString(texto, "codigo;tipo\nIM-1;Casa\n");
        Exception e = assertThrows(java.io.IOException.class, () -> importacao.conferir(texto));
        assertTrue(e.getMessage().contains(".xlsx"), e.getMessage());
    }

    @Test
    void soQuemTemAPermissaoImporta() throws Exception {
        assertTrue(Perfil.ADMINISTRADOR.getPermissoes().contains(br.org.fmc.model.Acao.IMPORTAR_DADOS));
        assertFalse(Perfil.SECRETARIA.getPermissoes().contains(br.org.fmc.model.Acao.IMPORTAR_DADOS));
        new ServicoAcesso().criarUsuario("Teste Secretaria", "secretaria@exemplo.org", Perfil.SECRETARIA, "teste123");
        Sessao.encerrar();
        new ServicoAcesso().login("secretaria@exemplo.org", "teste123");
        Path modelo = pasta.resolve("modelo.xlsx");
        ModeloPlanilha.gerar(modelo);
        assertThrows(AcessoNegadoException.class, () -> importacao.conferir(modelo));
    }
}
