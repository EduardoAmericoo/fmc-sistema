package br.org.fmc.service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * O modelo de planilha para trazer os dados da Fundação de uma vez (importar planilha).
 *
 * Uma aba "Como preencher" e as abas Imóveis, Inquilinos e Contratos. Cada aba tem o cabeçalho
 * (obrigatórios com *), uma dica embaixo de cada coluna e uma linha de exemplo marcada
 * "(exemplo)", que a importação ignora. As colunas com opções fixas (tipo, estado civil,
 * prazo, garantia...) têm uma lista de escolha, para não haver erro de digitação.
 *
 * É a mesma definição que a ImportacaoPlanilha usa para ler: as colunas são achadas pelo
 * título, então a ordem pode mudar.
 */
public final class ModeloPlanilha {

    /** Tudo o que estiver na 1ª coluna com esta marca é a linha de exemplo, e não é importado. */
    public static final String MARCA_EXEMPLO = "(exemplo)";

    /** Linha 1: títulos; linha 2: dicas; linha 3: exemplo; a partir da 4: os dados. */
    static final int LINHA_TITULOS = 0;
    static final int LINHA_DICAS = 1;
    static final int LINHA_EXEMPLO = 2;
    static final int PRIMEIRA_LINHA_DE_DADOS = 3;
    private static final int ULTIMA_LINHA_COM_LISTA = 2000;

    static final List<String> TIPOS = List.of("Casa", "Apartamento", "Sala comercial", "Loja");
    static final List<String> SITUACOES = List.of("Vago", "Em manutenção");
    static final List<String> ESTADOS_CIVIS = List.of("Solteiro(a)", "Casado(a)", "União estável",
            "Divorciado(a)", "Viúvo(a)");
    static final List<String> PRAZOS = List.of("12", "24", "36");
    static final List<String> GARANTIAS = List.of("Fiador", "Caução");
    static final List<String> FORMAS = List.of("PIX", "Transferência", "Boleto bancário", "Dinheiro", "Cheque");

    /** Uma coluna da planilha: título, se é obrigatória, a dica, o valor de exemplo e as opções (se houver). */
    public record Coluna(String titulo, boolean obrigatoria, String dica, Object exemplo, List<String> opcoes) {
        public String cabecalho() {
            return obrigatoria ? titulo + " *" : titulo;
        }
    }

    private static Coluna col(String titulo, boolean obrigatoria, String dica, Object exemplo) {
        return new Coluna(titulo, obrigatoria, dica, exemplo, List.of());
    }

    private static Coluna lista(String titulo, boolean obrigatoria, String dica, Object exemplo, List<String> opcoes) {
        return new Coluna(titulo, obrigatoria, dica, exemplo, opcoes);
    }

    /** As três abas de dados, na ordem em que são importadas (o contrato precisa do imóvel e do inquilino). */
    public enum Aba {
        IMOVEIS("Imóveis", List.of(
                col("Código", true, "Único para cada imóvel. Ex.: IM-001", "IM-001 " + MARCA_EXEMPLO),
                col("Unidade", false, "Apartamento, bloco ou casa", "Apto 101 · Bloco A"),
                lista("Tipo", true, "Escolha na lista", "Apartamento", TIPOS),
                col("Endereço", true, "Rua e número", "Av. Exemplo, 100"),
                col("Bairro", false, "", "Centro"),
                col("Aluguel (R$)", true, "Valor por mês, só o número", new BigDecimal("1500.00")),
                col("Taxa de luz (R$)", false, "Por mês. Em branco = 0", new BigDecimal("20.00")),
                col("IPTU (R$)", false, "Por mês. Em branco = 0", new BigDecimal("45.00")),
                lista("Situação", true, "Alugado também fica Vago: o contrato o deixa ocupado", "Vago", SITUACOES))),
        INQUILINOS("Inquilinos", List.of(
                col("Nome completo", true, "Como no documento", "Maria Exemplo da Silva " + MARCA_EXEMPLO),
                col("CPF", true, "Com ou sem pontos", "529.982.247-25"),
                col("Celular", false, "Com DDD. Usado no WhatsApp da cobrança", "(79) 99999-0000"),
                col("E-mail", false, "Usado no e-mail da cobrança", "maria@exemplo.com"),
                lista("Estado civil", true, "Escolha na lista", "Solteiro(a)", ESTADOS_CIVIS),
                col("Profissão", false, "", "Professora"),
                col("Local de trabalho", false, "", "Escola Exemplo"),
                col("Renda mensal (R$)", true, "Precisa ser 3 vezes o aluguel (somando cônjuge e outras rendas)",
                        new BigDecimal("4800.00")),
                col("Outras rendas (R$)", false, "Em branco = 0", new BigDecimal("0")),
                col("Cônjuge: nome", false, "Só para casado(a) ou união estável", ""),
                col("Cônjuge: CPF", false, "", ""),
                col("Cônjuge: renda (R$)", false, "", ""))),
        CONTRATOS("Contratos", List.of(
                col("Código do imóvel", true, "O mesmo da aba Imóveis (ou de um já cadastrado)",
                        "IM-001 " + MARCA_EXEMPLO),
                col("CPF do inquilino", true, "O mesmo da aba Inquilinos (ou de um já cadastrado)", "529.982.247-25"),
                col("Entrega das chaves", true, "Data (dd/mm/aaaa). O 1º aluguel vence 30 dias depois",
                        LocalDate.of(2025, 3, 10)),
                lista("Prazo (meses)", true, "12, 24 ou 36", "12", PRAZOS),
                lista("Garantia", true, "Fiador ou Caução: preencha só as colunas da garantia escolhida", "Fiador",
                        GARANTIAS),
                col("Fiador: nome", false, "Obrigatório se a garantia for Fiador", "José Exemplo Santos"),
                col("Fiador: CPF", false, "Diferente do inquilino", "111.444.777-35"),
                col("Fiador: celular", false, "", "(79) 98888-0000"),
                lista("Fiador: estado civil", false, "Escolha na lista", "Casado(a)", ESTADOS_CIVIS),
                col("Fiador: renda (R$)", false, "", new BigDecimal("6000.00")),
                col("Fiador: endereço", false, "", "Rua Exemplo, 50 · Centro · Aracaju/SE"),
                col("Fiador: cônjuge", false, "Obrigatório para fiador casado(a)", "Ana Exemplo Santos"),
                col("Fiador: CPF do cônjuge", false, "", "390.533.447-05"),
                col("Caução: valor (R$)", false, "Obrigatório se a garantia for Caução (até 3 aluguéis)", ""),
                col("Caução: recebida em", false, "Data (dd/mm/aaaa)", ""),
                lista("Caução: forma de pagamento", false, "Escolha na lista", "", FORMAS),
                col("Caução: nº do recibo", false, "", "")));

        private final String nome;
        private final List<Coluna> colunas;

        Aba(String nome, List<Coluna> colunas) {
            this.nome = nome;
            this.colunas = colunas;
        }

        public String getNome() {
            return nome;
        }

        public List<Coluna> getColunas() {
            return colunas;
        }
    }

    private ModeloPlanilha() {
    }

    /** O modelo vazio, só com o exemplo, para a Fundação baixar e preencher. */
    public static void gerar(Path destino) throws IOException {
        escrever(destino, Map.of());
    }

    /** O modelo com linhas de dados (usado nos testes, a partir dos dados de teste). */
    static void escrever(Path destino, Map<Aba, List<List<Object>>> dados) throws IOException {
        try (OutputStream saida = Files.newOutputStream(destino)) {
            Workbook planilha = new Workbook(saida, "Sistema FMC", "1.0");
            escreverInstrucoes(planilha.newWorksheet("Como preencher"));
            for (Aba aba : Aba.values()) {
                escreverAba(planilha.newWorksheet(aba.getNome()), aba, dados.getOrDefault(aba, List.of()));
            }
            planilha.finish();
        }
    }

    private static void escreverInstrucoes(Worksheet ws) {
        String[] linhas = {
            "Como trazer os dados para o Sistema FMC",
            "",
            "1. Preencha as abas Imóveis, Inquilinos e Contratos, uma linha por cadastro, a partir da linha 4.",
            "2. As colunas com * são obrigatórias. Embaixo de cada título há uma dica de como preencher.",
            "3. A linha 3 de cada aba é um exemplo (marcado \"(exemplo)\"): ela é ignorada. Pode apagar ou deixar.",
            "4. Nas colunas com opções (Tipo, Situação, Estado civil, Prazo, Garantia...), escolha na lista.",
            "5. Valores em reais só com o número (ex.: 1500 ou 1.500,00). Datas como dd/mm/aaaa.",
            "6. O contrato usa o Código do imóvel e o CPF do inquilino para achar cada um.",
            "7. Imóvel alugado entra como Vago: ao cadastrar o contrato, o sistema o deixa Ocupado.",
            "8. Os aluguéis de antes da importação contam como já pagos (no papel). O sistema começa a cobrar",
            "   a partir do próximo vencimento de cada contrato.",
            "",
            "No sistema: menu Importar planilha > Escolher planilha. O sistema confere tudo antes e mostra cada",
            "problema com a aba e a linha. Nada é gravado enquanto houver problema: é tudo ou nada.",
        };
        ws.width(0, 110);
        for (int i = 0; i < linhas.length; i++) {
            ws.value(i, 0, linhas[i]);
        }
        ws.style(0, 0).bold().fontColor("1D4D7A").set();
        ws.keepInActiveTab();
    }

    private static void escreverAba(Worksheet ws, Aba aba, List<List<Object>> linhas) {
        List<Coluna> colunas = aba.getColunas();
        for (int c = 0; c < colunas.size(); c++) {
            Coluna coluna = colunas.get(c);
            ws.width(c, Math.max(16, Math.min(40, coluna.cabecalho().length() + 6)));
            ws.value(LINHA_TITULOS, c, coluna.cabecalho());
            ws.style(LINHA_TITULOS, c).bold().fontColor("FFFFFF").fillColor("1D4D7A").set();
            ws.value(LINHA_DICAS, c, coluna.dica());
            ws.style(LINHA_DICAS, c).italic().fontColor("5B6472").wrapText(true).set();
            valor(ws, LINHA_EXEMPLO, c, coluna.exemplo());
            ws.style(LINHA_EXEMPLO, c).italic().fontColor("8A94A6").fillColor("F3F5F8").set();
            if (!coluna.opcoes().isEmpty()) {
                ws.range(PRIMEIRA_LINHA_DE_DADOS, c, ULTIMA_LINHA_COM_LISTA, c)
                        .validateWithListByFormula("\"" + String.join(",", coluna.opcoes()) + "\"");
            }
        }
        for (int i = 0; i < linhas.size(); i++) {
            List<Object> linha = linhas.get(i);
            for (int c = 0; c < linha.size() && c < colunas.size(); c++) {
                valor(ws, PRIMEIRA_LINHA_DE_DADOS + i, c, linha.get(c));
            }
        }
        ws.freezePane(0, PRIMEIRA_LINHA_DE_DADOS);
    }

    private static void valor(Worksheet ws, int linha, int coluna, Object valor) {
        if (valor == null || "".equals(valor)) {
            return;
        }
        if (valor instanceof LocalDate data) {
            ws.value(linha, coluna, data);
            ws.style(linha, coluna).format("dd/mm/yyyy").set();
        } else if (valor instanceof BigDecimal numero) {
            ws.value(linha, coluna, numero);
            ws.style(linha, coluna).format("#,##0.00").set();
        } else if (valor instanceof Number numero) {
            ws.value(linha, coluna, numero);
        } else {
            ws.value(linha, coluna, valor.toString());
        }
    }
}
