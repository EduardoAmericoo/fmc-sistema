package br.org.fmc.util;

/**
 * Os dois jeitos de o sistema rodar (decisão do PO na auditoria de segurança de 21/11):
 *
 * - Demonstração (o padrão): dados de teste, senha do admin conhecida no README e e-mail simulado.
 *   Serve para os avaliadores, os professores e a equipe.
 * - Cliente: o computador da Fundação. Sem dados de teste, sem e-mail simulado, e o Administrador
 *   inicial precisa trocar a senha no 1º acesso.
 *
 * O modo cliente liga com o instalador do cliente (scripts/gerar-exe.ps1 -Modo cliente, que passa
 * -Dfmc.modo=cliente) ou com a variável de ambiente FMC_DADOS_TESTE=nao.
 */
public final class ModoSistema {

    private ModoSistema() {
    }

    public static boolean cliente() {
        return "cliente".equalsIgnoreCase(System.getProperty("fmc.modo"))
                || "nao".equalsIgnoreCase(System.getenv("FMC_DADOS_TESTE"));
    }

    /** "recarregar" apaga tudo e carrega os dados de teste do zero. Nunca no modo cliente. */
    public static boolean recarregarDadosDeTeste() {
        return !cliente() && "recarregar".equalsIgnoreCase(System.getenv("FMC_DADOS_TESTE"));
    }
}
