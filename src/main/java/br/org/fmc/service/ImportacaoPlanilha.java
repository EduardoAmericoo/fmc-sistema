package br.org.fmc.service;

import br.org.fmc.db.Database;
import br.org.fmc.model.Acao;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.service.ModeloPlanilha.Aba;
import br.org.fmc.service.ModeloPlanilha.Coluna;
import br.org.fmc.util.TextoBusca;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

/**
 * Importar planilha (revisão da SR2): traz de uma vez os imóveis, inquilinos e contratos que a
 * Fundação tem hoje no papel ou no Excel, no modelo do ModeloPlanilha.
 *
 * Como funciona, para nada ficar pela metade:
 * 1. conferir: o sistema faz um ensaio numa CÓPIA do banco, cadastrando cada linha pelos mesmos
 *    services das telas (mesmas regras: CPF válido, renda 3×, garantia...). Cada linha recusada
 *    vira um problema com a aba e a linha. O banco de verdade não é tocado;
 * 2. importar: só se o ensaio não teve nenhum problema. Antes, o sistema guarda uma cópia do
 *    banco; se algo der errado no meio, volta tudo como estava (tudo ou nada).
 *
 * Contratos importados não geram os meses que já passaram: os aluguéis antigos contam como pagos
 * no papel (ServicoContratos.cadastrarEGerar com gerarMesesQueJaVenceram = false).
 */
public class ImportacaoPlanilha {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("d/M/yyyy");

    /** Um problema para a pessoa corrigir na planilha. Linha = a linha do Excel (a mesma que ela vê). */
    public record Problema(String aba, int linha, String mensagem) {
        public String local() {
            return linha > 0 ? aba + ", linha " + linha : aba;
        }
    }

    /** O resultado do ensaio ou da importação. */
    public record Resultado(int imoveis, int inquilinos, int contratos, List<Problema> problemas) {
        public boolean semProblemas() {
            return problemas.isEmpty();
        }

        public int total() {
            return imoveis + inquilinos + contratos;
        }
    }

    /** Uma linha da planilha já lida: os textos por título de coluna. */
    record Linha(Aba aba, int numero, Map<String, String> valores) {
        String get(String titulo) {
            String v = valores.get(TextoBusca.normalizar(titulo));
            return v == null ? "" : v.strip();
        }
    }

    /** Confere a planilha sem gravar nada (ensaio numa cópia do banco). */
    public Resultado conferir(Path planilha) throws IOException, SQLException {
        Sessao.exigir(Acao.IMPORTAR_DADOS);
        Map<Aba, List<Linha>> linhas = new EnumMap<>(Aba.class);
        List<Problema> problemas = new ArrayList<>();
        ler(planilha, linhas, problemas);
        if (!problemas.isEmpty()) {
            return contar(linhas, problemas);
        }
        Path original = Database.arquivo();
        Path copia = Files.createTempFile("fmc-ensaio-importacao", ".db");
        try {
            Files.copy(original, copia, StandardCopyOption.REPLACE_EXISTING);
            Database.usarArquivo(copia);
            return aplicar(linhas);
        } finally {
            Database.usarArquivo(original);
            Files.deleteIfExists(copia);
        }
    }

    /**
     * Importa de verdade. Confere antes e só grava se não houver nenhum problema; se algo der
     * errado no meio, devolve o banco como estava.
     */
    public Resultado importar(Path planilha) throws IOException, SQLException {
        Resultado ensaio = conferir(planilha);
        if (!ensaio.semProblemas()) {
            return ensaio;
        }
        Map<Aba, List<Linha>> linhas = new EnumMap<>(Aba.class);
        ler(planilha, linhas, new ArrayList<>());
        Path original = Database.arquivo();
        Path seguranca = Files.createTempFile("fmc-antes-da-importacao", ".db");
        Files.copy(original, seguranca, StandardCopyOption.REPLACE_EXISTING);
        try {
            Resultado feito = aplicar(linhas);
            if (!feito.semProblemas()) {
                Files.copy(seguranca, original, StandardCopyOption.REPLACE_EXISTING);
            }
            return feito;
        } catch (SQLException | RuntimeException e) {
            Files.copy(seguranca, original, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        } finally {
            Files.deleteIfExists(seguranca);
        }
    }

    // ---------- leitura ----------

    /** Lê as três abas; cada linha vira um mapa "título da coluna → texto". */
    static void ler(Path planilha, Map<Aba, List<Linha>> linhas, List<Problema> problemas) throws IOException {
        try (InputStream entrada = Files.newInputStream(planilha); ReadableWorkbook wb = new ReadableWorkbook(entrada)) {
            Map<String, Sheet> abas = new HashMap<>();
            wb.getSheets().forEach(s -> abas.put(TextoBusca.normalizar(s.getName()), s));
            for (Aba aba : Aba.values()) {
                Sheet sheet = abas.get(TextoBusca.normalizar(aba.getNome()));
                if (sheet == null) {
                    problemas.add(new Problema(aba.getNome(), 0, "A aba \"" + aba.getNome()
                            + "\" não foi encontrada. Use o modelo baixado no sistema."));
                    linhas.put(aba, List.of());
                    continue;
                }
                linhas.put(aba, lerAba(aba, sheet.read(), problemas));
            }
        } catch (IOException | RuntimeException e) {
            throw new IOException("Não foi possível ler a planilha. Use um arquivo do Excel (.xlsx) no modelo "
                    + "baixado no sistema (feche o arquivo no Excel antes de importar).", e);
        }
    }

    private static List<Linha> lerAba(Aba aba, List<Row> rows, List<Problema> problemas) {
        if (rows.isEmpty()) {
            return List.of();
        }
        // Os títulos da 1ª linha dizem onde está cada coluna ("CPF *" e "cpf" valem igual).
        Map<Integer, Coluna> porIndice = new HashMap<>();
        Row titulos = rows.get(0);
        for (Coluna coluna : aba.getColunas()) {
            int achou = -1;
            for (int i = 0; i < titulos.getCellCount(); i++) {
                String t = titulos.getCellText(i).replace("*", "");
                if (TextoBusca.normalizar(t).equals(TextoBusca.normalizar(coluna.titulo()))) {
                    achou = i;
                }
            }
            if (achou >= 0) {
                porIndice.put(achou, coluna);
            } else if (coluna.obrigatoria()) {
                problemas.add(new Problema(aba.getNome(), 1, "Falta a coluna \"" + coluna.titulo() + "\"."));
            }
        }
        List<Linha> linhas = new ArrayList<>();
        for (Row row : rows.subList(1, rows.size())) {
            Map<String, String> valores = new HashMap<>();
            boolean vazia = true;
            for (Map.Entry<Integer, Coluna> e : porIndice.entrySet()) {
                String texto = texto(row, e.getKey(), e.getValue());
                if (!texto.isBlank()) {
                    vazia = false;
                }
                valores.put(TextoBusca.normalizar(e.getValue().titulo()), texto);
            }
            String primeira = row.getCellCount() > 0 ? row.getCellText(0) : "";
            boolean exemploOuDica = primeira.contains(ModeloPlanilha.MARCA_EXEMPLO)
                    || row.getRowNum() == ModeloPlanilha.LINHA_DICAS + 1;
            if (!vazia && !exemploOuDica) {
                linhas.add(new Linha(aba, row.getRowNum(), valores));
            }
        }
        return linhas;
    }

    /** O texto da célula; número e data viram o texto que os services entendem. */
    private static String texto(Row row, int indice, Coluna coluna) {
        Optional<Cell> celula = row.getOptionalCell(indice);
        if (celula.isEmpty() || celula.get().getType() == CellType.EMPTY) {
            return "";
        }
        Cell c = celula.get();
        if (c.getType() == CellType.NUMBER) {
            String titulo = coluna.titulo().toLowerCase();
            if (titulo.contains("entrega") || titulo.contains("recebida em")) {
                return c.asDate().toLocalDate().toString(); // aaaa-mm-dd
            }
            BigDecimal numero = c.asNumber();
            if (titulo.contains("cpf")) {
                return String.format("%011d", numero.longValue()); // CPF digitado como número perde os zeros
            }
            if (titulo.contains("celular")) {
                return numero.toBigInteger().toString();
            }
            return numero.stripTrailingZeros().toPlainString();
        }
        return c.getText() == null ? "" : c.getText();
    }

    // ---------- gravação (no ensaio, numa cópia; na importação, no banco de verdade) ----------

    private Resultado aplicar(Map<Aba, List<Linha>> linhas) throws SQLException {
        List<Problema> problemas = new ArrayList<>();
        int imoveis = 0;
        int inquilinos = 0;
        int contratos = 0;
        ServicoImoveis servicoImoveis = new ServicoImoveis();
        for (Linha l : linhas.getOrDefault(Aba.IMOVEIS, List.of())) {
            if (tentar(l, problemas, () -> {
                SituacaoImovel situacao = escolher(l, "Situação", SituacaoImovel.values(), SituacaoImovel::getDescricao,
                        SituacaoImovel.VAGO);
                servicoImoveis.salvarFormulario(null, l.get("Código"), l.get("Unidade"),
                        escolher(l, "Tipo", TipoImovel.values(), TipoImovel::getDescricao, null),
                        l.get("Endereço"), l.get("Bairro"), "", l.get("Aluguel (R$)"), zero(l.get("Taxa de luz (R$)")),
                        zero(l.get("IPTU (R$)")), situacao == SituacaoImovel.OCUPADO ? SituacaoImovel.VAGO : situacao);
            })) {
                imoveis++;
            }
        }
        ServicoInquilinos servicoInquilinos = new ServicoInquilinos();
        for (Linha l : linhas.getOrDefault(Aba.INQUILINOS, List.of())) {
            if (tentar(l, problemas, () -> servicoInquilinos.cadastrar(new DadosInquilino(l.get("Nome completo"),
                    l.get("CPF"), l.get("Celular"), l.get("E-mail"),
                    escolher(l, "Estado civil", EstadoCivil.values(), EstadoCivil::getDescricao, null),
                    l.get("Profissão"), l.get("Local de trabalho"), l.get("Renda mensal (R$)"),
                    zero(l.get("Outras rendas (R$)")), l.get("Cônjuge: nome"), l.get("Cônjuge: CPF"),
                    zero(l.get("Cônjuge: renda (R$)")))))) {
                inquilinos++;
            }
        }
        Map<String, Long> imovelPorCodigo = new HashMap<>();
        for (Imovel i : new ImovelRepository().listarTodos()) {
            imovelPorCodigo.put(TextoBusca.normalizar(i.getCodigo()), i.getId());
        }
        InquilinoRepository repoInquilinos = new InquilinoRepository();
        ServicoContratos servicoContratos = new ServicoContratos();
        for (Linha l : linhas.getOrDefault(Aba.CONTRATOS, List.of())) {
            if (tentar(l, problemas, () -> {
                Long imovel = imovelPorCodigo.get(TextoBusca.normalizar(l.get("Código do imóvel")));
                if (imovel == null) {
                    throw new RegraNegocioException("O imóvel \"" + l.get("Código do imóvel")
                            + "\" não está na aba Imóveis nem no sistema.");
                }
                Long inquilino = buscarInquilino(repoInquilinos, l.get("CPF do inquilino"));
                boolean caucao = escolher(l, "Garantia", new String[] {"Fiador", "Caução"}, s -> s, null).equals("Caução");
                DadosContrato.Fiador fiador = caucao ? null : new DadosContrato.Fiador(l.get("Fiador: nome"),
                        l.get("Fiador: CPF"), l.get("Fiador: celular"),
                        escolher(l, "Fiador: estado civil", EstadoCivil.values(), EstadoCivil::getDescricao,
                                EstadoCivil.SOLTEIRO),
                        l.get("Fiador: renda (R$)"), l.get("Fiador: endereço"), l.get("Fiador: cônjuge"),
                        l.get("Fiador: CPF do cônjuge"));
                DadosContrato.Caucao dadosCaucao = !caucao ? null : new DadosContrato.Caucao(
                        l.get("Caução: valor (R$)"), data(l, "Caução: recebida em"),
                        escolher(l, "Caução: forma de pagamento", FormaPagamento.values(),
                                FormaPagamento::getDescricao, null),
                        l.get("Caução: nº do recibo"));
                PrazoContrato prazo = escolher(l, "Prazo (meses)", PrazoContrato.values(),
                        p -> String.valueOf(p.getMeses()), null);
                servicoContratos.cadastrarEGerar(new DadosContrato(imovel, inquilino, prazo,
                        data(l, "Entrega das chaves"), fiador, dadosCaucao), false);
            })) {
                contratos++;
            }
        }
        return new Resultado(imoveis, inquilinos, contratos, problemas);
    }

    private interface Passo {
        void executar() throws SQLException;
    }

    /** Cadastra uma linha; se for recusada, guarda o motivo com a aba e a linha e segue para a próxima. */
    private static boolean tentar(Linha l, List<Problema> problemas, Passo passo) throws SQLException {
        try {
            passo.executar();
            return true;
        } catch (RegraNegocioException e) {
            problemas.add(new Problema(l.aba().getNome(), l.numero(), String.join(" ", e.getErros())));
        } catch (IllegalArgumentException | IllegalStateException e) {
            problemas.add(new Problema(l.aba().getNome(), l.numero(), e.getMessage()));
        }
        return false;
    }

    private static Long buscarInquilino(InquilinoRepository repo, String cpf) throws SQLException {
        Cpf numero;
        try {
            numero = new Cpf(cpf);
        } catch (RuntimeException e) {
            throw new RegraNegocioException("O CPF do inquilino \"" + cpf + "\" é inválido.");
        }
        return repo.buscarPorCpf(numero).map(Inquilino::getId).orElseThrow(() -> new RegraNegocioException(
                "O inquilino de CPF " + cpf + " não está na aba Inquilinos nem no sistema."));
    }

    /** Escolhe a opção pelo texto (ignora maiúsculas e acentos); em branco, o padrão (ou erro se não houver). */
    private static <T> T escolher(Linha l, String titulo, T[] opcoes, Function<T, String> texto, T padrao) {
        String valor = l.get(titulo);
        if (valor.isBlank()) {
            if (padrao != null) {
                return padrao;
            }
            throw new RegraNegocioException("Preencha a coluna \"" + titulo + "\".");
        }
        String procurado = TextoBusca.normalizar(valor);
        return Arrays.stream(opcoes).filter(o -> TextoBusca.normalizar(texto.apply(o)).equals(procurado)).findFirst()
                .orElseThrow(() -> new RegraNegocioException("\"" + valor + "\" não é uma opção de " + titulo
                        + ". Use: " + String.join(", ", Arrays.stream(opcoes).map(texto).toList()) + "."));
    }

    /** Data em dd/mm/aaaa (ou aaaa-mm-dd, como o Excel guarda). */
    private static LocalDate data(Linha l, String titulo) {
        String valor = l.get(titulo);
        if (valor.isBlank()) {
            return null;
        }
        try {
            return valor.contains("-") ? LocalDate.parse(valor) : LocalDate.parse(valor, DATA_BR);
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("A data \"" + valor + "\" em " + titulo + " é inválida. Use dd/mm/aaaa.");
        }
    }

    private static String zero(String valor) {
        return valor.isBlank() ? "0" : valor;
    }

    private static Resultado contar(Map<Aba, List<Linha>> linhas, List<Problema> problemas) {
        return new Resultado(linhas.getOrDefault(Aba.IMOVEIS, List.of()).size(),
                linhas.getOrDefault(Aba.INQUILINOS, List.of()).size(),
                linhas.getOrDefault(Aba.CONTRATOS, List.of()).size(), problemas);
    }
}
