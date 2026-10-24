package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.EstadoCivil;
import br.org.fmc.model.Perfil;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.SistemaRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Carga dos dados de teste (BOARD-52): imóveis, inquilinos e usuários fictícios
 * para testar e apresentar o sistema.
 *
 * Os dados ficam em resources/dados-teste/*.csv (exportados da planilha
 * "Dados de teste FMC"). Tudo é cadastrado pelos services, então passa pelas
 * mesmas validações da tela: se um dado estiver errado, a carga para e diz qual.
 *
 * Os arquivos contratos.csv, fiadores.csv e caucoes.csv já estão prontos, mas
 * só serão carregados quando o cadastro de contratos (BOARD-36/57) existir.
 */
public class ServicoDadosTeste {

    /** Senha dos usuários de teste (dados fictícios, ver README). */
    public static final String SENHA_TESTE = "teste123";

    /** O que foi carregado, para mostrar ao usuário. */
    public record Resumo(int usuarios, int imoveis, int inquilinos, int contratosAguardando) {
        @Override
        public String toString() {
            return imoveis + " imóveis, " + inquilinos + " inquilinos e " + usuarios + " usuários de teste. "
                    + contratosAguardando + " contratos ficam para quando o cadastro de contratos existir.";
        }
    }

    private final SistemaRepository sistema = new SistemaRepository();

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
            int contratos = ler("contratos.csv").size();
            return new Resumo(usuarios, imoveis, inquilinos, contratos);
        } finally {
            acesso.sair();
        }
    }

    /** Um usuário de cada perfil, mais a "jovem aprendiz" (Secretaria sem cadastrar inquilino novo). */
    private int carregarUsuarios(ServicoAcesso acesso) throws SQLException {
        acesso.criarUsuario("Bruna Secretária Teste", "secretaria@exemplo.org", Perfil.SECRETARIA, SENHA_TESTE);
        acesso.criarUsuario("Carlos Financeiro Teste", "financeiro@exemplo.org", Perfil.FINANCEIRO, SENHA_TESTE);
        Usuario aprendiz = acesso.criarUsuario("Júlia Aprendiz Teste", "aprendiz@exemplo.org", Perfil.SECRETARIA, SENHA_TESTE);
        Set<Acao> semCriarInquilino = EnumSet.copyOf(Perfil.SECRETARIA.getPermissoes());
        semCriarInquilino.remove(Acao.CRIAR_INQUILINO);
        acesso.alterarPermissoes(aprendiz.getId(), semCriarInquilino);
        return 3;
    }

    private int carregarImoveis() throws SQLException {
        ServicoImoveis imoveis = new ServicoImoveis();
        List<String[]> linhas = ler("imoveis.csv");
        for (String[] c : linhas) {
            try {
                imoveis.salvarFormulario(null, c[0], c[1], tipo(c[2]), c[3], c[4], "", c[5], c[6], c[7], situacao(c[8]));
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

    private static EstadoCivil estadoCivil(String texto) {
        return Arrays.stream(EstadoCivil.values()).filter(e -> e.getDescricao().equals(texto)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Estado civil desconhecido: " + texto));
    }
}
