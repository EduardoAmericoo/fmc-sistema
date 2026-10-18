package br.org.fmc.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * O que todo repositório sabe fazer. Cada entidade tem o seu:
 * ImovelRepository implements Repositorio&lt;Imovel&gt;, e assim por diante.
 *
 * salvar() insere quando o objeto ainda não tem id e atualiza quando já tem;
 * depois de inserir, coloca no objeto o id gerado pelo banco.
 */
public interface Repositorio<T> {

    void salvar(T objeto) throws SQLException;

    Optional<T> buscarPorId(long id) throws SQLException;

    List<T> listarTodos() throws SQLException;
}
