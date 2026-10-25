package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.Cpf;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.PrazoContrato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.InquilinoRepository;
import br.org.fmc.repository.SistemaRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Carga dos dados de teste (BOARD-52): imóveis, inquilinos, contratos e usuários fictícios
 * para testar e apresentar o sistema.
 *
 * Os dados ficam em resources/dados-teste/*.csv (exportados da planilha
 * "Dados de teste FMC"). Tudo é cadastrado pelos services, então passa pelas
 * mesmas validações da tela: se um dado estiver errado, a carga para e diz qual.
 *
 * Os contratos (contratos.csv, com fiadores.csv e caucoes.csv) também passam pelo
 * ServicoContratos: os imóveis "Ocupado" entram vagos e ficam ocupados ao receber o contrato.
 */
public class ServicoDadosTeste {

    /** Senha dos usuários de teste (dados fictícios, ver README). */
    public static final String SENHA_TESTE = "teste123";

    /** O que foi carregado, para mostrar ao usuário. */
    public record Resumo(int usuarios, int imoveis, int inquilinos, int contratos) {
        @Override
        public String toString() {
            return imoveis + " imóveis, " + inquilinos + " inquilinos, " + contratos + " contratos e "
                    + usuarios + " usuários de teste.";
        }
    }

    private final SistemaRepository sistema = new SistemaRepository();

    /**
     * Se o banco estiver vazio (primeira vez que o sistema abre), carrega os dados de teste.
     * Se já houver qualquer cadastro, não mexe em nada.
     *
     * @return o que foi carregado, ou vazio se o banco já tinha dados
     */
    public Optional<Resumo> carregarSeBancoVazio() throws SQLException {
        if (!sistema.bancoSemDados()) {
            return Optional.empty();
        }
        return Optional.of(recarregarDoZero());
    }

    /**
     * Apaga todos os dados do banco e carrega os dados de teste do zero.
     * Irreversível: usar só em banco de teste ou antes de uma apresentação.
     */
    public Resumo recarregarDoZero() throws SQLException {
        sistema.apagarTodosOsDados();
        ServicoAcesso acesso = new ServicoAcesso();
        acesso.garantirAdministradorInicial();
        // A carga usa os services, que exigem alguém logado com permissão: entra como Administrador.
        acesso.login(ServicoAcesso.EMAIL_ADMIN_INICIAL, ServicoAcesso.SENHA_ADMIN_INICIAL);
        try {
            int usuarios = carregarUsuarios(acesso);
            int imoveis = carregarImoveis();
            int inquilinos = carregarInquilinos();
            int contratos = carregarContratos();
            return new Resumo(usuarios, imoveis, inquilinos, contratos);
        } finally {
            acesso.sair();
        }
    }

    /** Um usuário de cada perfil, mais a "jovem aprendiz" (Secretaria sem cadastrar inquilino novo). */
    private int carregarUsuarios(ServicoAcesso acesso) throws SQLException {
        acesso.criarUsuario("Ana Administradora Teste", "administrador@exemplo.org", Perfil.ADMINISTRADOR, SENHA_TESTE);
        acesso.criarUsuario("Bruna Secretária Teste", "secretaria@exemplo.org", Perfil.SECRETARIA, SENHA_TESTE);
        acesso.criarUsuario("Carlos Financeiro Teste", "financeiro@exemplo.org", Perfil.FINANCEIRO, SENHA_TESTE);
        Usuario aprendiz = acesso.criarUsuario("Júlia Aprendiz Teste", "aprendiz@exemplo.org", Perfil.SECRETARIA, SENHA_TESTE);
        Set<Acao> semCriarInquilino = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        semCriarInquilino.remove(Acao.CRIAR_INQUILINO);
        acesso.alterarPermissoes(aprendiz.getId(), semCriarInquilino);
        return 4;
    }

    /** Os acessos para entrar no sistema depois da carga, para mostrar na tela. */
    public static String acessos() {
        return "Administrador: " + ServicoAcesso.EMAIL_ADMIN_INICIAL + " (senha " + ServicoAcesso.SENHA_ADMIN_INICIAL + ")"
                + "\nUsuários de teste (senha " + SENHA_TESTE + "):"
                + "\n  administrador@exemplo.org (Administrador)"
                + "\n  secretaria@exemplo.org (Secretaria)"
                + "\n  financeiro@exemplo.org (Financeiro)"
                + "\n  aprendiz@exemplo.org (Secretaria que não cadastra inquilino novo)";
    }

    private int carregarImoveis() throws SQLException {
        ServicoImoveis imoveis = new ServicoImoveis();
        List<String[]> linhas = ler("imoveis.csv");
        for (String[] c : linhas) {
            // "Ocupado" entra como vago: quem ocupa o imóvel é o contrato, carregado depois.
            SituacaoImovel situacao = situacao(c[8]) == SituacaoImovel.OCUPADO ? SituacaoImovel.VAGO : situacao(c[8]);
            try {
                imoveis.salvarFormulario(null, c[0], c[1], tipo(c[2]), c[3], c[4], "", c[5], c[6], c[7], situacao);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Imóvel " + c[0] + " do imoveis.csv foi recusado: " + e.getMessage(), e);
            }
        }
        return linhas.size();
    }

    private int carregarInquilinos() throws SQLException {
        ServicoInquilinos inquilinos = new ServicoInquilinos();
        List<String[]> linhas = ler("inquilinos.csv");
        for (String[] c : linhas) {
            try {
                inquilinos.cadastrar(new DadosInquilino(c[0], c[1], c[2], c[3], estadoCivil(c[4]), c[5], c[6],
                        c[7], c[8], c[9], c[10], c[11]));
            } catch (RegraNegocioException e) {
                throw new IllegalStateException("Inquilino " + c[0] + " do inquilinos.csv foi recusado: "
                        + String.join(" ", e.getErros()), e);
            }
        }
        return linhas.size();
    }

    /**
     * Contratos na ordem do contratos.csv, cada um com a sua garantia (fiador ou caução).
     * O sistema numera sozinho (CT-1001, CT-1002...), e a carga confere se bateu com a planilha.
     */
    private int carregarContratos() throws SQLException {
        Map<String, String[]> fiadores = porContrato(ler("fiadores.csv"));
        Map<String, String[]> caucoes = porContrato(ler("caucoes.csv"));
        Map<String, Long> imoveis = new HashMap<>();
        new ImovelRepository().listarTodos().forEach(i -> imoveis.put(i.getCodigo(), i.getId()));
        InquilinoRepository inquilinos = new InquilinoRepository();
        ServicoContratos contratos = new ServicoContratos();

        List<String[]> linhas = ler("contratos.csv");
        for (String[] c : linhas) {
            String[] f = fiadores.get(c[0]);
            String[] k = caucoes.get(c[0]);
            DadosContrato.Fiador fiador = f == null ? null
                    : new DadosContrato.Fiador(f[1], f[2], f[3], estadoCivil(f[4]), f[5], f[8], f[6], f[7]);
            DadosContrato.Caucao caucao = k == null ? null
                    : new DadosContrato.Caucao(k[1], LocalDate.parse(k[2]), formaPagamento(k[3]), k[4]);
            Long inquilino = inquilinos.buscarPorCpf(new Cpf(c[2])).map(Inquilino::getId).orElse(null);
            DadosContrato dados = new DadosContrato(imoveis.get(c[1]), inquilino, prazo(c[4]),
                    LocalDate.parse(c[3]), fiador, caucao);
            Contrato salvo;
            try {
                salvo = contratos.cadastrar(dados);
            } catch (RegraNegocioException e) {
                throw new IllegalStateException("Contrato " + c[0] + " do contratos.csv foi recusado: "
                        + String.join(" ", e.getErros()), e);
            }
            if (!salvo.getNumero().equals(c[0])) {
                throw new IllegalStateException("O contrato " + c[0] + " do contratos.csv ficou com o número "
                        + salvo.getNumero() + ". Confira a ordem do arquivo.");
            }
        }
        return linhas.size();
    }

    private static Map<String, String[]> porContrato(List<String[]> linhas) {
        Map<String, String[]> mapa = new HashMap<>();
        linhas.forEach(l -> mapa.put(l[0], l));
        return mapa;
    }

    /** Lê um CSV de resources/dados-teste (separado por ";", primeira linha = cabeçalho). */
    static List<String[]> ler(String arquivo) {
        try (InputStream in = ServicoDadosTeste.class.getResourceAsStream("/dados-teste/" + arquivo)) {
            if (in == null) {
                throw new IllegalStateException("Arquivo dados-teste/" + arquivo + " não encontrado.");
            }
            BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String[]> linhas = new ArrayList<>();
            leitor.readLine(); // cabeçalho
            for (String linha = leitor.readLine(); linha != null; linha = leitor.readLine()) {
                if (!linha.isBlank()) {
                    linhas.add(linha.split(";", -1));
                }
            }
            return linhas;
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao ler dados-teste/" + arquivo, e);
        }
    }

    private static TipoImovel tipo(String texto) {
        return Arrays.stream(TipoImovel.values()).filter(t -> t.getDescricao().equals(texto)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Tipo de imóvel desconhecido: " + texto));
    }

    private static SituacaoImovel situacao(String texto) {
        return Arrays.stream(SituacaoImovel.values()).filter(s -> s.getDescricao().equals(texto)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Situação desconhecida: " + texto));
    }

    private static PrazoContrato prazo(String meses) {
        return Arrays.stream(PrazoContrato.values()).filter(p -> String.valueOf(p.getMeses()).equals(meses)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Prazo desconhecido: " + meses));
    }

    private static FormaPagamento formaPagamento(String texto) {
        return Arrays.stream(FormaPagamento.values()).filter(f -> f.getDescricao().equals(texto)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Forma de pagamento desconhecida: " + texto));
    }

    private static EstadoCivil estadoCivil(String texto) {
        return Arrays.stream(EstadoCivil.values()).filter(e -> e.getDescricao().equals(texto)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Estado civil desconhecido: " + texto));
    }
}
