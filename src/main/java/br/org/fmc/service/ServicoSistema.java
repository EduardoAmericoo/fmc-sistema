package br.org.fmc.service;

import br.org.fmc.repository.SistemaRepository;
import java.sql.SQLException;

/**
 * Informações gerais do sistema para a tela principal.
 *
 * Exemplo do caminho certo entre as camadas: a tela chama este service,
 * que usa o repository, que fala com o banco.
 */
public class ServicoSistema {

    private final SistemaRepository repositorio;

    public ServicoSistema() {
        this(new SistemaRepository());
    }

    public ServicoSistema(SistemaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Versão do schema do banco, ou "?" se ainda não foi gravada. */
    public String versaoDoBanco() throws SQLException {
        return repositorio.buscarValor("versao_schema").orElse("?");
    }
}
